# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- The root screen of every tab shows its destination's icon before the title.
- Audio: playlist folders in the playlist picker, as headings with their playlists indented beneath
  them; the picked playlist is marked.
- Remote, from 840 dp: a Next Up card in the side column with the next item's name and arrangement,
  labelled Next item and Previous item buttons, and Back to live while an item is cued.
- An app icon: a deck of slides with the live one in front, with a themed (monochrome) layer; the
  debug build's icon carries a `DEBUG` ribbon.
- Debug builds draw a `DEBUG` ribbon across the top corner of the app.
- This changelog.

### Changed
- Beside the navigation rail, lists and grids draw under the gesture bar and their last item
  scrolls clear of it.
- The Audio destination uses a double-note icon.
- A playlist's change connection is retried after 2, 4, 8, 16, then 30 s instead of every 2 s.

### Removed
- The back arrow on Settings; the system Back and the More tab leave it.

### Fixed
- Audio: a playlist's tracks are read again after the connection to ProPresenter returns.
- In two panes with navigation buttons at the side, the list pane is no longer padded for them.

## 0.2.0 - 2026-10-07

### Added
- A `CLEARED` mark on the slide that was live before a clear, on the grid, the list view, the
  library grid and the Remote.
- Playlist changes made in ProPresenter arrive without a manual refresh.
- A `PAUSED` mark on a paused media or audio playlist item and on a paused audio track.
- Settings: an Orientation setting (Follow system, Portrait, Landscape).

### Changed
- The grid's Previous and Next step from the cleared slide after a clear.
- A group's name is shown only on the first slide of each occurrence of the group.
- In two panes, Back takes the list pane from the playlist to the tree and keeps the open item.
- Audio tracks are triggered by their uuid.
- Only the newest failure message is shown.

### Fixed
- The More list is kept across a rotation.
- The system Back in the two-pane layout no longer closes the app.
- Transports whose stream URL ProPresenter rejects are shown as not available.

## 0.1.0 - 2026-10-06

### Added
- Connect to ProPresenter by discovery or by entering a host; auto-connect to the saved host.
- Presentation: the playlist tree, a playlist screen with type icons and coloured headers, a slide
  grid and list view that follow each item's arrangement, library browsing with search, and an
  item screen for media, audio and live-video items.
- Remote: the live and next slide, a cue sidebar, item steps, an arrangement banner with Re-sync.
- Clear: layer clears, clear groups and Clear All.
- Macros, Timers, Looks, Props and Audio tabs, fed by one status stream.
- Settings: keep-awake, connection and About.
- A navigation bar or rail that fits the destinations to the screen, with a More list.
- A `.debug` application id for debug builds and optional release signing.

[Unreleased]: https://github.com/greenfodor/ProPresenterRemoteCommunityEdition/compare/6bf196e...HEAD
