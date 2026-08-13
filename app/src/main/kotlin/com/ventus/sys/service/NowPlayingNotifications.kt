package com.ventus.sys.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ventus.sys.MainActivity
import com.ventus.sys.R

private const val CHANNEL_ID = "now_playing"
private const val NOTIFICATION_ID = 1

/** Notification channel/building/updating for [NowPlayingService] — split out to keep the service itself focused on the poll loop. */
class NowPlayingNotifications(
    private val service: Service,
) {
    fun createChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Live Scorer",
                NotificationManager.IMPORTANCE_LOW, // no sound/vibration — this is a status notification, not an alert
            ).apply {
                description = "Shows the currently-playing track while VENTUS scores it against your taste profile."
            }
        service.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun startForeground(text: String) {
        val notification = build(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            service.startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            service.startForeground(NOTIFICATION_ID, notification)
        }
    }

    fun update(text: String) {
        service.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build(text))
    }

    private fun build(text: String): Notification {
        val contentIntent =
            PendingIntent.getActivity(
                service,
                0,
                Intent(service, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat
            .Builder(service, CHANNEL_ID)
            .setContentTitle("VENTUS // SYS")
            .setContentText(text)
            // TODO: dedicated flat/monochrome notification icon —
            // ic_launcher_foreground is a full-color adaptive-icon layer, not
            // designed for the status bar. Works, doesn't look sharp.
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val NOTIFICATION_TEXT_IDLE = "Waiting for playback…"
    }
}
