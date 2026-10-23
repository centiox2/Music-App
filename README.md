# Cover Glow

A personal YouTube Music client for Android. The interface is the **Cover Glow**
design (option 6): dark, glassy, and tinted by whatever cover is playing. Everything
underneath it — the YouTube Music client, stream resolution, playback, downloads,
lyrics, sign-in, Listen Together, scrobbling — comes from
[BitChord](https://github.com/kushagrasinghx/BitChord).

> Not affiliated with, endorsed by, or connected to YouTube or Google.

## What's where

| Path | What it is |
| --- | --- |
| `app/src/main/java/com/music/bitchord/ui/glow/` | **The new UI** — every screen of the Cover Glow design, in Jetpack Compose |
| `app/src/main/java/com/music/bitchord/playback/PlaybackActions.kt` | BitChord's play / queue / radio / download logic, lifted out of its old UI so any interface can drive it |
| `app/src/main/java/com/music/bitchord/data/PlayHistory.kt` | On-device play history (the History screen's "On this phone" tab) |
| `app/src/main/java/com/music/bitchord/ui/MainViewModel.kt` | BitChord's view model: home, search, library, detail pages, account |
| everything else under `app/`, `shared/`, `sharedUi/`, `native/` | BitChord, unchanged apart from removing its own screens |

### Screens

Home (chips, featured mix, quick picks, shelves, loading skeleton, offline state) ·
Search (recent searches, moods & genres, suggestions, results with Top/Songs/Albums/…
filters, voice search) · Mood / genre · Explore (new releases, charts, moods) ·
Library (playlists, songs, albums, artists, downloads) · Liked songs · Downloads
(progress, storage, empty state) · History · Album / playlist · Artist ·
Discography & About · Player (cover glow, lyrics with word-by-word karaoke, Up next
with drag to reorder, playback-error card, landscape/tablet layout) · Song menu ·
Save to playlist · New playlist · Edit playlist · Sleep timer · Song details ·
Devices & Listen together · Join a session · Settings · Theme · Equalizer & audio ·
Lyrics sources · Downloads & storage · Streaming quality · Last.fm · Sign in ·
Sign-in error · Notifications prompt · Confirmation toasts · Light theme.

The lock-screen / notification controls are Android's media notification, driven by
BitChord's playback service.

## Building

Requirements: JDK 17+, Android SDK with platform 37, build-tools 36, NDK
27.0.12077973 and CMake 3.22.1 (Android Studio installs these when it syncs).

```sh
./gradlew :app:assembleProdDebug -Pbitchord.bundleWear=false
```

The APK lands in `app/build/outputs/apk/prod/debug/`
(`app-prod-arm64-v8a-debug.apk` for most phones). The `dev` flavor installs
side by side as "Cover Glow Dev".

Optional, in `local.properties`: `LASTFM_API_KEY` and `LASTFM_SECRET` for Last.fm
scrobbling, `LISTEN_TOGETHER_SERVER` for a Listen Together server of your own.

## Licence

BitChord is GPL-3.0, so this app is too — see [LICENSE](LICENSE). The Inter font is
under the SIL Open Font License — see [INTER-LICENSE.txt](INTER-LICENSE.txt).
