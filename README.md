# VENTUS // SYS

A native Android app that scores your music taste in real time against your own Spotify listening history — no server, no third-party account, just your phone talking directly to Spotify's Web API and a local on-device taste profile.

VENTUS builds a "taste profile" from a playlist you already love (or your Liked Songs), then compares whatever's currently playing against it using a 7-axis weighted feature model (energy, valence, danceability, tempo, acousticness, instrumentalness, loudness). Everything runs locally: your Spotify data never leaves your device except to talk to Spotify itself and to [ReccoBeats](https://reccobeats.com) (a free, open audio-features API used since Spotify restricted its own `/audio-features` endpoint in late 2024).

## Features

- **Live Scorer** — real-time score + radar chart for whatever's currently playing, updated every 3 seconds via a foreground service
- **Track Vault** — every track VENTUS has ever scored, searchable and sortable, with Camelot key notation
- **Dashboard** — KPIs, histograms, and a 7-axis radar for your whole taste profile
- **Playlist Audit** — score an entire playlist at once against your taste profile
- **Discover** — search Spotify and see how close a track is to your taste (7-axis weighted distance)
- **Queue Analyzer** — score your current Spotify queue
- **Signals** — top tracks/artists and recently-played, with a badge for anything that would score 80%+
- **Auto-Add** — automatically add tracks above a score threshold to a target playlist, with duplicate detection
- **Master Vault** — import and browse multiple playlists independently of your main taste profile, with CSV export
- **Session History** — a log of everything scored this session, exportable to CSV
- **Auto-sync** — keeps your taste-profile playlist in sync with Spotify on an interval you set

## How it works

1. Log in with your own Spotify Client ID via PKCE OAuth (no client secret needed — see [Setup](#setup) below)
2. Pick a playlist (or Liked Songs) to build your taste profile from
3. Play something on Spotify — VENTUS picks it up via the Web API's currently-playing endpoint, resolves its audio features through ReccoBeats, and scores it against your taste profile

Audio features are cached locally in a Room database, so a track only needs to be resolved once. Auth tokens are stored in `EncryptedSharedPreferences` (Android Keystore-backed).

## Setup

You need your own Spotify Client ID — VENTUS doesn't ship with one, since Spotify's terms don't allow bundling a shared one:

1. Create an app at the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard)
2. Add a Redirect URI: `ventus://oauth/callback`
3. Copy the Client ID and paste it into VENTUS on first launch

No client secret is needed — VENTUS uses the Authorization Code + PKCE flow, which is designed for public clients like native apps.

## Building from source

Requirements: JDK 17, Android SDK (compileSdk 36, minSdk 26).

```bash
git clone <this-repo-url>
cd <cloned-directory>
./gradlew assembleDebug
```

The debug build installs and runs with no further setup. For a release build, R8 minification/obfuscation is enabled by default (see `app/proguard-rules.pro`); you'll need your own signing key — copy `keystore.properties.example` to `keystore.properties` and fill in your own keystore details, or leave it absent to get an unsigned release build.

```bash
./gradlew assembleRelease
```

## Download

Grab the latest signed release APK from this repo's [Releases page](releases/latest) — no build required.

## Tech stack

Kotlin, Jetpack Compose, Hilt, Room, Retrofit + kotlinx.serialization, AppAuth-Android (PKCE OAuth), Coil.

## License

MIT — see [LICENSE](LICENSE).
