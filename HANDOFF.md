# Sonder: Handoff

Sonder is a personal YouTube Music client for Android, written in Kotlin with Jetpack Compose.
The UI is new (the "Cover Glow" design, option 6). Everything underneath (YouTube Music API,
playback, downloads, lyrics sources, account, settings storage) is
[BitChord](https://github.com/kushagrasinghx/BitChord). Parts of the lyrics engine, its animations
and AI translation come from Metrolist; the soft cover shadow comes from ViVi Music.

This document is for whoever picks the project up next: what exists, where it lives, how to build,
test and ship it, what the owner cares about, and what is still open.

---

## 1. Quick facts

| | |
|---|---|
| Repository | `centiox2/Music-App` (GitHub) |
| Working branch | `claude/elegant-clarke-w6bo53`. All work so far is here; no pull request has been opened |
| App name / icon | **Sonder** (renamed from "Cover Glow"); icon in `app/src/main/res/mipmap-*` |
| Application id | `com.coverglow.music` (prod), `com.coverglow.music.dev` (dev flavour) |
| Version | `1.8.1` (`build.gradle.kts`: `bitchordVersionName`, code 28); the phone shows `1.8.1-beta1` |
| SDK levels | minSdk 26, targetSdk 36, compileSdk 37 (InnerTubeX needs 37 to compile) |
| UI code | `app/src/main/java/com/music/bitchord/ui/glow/` (~13k lines, all written for Sonder) |
| Entry point | `MainActivity` → `setContent { GlowApp() }` |
| Owner's phone | OPPO CPH2727, Android 16 (API 36), ColorOS. Logs from it are full of harmless OEM noise (`UAH_JNI`, `IJankManager`, `SchedAssist`) |
| Tests | 1,085 app unit tests + 81 shared-module tests, all passing at hand-off |

### Git rules the owner set
- Commit and push only to `claude/elegant-clarke-w6bo53`: `git push -u origin claude/elegant-clarke-w6bo53`.
- **Do not open a pull request unless asked.**
- No model names or identifiers in commits or code.
- Commit messages end with the session's attribution trailer, as the existing commits do.

---

## 2. Repository layout

```
Music-App/
├── app/                      Android app (BitChord's app module + the Sonder UI)
│   └── src/main/java/com/music/bitchord/
│       ├── MainActivity.kt, BitChordApplication.kt
│       ├── ui/
│       │   ├── MainViewModel.kt     BitChord's view model (library, lyrics, home feeds…), extended for Sonder
│       │   └── glow/                ← the whole Sonder UI (see §3)
│       │       └── lyrics/          ← Metrolist lyrics engine port
│       ├── playback/               BitChord: PlaybackService (~8k lines), PlayerConnection, queue, crossfade, EQ…
│       ├── download/               BitChord: downloads, tagging, offline manifests
│       ├── data/                   BitChord data + Sonder additions (AppLog, LyricsCache, UserLyrics, LibrarySnapshot)
│       └── widget/, wear/, auth/
├── shared/                   Kotlin Multiplatform "shared" module: YouTube Music client (Innertube),
│                             lyrics providers, Http client, YtMusicRepository, models
├── sharedUi/                 Shared Compose bits (PlaybackPosition, artwork pixel helpers)
├── wearos/                   Watch app (skipped in builds with -Pbitchord.bundleWear=false)
├── native/                   Native code BitChord uses
└── HANDOFF.md                this file
```

The old BitChord UI was removed in the first round. `ui/` now holds only `MainViewModel.kt`,
`glow/` and `performance/`.

---

## 3. The Sonder UI (`ui/glow/`)

| File | What it is |
|---|---|
| `GlowApp.kt` | Root composable: tabs, page stack (with a `SaveableStateHolder` per page), mini player (swipe gestures, cover-tinted colour, progress line), full-player sheet, toasts, multi-select bar, crash notice at launch, `fractionOf()` helper |
| `GlowState.kt` | Shared UI state and actions: `Route` (navigation), `PlayerPane`, toasts, `push/pop/selectTab`, `openArtistOf/openAlbumOf`, likes (`LikedSongsCache`), multi-select (`LocalSelectionPool`, `LocalSelectionPlaylist`), `syncAccount`, song menu state |
| `GlowTheme.kt`, `GlowPrefs.kt` | Colours/typography (Inter + Archivo for the artist page) and Sonder-only preferences (liquid background, shadow strength, glow intensity, swipe on mini player, auto-sync…) |
| `GlowComponents.kt` | Shared pieces: `Cover`, `SquareCover`, `SongRow`, `TrackRow`, `GlowSheet` (takes an optional colour), `playerSheetColor()`, `DownloadedBadge`, `DownloadedMark`, `isFullyDownloaded()`, chips, cards, dialogs |
| `GlowArt.kt` | `ArtImage` (YouTube thumbnail candidates, HD fallbacks), `artCandidates()`, `coverShadow()` (soft ViVi-style shadow; draws only *outside* the shape) |
| `GlowMotion.kt` | `pressScale`, `staggeredEntrance`, `popOnChange`, `rememberReduceMotion` |
| `GlowPalette.kt` | Cover colours: `CoverPalette(colors, weights)`, k-means in OKLab, `ArtworkPalettes` cache, `rememberGlowPalette`, `miniPlayerTint` |
| `GlowMesh.kt` | Colour-mesh background (weighted blobs): used when the cover isn't the source of colour, and on Android 11 and older |
| `LiquidArtwork.kt` | Apple-Music-style liquid background built from the cover itself (§5.2) |
| `PlayerScreen.kt` | Full player: portrait, landscape and tablet layouts; `PaneSwitcher` (cover/lyrics/queue); artwork; title row; seek bar; controls; pane bar; queue pane; lyrics pane; `PLAYER_ART_PX` |
| `HomeScreen.kt` | Home feed, inline mood chips, quick picks, downloads strip |
| `SearchScreen.kt` | Search, typeahead, results, browse (moods/charts/releases) |
| `LibraryScreen.kt` | Library tabs (playlists, songs, albums, artists, downloads), Liked songs, Downloads page, History |
| `DetailScreen.kt` | Album/playlist pages and the redesigned artist page (Archivo font, `overlapAbove`) |
| `EditPlaylistScreen.kt` | Reorder/remove/rename a playlist |
| `Sheets.kt` | Song menu, save-to-playlist, new playlist, sleep timer, song details |
| `LyricsMenu.kt` | Lyrics menu (Metrolist style): source picker, refetch, edit, offset, translation |
| `DevicesScreen.kt` | Output devices, Listen Together, volume slider |
| `SettingsScreens.kt`, `SettingsMore.kt` | Settings home and sub-pages (theme, playback, storage, lyrics, translation, privacy, backup, Last.fm/ListenBrainz…) |
| `LogsScreen.kt` | Settings → Logs list and the log viewer |
| `SignInScreens.kt` | Onboarding and sign-in |
| `lyrics/GlowLyrics.kt`, `LyricsLine.kt`, `LyricsModel.kt` | Metrolist lyrics view: word/syllable sync, background vocals, scrolling, selection/share |
| `lyrics/LyricsTranslator.kt` | AI translation (Google default; OpenRouter, DeepL and others) |

### Navigation
- `GlowState.stack: List<Route>` on top of the selected tab (`tab`). `push()` ignores a duplicate
  of the current page. Back pops; at a tab root, back closes the player and selection first.
- Routes: `Detail`, `Discography`, `Mood`, `EditPlaylist`, `Explore`, `Liked`, `Downloads`,
  `History`, `Settings*`, `Equalizer`, `JoinSession`, `SettingsLogs`, `LogView(path, title)`.
- Full-screen pages that hide the mini player and tabs are listed in `chromeHidden` in `GlowApp.kt`.
  Add new full-screen routes there.
- The player is a separate sheet (`state.playerOpen`), with `state.playerPane` ∈
  `NOW_PLAYING | QUEUE | LYRICS`.

---

## 4. How the UI talks to BitChord

- `MainViewModel` (BitChord's, extended) is the data layer for screens: home feeds, explore,
  library (`LibrarySnapshot` persisted on disk, loaded on IO), lyrics (`loadLyrics`,
  `refetchLyrics`, provider selection, edits), playlists (`removeFromPlaylist` is optimistic).
- `GlowState.actions` is BitChord's `PlaybackActions`: play lists, shuffle, radio, queue edits,
  downloads.
- `PlayerConnection` turns the Media3 `MediaController` into `PlayerState` (song, queue, duration…)
  and a `PlaybackPosition`. Position is polled every 500ms while playing and only while the app is
  in the foreground.
- `PlaybackService` (BitChord) owns ExoPlayer, the media session, the queue
  (`BoundedContextRequests`, `QueueWindow`, autoplay), crossfade, EQ, scrobbling and casting. It runs
  in the **same process** as the UI, so a crash in it closes the app.
- Settings live in BitChord's `AppSettings` (shared module) plus `GlowPrefs` for Sonder-only
  settings.

---

## 5. Feature notes, by area

### 5.1 Player layout (owner-approved; don't move things casually)
The owner supplied a reference picture and is particular about positions. Portrait layout (inside
`PortraitPlayer`, in dp):
- Header, then `Spacer(weight 1)` (spare height goes **above** the cover), cover (full width,
  square), **30** gap, title row, **26** gap, seek bar, **25** gap, controls, **32** gap, pane bar.
- Bottom space under the pane bar: `max(12, 50 − navigation-bar inset)`, so the bar sits about
  50dp off the bottom edge counting the system navigation area. The owner likes where the bottom
  bar sits.
- On a 390×844dp screen this matches the reference within about 2dp (cover 124–474, seek ~588,
  controls ~675, bar 742–788).
- The title row has heart (when signed in) and ⋯ on the right. The cover does **not** shrink when
  paused; the owner asked for that to be removed.

### 5.2 Liquid background (`LiquidArtwork.kt`)
Built from the owner's research note on Apple Music's web player, using the values recovered from
the reconstructed implementation:
- Four copies of the cover at 125/80/50/25% of the **long** side of the screen (the original uses
  width on landscape). They rotate at +0.003, −0.008, −0.006 and +0.004 rad per 30fps tick; the two
  small ones also orbit.
- **Twist** (AGSL `RuntimeShader`, PixiJS TwistFilter, angle −3.25, radius 0.7 × long side), then
  **blur** (σ ≈ long side ÷ 11, tile mode CLAMP), then **saturation** (1.9, below the original's
  2.75 so white text stays readable). Dark mode then scales to 0.82; light mode lifts towards white.
- Rendered at **¼ resolution** and scaled up (`SCALE = 4`). Ticks at ~30fps, sleeping between
  ticks.
- Android 13+: full pipeline. Android 12: no twist. Android 11 and older: `GlowMesh` fallback.
- The cover cross-fades over 1.4s when the song changes; the old cover stays until the new one has
  loaded. `LiquidCovers` caches the decoded 200px covers and the clock.
- A light scrim on top (0.12 → 0.18 → 0.7 down the screen) keeps the controls readable.
- **Not verifiable in tests:** Robolectric ignores `RenderEffect`, so the background renders as a
  flat colour in screenshots. A Python simulation (`scratchpad/liquid-preview.png` at the time)
  was used to tune it. Check changes on a device.
- When "colour from artwork" is off, `GlowMesh` draws a fixed colour instead.

### 5.3 Cover shadow and pane switching (much iteration; read before touching)
- `coverShadow()` draws a `setShadowLayer` round rect with `clipOutPath(outline)`, so **only the
  shadow outside the cover** is drawn. Drawing the filled shape made a dark square show through
  the cover whenever it faded or hadn't loaded. Paint and path are cached in `drawWithCache`.
- `PaneSwitcher` (in `PlayerScreen.kt`) replaced `AnimatedContent`, because AnimatedContent clips
  each pane to its bounds during a switch and the cover is exactly as wide as the pane, so its side
  shadows vanished mid-switch. The switcher:
  - fades panes with `graphicsLayer { alpha; compositingStrategy = ModulateAlpha }`, so there is no
    offscreen layer;
  - extends each pane's layer by `PANE_MARGIN` (20dp) on both sides and puts that margin inside the
    pane, so the clip edge is the screen edge;
  - uses plain `Animatable`s and always ends with only the selected pane composed.
- Tests: `GlowScreenshotTest.paneSwitchFrames` measures the side-shadow darkness frame by frame
  and fails if it is cut off; `paneSwitchSettles` checks only the new pane remains afterwards.

### 5.4 Colours from the cover
- `coverPalette()` runs k-means (k=6) in OKLab on a 64px decode, merges near-identical clusters and
  drops colours under 1.5%. The weights are real proportions; the owner wanted accurate colours,
  not boosted ones.
- `miniPlayerTint()`: the mini player and the menus opened from the player (song menu, lyrics menu)
  take the cover's dominant colour at fixed lightness (0.2 dark / 0.86 light).

### 5.5 Images
- `Cover()` fetches at about 1.25× its on-screen pixel size (120–544px) unless `px` is given. List
  rows used to download full-HD video frames.
- The playing song's cover is fetched at `PLAYER_ART_PX` (1080) everywhere it appears (mini player,
  full player, lyrics header), so opening the player finds it already in memory.
- `artCandidates()`: YouTube video thumbnails fall back maxres → hq720 → sd → hq (≤200px rows use
  mq/hq).

### 5.6 Lyrics
- Engine: Metrolist port (`ui/glow/lyrics`). Word and syllable sync, background vocals,
  auto-scroll, tap to seek, selection and share.
- **Parsing is Metrolist only.** `lyrics/MetrolistLyricsParser.kt` is a verbatim port of Metrolist's
  `LyricsUtils`. Provider results are written out as Metrolist's rich-sync LRC
  (`LyricsModel.toMetrolistText()`) and read back by `MetrolistLyrics.parseLyrics`; BitChord's own
  syllable split and guessed background vocals are no longer used (they caused the jumpy animation).
- **Fetching** (`MainViewModel.fetchInOrder`): every enabled provider is asked at once, and
  answers are taken strictly in the user's order (a lower one wins only after everything above it
  missed or passed its 8s; 25s in all). One-at-a-time, as Metrolist does it, let a slow top source
  hold lyrics up for 8s each. Each lookup logs `lyrics for <id>: <source> … order …` (tag `Sonder`).
- The cache records the order a result was found under (`LyricsCache.Saved.order`, or `picked` for
  a hand-picked provider); found under another order, it's only a fallback while the sources are
  asked again. A downloaded file's embedded lyrics are used offline, and as the fallback online.
  Changing the order or "prefer word-synced" triggers a new lookup. The query comes
  from `data/LyricsQuery.kt`: bracketed parts and "official video" words go, and for a music video
  (or a "VEVO" / "- Topic" channel) the artist is taken from an "Artist - Title" title, since the
  uploader isn't the artist.
- **Manual search:** lyrics menu → "Search lyrics" (`LyricsSearch` in `LyricsMenu.kt`): edit title
  and artist, every enabled provider answers in parallel, tap a result to use and cache it.
- Added NetEase (line sync only; its word-sync API is encrypted). SimpMusic and Megalobiz are dead
  upstream.
- **Cache:** `data/LyricsCache.kt` saves the found lyrics (with word timing) per videoId to
  `filesDir/lyrics_cache` (JSON, max 3,000 files, least recently used pruned). Read before the
  network, so lyrics show instantly and offline. "Refetch" in the lyrics menu bypasses it; picking a
  provider by hand overwrites it. Settings → Storage → "Clear saved lyrics".
- User edits: `data/UserLyrics.kt` (`filesDir/lyrics_edits/*.lrc`) win over everything.
- Lyrics are only looked up once the player has been opened (`lyricsWanted` in `GlowApp`), to keep
  launch light.
- Performance: inactive lines read the position through a lambda and only update inside their own
  time window (`snapshotFlow`, clamped). Don't pass the per-frame position as a parameter again; it
  rebuilt every visible line on every frame.

### 5.7 Library, playlists, downloads
- Library loads from a disk snapshot first, then refreshes; pull to refresh syncs. Auto-sync runs
  after the app returns from the background if 15 minutes have passed (setting).
- Liked songs: full list cached in `LikedSongsCache`, so the count is exact. It has search and the
  shared sort menu, and glows with its own white tile (`GlowScreen(glowColor = …)`).
- **Playlist pages** (not albums) use `CollectionHeader` (`Collections.kt`), the Liked songs layout:
  cover beside the title, then sort, download, save, ⋯, shuffle and Play in one row. The count
  YouTube puts in the subtitle is dropped (`subtitleWithoutCounts`), so "2 tracks" and "2 songs"
  no longer both show. Sorting (`SongOrder`) plays the sorted list; the default order keeps the
  page's own queue context.
- Library → Playlists: sort menu (recent, A–Z, Z–A, downloaded first), grid/list, filters (All,
  Pinned, Downloaded). Sort and layout live in `GlowPrefs`, not state inside a lazy item.
  **Pinning:** long-press a playlist, or "Pin to library" in its ⋯ menu; pinned ones stay on top
  (`GlowPrefs.pinnedPlaylists`).
- Song rows show a small heart for liked songs (`LikedMark` in `SongRow`), except on Liked songs
  (`LocalShowLikedMark`). `likeStatuses` now includes the full liked list, not only its first page.
- Multi-select by long-press, with a bottom selection bar (add to playlist, remove from your
  playlist, queue).
- Songs tab and Downloads page are **one lazy item per song**. They used to compose the whole
  library at once; keep it that way.
- Lists with remote keys drop duplicates (`distinctBy` with the key). A duplicate key crashes a
  `LazyColumn`, and YouTube does send duplicates.
- Fully downloaded playlists and albums show `DownloadedBadge` (top-right of the cover) in Library
  list and grid, on the detail page and on Liked songs. `isFullyDownloaded()` requires **every
  track on disk**, not merely requested. Downloaded songs show `DownloadedMark` (download icon), not
  ticks.

### 5.8 Queue
- The queue pane shows Earlier / Up next / Rest of playlist from BitChord's bounded-context
  snapshot. Swipe to remove (no red background at rest). Shuffle covers the whole playlist
  (`QueueShuffle` moves earlier items behind the current song first). "Play next" was removed from
  the player's own menu; "Add to" works.

### 5.9 Mini player
- Tap opens the player. Swipe up opens it, swipe sideways skips, and swipe **down stops playback
  and clears the queue** (`controller.stop(); clearMediaItems()`). The swipe can be turned off in
  settings.
- The progress line is its own small composable (`MiniProgress`) that only redraws.
- **Crash fixed in the last round:** with the queue empty, the player's container is 0 tall, and
  the drag effects divided by its height (0 ÷ 0 = NaN corners). Always use `fractionOf(part, whole)`
  for size-relative maths in `graphicsLayer`.

- Colour: `miniPlayerTint` uses `coverAccent()` (the dominant colour unless it is a backdrop:
  greyish, near black or near white; then the most vivid colour with a real share). The old tint is
  kept until the new cover's palette is ready. Play/pause is a bare icon; a heart replaced "next".

### 5.9a Other screens (this round)
- **Listen together** has its own page (`Route.ListenTogether`, `ListenTogetherScreen` in
  `DevicesScreen.kt`), opened from a button in Home's top bar (green dot while a session is live)
  and from a row in the devices sheet.
- **Cover glow:** `glowVisible()` lifts each glow colour into a visible lightness (keeping its hue),
  so dark covers glow too; before, near-black colours were drawn at the page's own lightness.
- **Artist page:** "Latest release" picks the newer of the first album and first single by the
  year in their subtitles (`latestRelease`); singles used to always win.
- **Light mode** has its own palette (`LightGlowColors`): bright cool page, solid grey text, white
  sheets and bars, Sonder teal for primary controls. The status/navigation bar icons now follow
  the app's theme rather than the phone's.

### 5.10 Networking (shared module)
- `shared/.../data/Http.kt`: one OkHttp client shared by Innertube and ExoPlayer. **1-minute
  keepalive, 15s HTTP/2 pings**, and `Http.onNetworkChanged()` evicts idle connections.
  `BitChordApplication.watchNetworkChanges()` calls it, plus `Innertube.onNetworkChanged()`, when
  the default network changes. Without this, a connection that died while the phone slept or
  changed network was reused until the app was force-stopped (Home, artist pages and search hung).
- `Innertube.ensureSessionScope()`: every signed-in request first needs the account's "session
  scope", which comes from reading `music.youtube.com` (it matters for brand channels; the owner
  uses one). It now:
  - reads in the background (`scopeJobs`) with a 20s limit;
  - makes callers wait at most **1.5s** (`SCOPE_WAIT_MS`), using `join`, not `await`;
  - remembers a failure for 60s;
  - resets on network change or sign-in;
  - logs `session scope: …` with timings.

  Tests are in `shared/src/jvmTest/.../innertube/SessionScopeTest.kt`; the test seam is
  `shellPageForTest`.
- Explore no longer fetches artwork per mood/genre (it was about 50 requests at launch for pictures
  Sonder never shows).

### 5.11 Logging and crash reports (`data/AppLog.kt`, `LogsScreen.kt`)
- `AppLog.install()` runs first in `BitChordApplication.onCreate()`:
  - starts `logcat --pid=<own pid> -f filesDir/logs/session.log -r 1024 -n 3` (rotating, about 4MB);
  - renames the previous launch's files to `previous-session.log*`;
  - installs an uncaught-exception handler that writes `logs/crashes/crash-<time>.txt` (stack trace
    plus the last 800 log lines), keeps 10, and flags it for the next launch.
- `DebugLog.sink` (the shared module's logging) now writes to logcat on **all** builds (it was debug
  only), so YouTube client and lyrics activity appears in the log. The tag is `BitChord`.
- `AppLog.event()` lines (tag `Sonder`) mark navigation, tabs, song menus and the mini-player
  swipe-away.
- Settings → Logs lists This session / Last session / crashes; the viewer colours by level and can
  copy or share. Sharing writes to `cache/shared/` and goes through the app's FileProvider
  (`${applicationId}.fileprovider`).
- After a crash, the next launch shows "Sonder closed unexpectedly last time" with **View log**.
- **How to debug a field report:** ask the owner for Settings → Logs → (crash or Last session) →
  share. Errors from OEM tags (`UAH_*`, `IJankManager`, `SchedAssist`, `OplusBracketLog`) and GMS
  phenotype are noise.

---

## 6. Building and shipping an APK

```bash
# Release build (skip the watch app, which is slower and isn't needed)
./gradlew :app:assembleProdRelease -Pbitchord.bundleWear=false
# Output: app/build/outputs/apk/prod/release/app-prod-<abi>-release-unsigned.apk
```

Kotlin may print `e: … metadata version` lines during the release build; they are non-fatal.

**Signing.** The APK the owner installs is signed with a self-made key that lived in the session's
scratch folder: `signing/coverglow.jks`, with the password in `signing/password.txt`. **That folder
is temporary and won't survive the container.** If the key is lost, a new key means the owner must
uninstall the old app before installing (Android rejects updates signed with a different key), and
the app's data is lost with it. Ask the owner whether they kept a copy of the key, or agree on a new
one and keep it somewhere durable (not in the repo).

```bash
BT=$(dirname "$(find / -name zipalign -type f 2>/dev/null | head -1)")
$BT/zipalign -f 4 app-prod-arm64-v8a-release-unsigned.apk aligned.apk
$BT/apksigner sign --ks coverglow.jks --ks-pass file:password.txt --out Sonder-arm64-v8a.apk aligned.apk
$BT/apksigner verify Sonder-arm64-v8a.apk
```
The owner's phone is arm64; send `Sonder-arm64-v8a.apk`.

---

## 7. Tests

```bash
./gradlew :app:testDevDebugUnitTest          # ~1,085 tests (Robolectric), ~5–8 min
./gradlew :shared:jvmTest                    # 81 tests
```

### Screenshot harness (`app/src/test/.../ui/glow/GlowScreenshotTest.kt`)
Gated by an environment variable; renders real screens with Robolectric:
```bash
GLOW_SHOTS=/some/dir ./gradlew :app:testDevDebugUnitTest --tests '*GlowScreenshotTest*' --rerun
```
- Phone size 390×844dp xxhdpi; long settings pages use `w400dp-h1900dp-xhdpi`.
- `launch(playing, light, positionMs, route, …)` sets up the app with a fake `PlayerState`
  (`playerOverride`); reduce-motion is on so frames are still.
- Images load from the real network (they can arrive late, so compare carefully).
- **Quirks learned the hard way:**
  - `shot()` can capture a stale frame after a switch. For anything timing-sensitive, hold the clock
    (`mainClock.autoAdvance = false`, `advanceTimeBy`) and use `shotNow()`, which waits for idle. The
    UI tree (`onAllNodes(...)`) is the reliable source of truth.
  - `RenderEffect` (blur/twist) isn't rendered, so the liquid background is a flat colour.
  - Robolectric tests need `@ConscryptMode(ConscryptMode.Mode.OFF)`.
  - Run the lyrics screenshot last in a test: the lyrics frame loop stops Compose from going idle.

### Regression tests worth knowing
| Test | Guards |
|---|---|
| `paneSwitchFrames`, `paneSwitchSettles` | side shadows during pane switches; old pane removed afterwards |
| `queueEmptiedWithThePlayerOpen`, `miniPlayerSwipeAwayEmptiesQueue` | the NaN-corner crash; the UI surviving an emptied queue |
| `playback/StopAndClearTest` | the real `PlaybackService` survives stop + clear |
| `data/AppLogTest` | crash report written and offered once; rotated logs read in order |
| `data/LyricsCacheTest` | word timing survives the cache round trip |
| `CoverPaletteTest` | colour proportions (90/10, 60/30/10) |
| shared `SessionScopeTest`, `SharedHttpClientTest` | failed/hanging/slow session reads; pings configured |

---

## 8. What the owner cares about (preferences gathered over the project)
- **Exact visuals.** They send screenshots and reference images. Match positions and gaps
  precisely; keep the cover and the bottom bar where they are unless asked.
- **Colour accuracy.** Backgrounds and tints should reflect the cover's real colours and
  proportions.
- **Smoothness.** No pop-in (image before shadow, colour flashes), no clipped shadows, fast startup.
- **Verify before building.** After a fix "didn't work" they asked that issues be confirmed solved
  before an APK is built. Reproduce with a test or frame capture first, and prove the test fails
  without the fix.
- Short, plain explanations of causes, then what changed.

---

## 9. Open items and known limitations

**Unverified on a real device (no emulator was available):**
- Liquid background look and performance (AGSL twist, blur), light-mode tone.
- Pane-switch shadows with the liquid background on.
- Effect of the network fixes (pings, eviction, 1.5s session wait) on the owner's phone. The next
  log will show `session scope: read in Nms` or why it failed.

**Known issues and ideas not done:**
1. **Slow first frame at launch.** The owner's log shows `Skipped 55 frames` / `Davey! 1405ms`:
   about 1.4s of main-thread work before the first frame. Not investigated. Start with
   `BitChordApplication.onCreate()` (many `init` calls) and the first composition of
   `GlowApp`/Home; profile with a release build.
2. **Why the session read is slow** on the owner's network is unknown (see above).
3. NetEase lyrics are line-synced only; SimpMusic and Megalobiz are dead.
4. Tablet and landscape layouts are functional but much plainer than portrait.
5. The home-screen widget still has BitChord's design.
6. Wear OS app: not built or tested in this project (`-Pbitchord.bundleWear=false`).
7. The Robolectric `shot()` stale-frame issue (test harness only).
8. Lint: about 90 `UnsafeOptInUsageError` (Media3 `@UnstableApi`) in BitChord code. Annotation
   hygiene only, no runtime effect.

**Feature backlog** (the engine already supports these; each mostly needs UI). The owner was
offered this list and hasn't chosen yet:
- Listening stats / "Wrapped" (`data/stats/ListeningStats`, `ArtistFacts`)
- Animated covers, "Canvas" (`data/canvas/*`: Spotify, Apple, Tidal), animated artist art
- Discord status (`data/discord`, `presence`)
- On-device music library (`LocalMediaRepository`)
- WebDAV backup / SMB network folders (`data/webdav`, `data/smb`)
- Other sources: JioSaavn and add-ons (`data/sources`)
- In-app updates (`AppUpdateChecker`)
- New ideas: music-video mode, Sonder-style widget, sleep-timer fade-out, smarter queue, full-screen
  karaoke lyrics, lyric image cards, offline mode switch, auto-download liked, Android Auto polish,
  app-icon shortcuts, double-tap cover to like.

---

**Playback fixes this round** (`PlaybackService.kt`, `StreamResolver.kt`):
- Download: a song with no Opus stream falls back to AAC, then the playback stream ("No
  downloadable audio" in the owner's log).
- Offline: when a song can't play offline, playback jumps to the next downloaded or cached song not
  heard in the last 30, or appends a shuffled mix of up to 20 saved songs; it used to loop the same
  few.
- Quality: the track selector forces the highest bitrate the format offers. `TrackLog` now writes
  to logcat on all builds, so the in-app log shows the chosen stream and bitrate.
- Automix was reviewed against the log: transitions ran without errors (DJ cues enter the next
  song around 16s in, tempo matched about 2–3.5% with pitch kept). Nothing changed.

## 10. Project history (one commit per round, newest last)

| Commit | Round |
|---|---|
| `4bb5a47` | Cover Glow UI on BitChord |
| `11b681b`, `3608c4b` | settings navigation, screenshot harness, tablet layout, launcher icon |
| `f490e58`, `45da3da`, `6f0ffb2` | Metrolist lyrics engine and animations; translation; settings pages; polish |
| `3f00c8d` | liquid background (v1), colours, cover shadows, HD thumbnails, playing marks |
| `13e42c3` | lyrics menu, NetEase, word-sync fixes |
| `1df9811` | library: liked count, multi-select, remove from playlist, snapshot, sync |
| `6c67691` | queue: whole playlist, shuffle, remove |
| `8b900cf` | rename to Sonder, new icon, artist page redesign, volume control |
| `5d13adf`, `8b4dc4d` | home loads once, queue fixes, colour-accurate mesh, lighter startup |
| `1df68cd` … `b50ad14` | player spacing; liquid background from the cover; menu colours; download badges; lyrics cache; shadow and pane-switch fixes |
| `6c395a6` | bug sweep and optimisation (lazy lists, duplicate keys, lyrics per-frame cost, image sizes) |
| `251da24` | network: pings, eviction, session-scope backoff |
| `9d91106` | in-app logs and crash reports |
| `1ff5dbe` | mini-player swipe crash (NaN corners); launch: session wait, mood artwork requests |

`git log` has the full detail; the commit messages explain causes as well as changes.

---

## 11. Gotchas checklist
- Size-relative maths in `graphicsLayer`/`drawWithCache`: use `fractionOf()`. Containers can be 0
  tall during exit animations.
- Never key a `LazyColumn` with raw remote IDs alone; dedupe or include the index.
- Don't put a whole list inside one `item {}`.
- Fading something with a shadow: use `ModulateAlpha` and make sure no ancestor layer clips at the
  shadow's edge (`AnimatedContent` does during transitions).
- Don't pass the playback position (which changes every frame) as a parameter to many composables;
  pass a reader lambda.
- `PlaybackService` shares the UI process: a crash there closes the app.
- Anything new that sends signed-in requests goes through `ensureSessionScope()`. Keep it
  non-blocking.
- New full-screen routes go in `chromeHidden`.
- The signing key must stay the same between builds the owner installs.
