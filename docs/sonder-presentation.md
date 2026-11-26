# Sonder presentation on Metrolist KMP

Android enters through `MainActivity` → `MetrolistApp`. The Android route tree uses the shared presentation components; `shared/ui/mobile/MobileApp.kt` is not its root.

The design reference is Sonder's `ui/glow` on branch `claude/serene-ptolemy-yoa9t4`: Inter, a teal black base, cover-driven mesh, flat track rows, small cover shelves, quiet grouped settings, and a floating Home / Search / Library bar. Artist names use Archivo Condensed Black. New surfaces follow those elements.

Presentation ownership:

| Surface | Shared presentation |
| --- | --- |
| Home, feed sections | `SonderHome`, `HomeContent`, `BrowseContent` |
| Search and history | `SearchScreen`, `SonderSearchResults`, `HistoryEntryRow` |
| Library and automatic collections | `LibraryContent`, `SonderMediaRow`, `YTItemCards` |
| Album, playlist, podcast, artist | `CollectionHeader`, `AlbumComponents`, `PlaylistComponents`, `PodcastContent`, `ArtistScreen` |
| Mini player, full player, queue, lyrics | `MiniPlayer`, `SonderPlayerScreen`, `SonderPlayerPresentation`; existing KMP lyrics renderer |
| Media, selection, account, lyrics and device menus | Shared menu content, `PlayerMenuComponents`, `Material3MenuGroup`, `AccountPopupDialog` |
| Settings and platform subpages | `SharedSettingsScreen`, `SettingsSubScreen`, `SonderTopBar`, `SonderToolbar` |
| Statistics, Listen Together, onboarding | Existing data/state flows with Sonder presentation and controls |

Authentication, repository calls, local database, library management, stream resolution, playback, downloads, lyrics providers, casting and integrations remain Metrolist KMP. The UI does not introduce a second backend.

`SonderScreenshotsTest` renders shipped presenters with local fixture artwork. GitHub Actions uploads the PNGs and the test report as `sonder-screen-review` before assembling the signed APK. These renders verify layout; they do not replace live account, streaming, casting or device testing.

The `.next` application id remains in place so this migration build can coexist with the original Sonder installation. Existing data import is separate from this presentation change.
