package com.ventus.sys.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.ventus.sys.data.local.AppPreferences
import com.ventus.sys.data.repository.AutoAddRepository
import com.ventus.sys.data.repository.AutoAddResult
import com.ventus.sys.data.repository.NowPlaying
import com.ventus.sys.data.repository.SpotifyRepository
import com.ventus.sys.data.repository.SyncResult
import com.ventus.sys.data.repository.TasteProfileRepository
import com.ventus.sys.domain.FeatureResolver
import com.ventus.sys.domain.ScoreEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service — Android's mechanism for reliable background work in
 * exchange for a persistent notification. WorkManager's 15-min floor
 * categorically can't do a 3s live poll. Ports app.py's
 * `_now_playing_poller` (app.py:1059-1107) exactly: 3s interval, silently
 * vault-scores every new track in the background. No SSE broadcast needed —
 * [NowPlayingStateHolder] is a StateFlow any screen subscribes to
 * in-process, since there's no separate backend process here.
 *
 * Notification building lives in [NowPlayingNotifications] (kept separate
 * so this class stays focused on the poll loop itself). The explicit (not
 * silent) battery-optimization-exemption prompt and OEM background-kill
 * onboarding (Xiaomi/MIUI etc.) are UI concerns, not this class — this
 * class only needs to survive once the user has actually granted that
 * exemption.
 */
@AndroidEntryPoint
class NowPlayingService : Service() {
    @Inject
    lateinit var spotifyRepository: SpotifyRepository

    @Inject
    lateinit var featureResolver: FeatureResolver

    @Inject
    lateinit var tasteProfileRepository: TasteProfileRepository

    @Inject
    lateinit var stateHolder: NowPlayingStateHolder

    @Inject
    lateinit var sessionHistoryStateHolder: SessionHistoryStateHolder

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var autoSyncStateHolder: AutoSyncStateHolder

    @Inject
    lateinit var autoAddRepository: AutoAddRepository

    @Inject
    lateinit var autoAddStateHolder: AutoAddStateHolder

    private val notifications by lazy { NowPlayingNotifications(this) }
    private var pollJob: Job? = null
    private var autoSyncJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        notifications.createChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        notifications.startForeground(NowPlayingNotifications.NOTIFICATION_TEXT_IDLE)
        startPolling()
        startAutoSyncLoop()
        return START_STICKY // system should restart this if it's killed
    }

    override fun onDestroy() {
        pollJob?.cancel()
        autoSyncJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob =
            serviceScope.launch {
                var lastTrackId: String? = null
                while (true) {
                    // Unconditional assignment, not `?.let` - pollOnce's null
                    // return means "nothing is playing right now", which is
                    // itself a real value lastTrackId needs to remember. A
                    // `?.let` here would skip that case, so lastTrackId would
                    // stay stale at whatever track was playing before Spotify
                    // went idle - meaning the *same* track resuming afterward
                    // would see trackChanged=false and never re-trigger
                    // scoring, leaving Live Scorer stuck on IdleState.
                    lastTrackId = pollOnce(lastTrackId)
                    delay(POLL_INTERVAL_MS)
                }
            }
    }

    /**
     * Returns the trackId to remember for next poll's comparison — null when
     * nothing's playing (mirrors app.py:1073's track_changed check).
     */
    private suspend fun pollOnce(lastTrackId: String?): String? {
        val nowPlaying = runCatching { spotifyRepository.getCurrentlyPlaying() }.getOrNull()

        if (nowPlaying == null) {
            if (lastTrackId != null) {
                stateHolder.update { NowPlayingState(isPlaying = false) }
                notifications.update(NowPlayingNotifications.NOTIFICATION_TEXT_IDLE)
            }
            return null
        }

        val trackChanged = nowPlaying.trackId != lastTrackId
        if (trackChanged) {
            stateHolder.update {
                NowPlayingState(
                    isPlaying = nowPlaying.isPlaying,
                    trackId = nowPlaying.trackId,
                    trackName = nowPlaying.name,
                    artist = nowPlaying.artist,
                    albumArtUrl = nowPlaying.albumArtUrl,
                    progressMs = nowPlaying.progressMs,
                    durationMs = nowPlaying.durationMs,
                    isScoring = true,
                )
            }
            notifications.update("${nowPlaying.name} — ${nowPlaying.artist}")
            scoreTrack(nowPlaying)
        } else {
            // Also reconciles isPlaying — corrects the transport bar's optimistic
            // play/pause flip if it guessed wrong, and reflects pausing/resuming
            // from the real Spotify app too, not just VENTUS's own controls.
            stateHolder.update { it.copy(progressMs = nowPlaying.progressMs, isPlaying = nowPlaying.isPlaying) }
        }

        return nowPlaying.trackId
    }

    /** Silent background scoring — mirrors app.py's `_auto_vault_track` (app.py:1039-1052). */
    private fun scoreTrack(nowPlaying: NowPlaying) {
        val trackId = nowPlaying.trackId
        serviceScope.launch {
            val resolved = featureResolver.resolve(trackId)
            val features = resolved.features
            if (features == null) {
                stateHolder.update { if (it.trackId == trackId) it.copy(isScoring = false) else it }
                sessionHistoryStateHolder.push(
                    SessionHistoryEntry(
                        id = trackId,
                        name = nowPlaying.name,
                        artist = nowPlaying.artist,
                        score = null,
                        verdict = null,
                        method = resolved.method.name.lowercase(),
                        timestampMs = System.currentTimeMillis(),
                    ),
                )
                return@launch
            }
            val profile = tasteProfileRepository.observeProfile().first()
            val result = ScoreEngine.score(features, profile)
            stateHolder.update {
                if (it.trackId == trackId) {
                    it.copy(features = features, profile = profile, scoreResult = result, isScoring = false)
                } else {
                    it
                }
            }
            sessionHistoryStateHolder.push(
                SessionHistoryEntry(
                    id = trackId,
                    name = nowPlaying.name,
                    artist = nowPlaying.artist,
                    score = result.score,
                    verdict = result.verdict,
                    method = resolved.method.name.lowercase(),
                    timestampMs = System.currentTimeMillis(),
                ),
            )
            maybeAutoAdd(nowPlaying, result.score)
        }
    }

    /**
     * Ports app.js's maybeAutoAdd (app.js:1646-1671) — fires right after a
     * track scores, same call site as desktop's triggerScore/
     * silentVaultScore. [AppPreferences] read fresh on every call (not
     * cached) so a mid-session settings change (toggle off, threshold
     * raised, target playlist changed) takes effect on the very next scored
     * track rather than waiting for a service restart.
     */
    private fun maybeAutoAdd(
        nowPlaying: NowPlaying,
        score: Int,
    ) {
        if (!appPreferences.autoAddEnabled) return
        val playlistId = appPreferences.autoAddPlaylistId
        if (playlistId.isNullOrBlank()) return
        if (score < appPreferences.autoAddThreshold) return
        serviceScope.launch {
            val result = runCatching { autoAddRepository.addToPlaylist(playlistId, nowPlaying.trackId, nowPlaying.name) }.getOrNull()
            val now = System.currentTimeMillis()
            when (result) {
                is AutoAddResult.Added -> {
                    autoAddStateHolder.update(now, "Added \"${result.trackName}\" ($score%)", isError = false)
                }

                is AutoAddResult.AlreadyInPlaylist -> {
                    autoAddStateHolder.update(now, "\"${result.trackName}\" already in playlist", isError = false)
                }

                is AutoAddResult.Failed -> {
                    autoAddStateHolder.update(now, "Failed: ${result.message}", isError = true)
                }

                null -> {
                    autoAddStateHolder.update(now, "Failed: unexpected error", isError = true)
                }
            }
        }
    }

    /**
     * Ports app.py's `_playlist_autosync_worker` (app.py:1282-1300): a
     * continuously-running loop that periodically re-syncs the taste-profile
     * playlist, not a WorkManager job - the configurable interval (60s+,
     * app.py:2284) is well under WorkManager's 15-minute PeriodicWorkRequest
     * floor, the same reason the 3s live poll above needed a foreground
     * service instead. Reuses this already-running service (not a second
     * one) since it's already alive for exactly as long as desktop's daemon
     * thread is - the lifetime of active use, not a scheduled OS trigger.
     * Checks every [AUTO_SYNC_CHECK_INTERVAL_MS] whether the configured
     * interval has actually elapsed, rather than sleeping for the full
     * configured interval - lets a mid-loop settings change (enabling/
     * disabling, or changing the target playlist) take effect promptly
     * instead of waiting out whatever interval was live when the loop last
     * slept.
     */
    private fun startAutoSyncLoop() {
        if (autoSyncJob?.isActive == true) return
        autoSyncJob =
            serviceScope.launch {
                var lastRunAtMs = 0L
                while (true) {
                    delay(AUTO_SYNC_CHECK_INTERVAL_MS)
                    val playlistId = appPreferences.autoSyncPlaylistId
                    val now = System.currentTimeMillis()
                    val dueAtMs = lastRunAtMs + appPreferences.autoSyncIntervalSeconds * MS_PER_SECOND
                    if (appPreferences.autoSyncEnabled && !playlistId.isNullOrBlank() && now >= dueAtMs) {
                        lastRunAtMs = now
                        runAutoSync(playlistId, now)
                    }
                }
            }
    }

    private suspend fun runAutoSync(
        playlistId: String,
        atMs: Long,
    ) {
        val message =
            when (val result = runCatching { tasteProfileRepository.syncPlaylist(playlistId) }.getOrNull()) {
                is SyncResult.Success -> "Auto-synced — ${result.added} new, ${result.removed} removed."
                is SyncResult.Refused -> "Auto-sync skipped: ${result.reason}"
                is SyncResult.Failed -> "Auto-sync failed: ${result.error}"
                null -> "Auto-sync failed: unexpected error"
            }
        autoSyncStateHolder.update(atMs, message)
    }

    companion object {
        private const val POLL_INTERVAL_MS = 3000L // app.py:1105 — "correct for a live player"
        private const val AUTO_SYNC_CHECK_INTERVAL_MS = 30_000L
        private const val MS_PER_SECOND = 1000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, NowPlayingService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NowPlayingService::class.java))
        }
    }
}
