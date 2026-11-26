# Sonder on Metrolist KMP

This branch (`claude/sonder-metrolist`) moves Sonder off BitChord (plus the ViVi and Metrolist
pieces it carried) onto **Metrolist KMP** only: Metrolist's engine (YouTube Music client,
playback, downloads, lyrics, sync, Listen Together) with Sonder's own interface on top.

- Base: `MetrolistGroup/Metrolist` @ `2e559b7` (2026-11-15), GPL-3.0, with its submodules
  (`metroproto`, `opusDecoder/src/main/jni/libopus`). Metrolist's agent guides are kept in
  `docs/metrolist/` for reference.
- The previous Sonder (BitChord engine, Sonder UI in `ui/glow`) is on `claude/serene-ptolemy-yoa9t4`.
- Owner's decisions: drop what Metrolist doesn't have (Automix/loop mix, add-on sources,
  bit-perfect USB, Listen Together with old-Sonder users); work here in phases while the
  current app keeps shipping; migrate the owner's data where Metrolist has a place for it.

## Phases
1. Metrolist building under Sonder's name and icon, app id `com.coverglow.music.next` (installs
   beside the current Sonder). CI: `.github/workflows/build-apk.yml` → release `sonder-next`.
2. Sonder's player (portrait, landscape, TV) and mini player on Metrolist's playback.
3. Home, Search, Library, album/artist/playlist pages on Metrolist's view models.
4. Lyrics (Sonder's lyrics view on Metrolist's providers), settings, TV remote support.
5. App id back to `com.coverglow.music`, data migration from the BitChord build, widget.

## Current integration

The running app uses only Metrolist KMP's models, view models, playback, database,
downloads, authentication, lyrics and sync. No BitChord or legacy Metrolist runtime
module is included. Sonder's original artwork palette extraction and weighted mesh
have been ported to common Kotlin UI code, along with its dark/light surfaces,
bundled Inter fonts, floating three-tab navigation, inset mini player and circular
transport controls. Phone, landscape and TV layouts use the same KMP player actions.
Browsing and detail pages use Metrolist's shared workflows with Sonder's theme;
menus, account management and settings remain Metrolist's implementations.

The build workflow verifies the artwork palette, contrast, navigation preferences
and settings restore before assembling and publishing the signed Android APK.
This remains the parallel-install `com.coverglow.music.next` build. Importing data
from the older BitChord app and switching back to its application ID remain a
separate migration phase; this build does not read the older app's private storage.
