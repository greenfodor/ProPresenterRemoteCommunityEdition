# ProPresenter Remote CE

An Android remote for ProPresenter that keeps each playlist item's arrangement. It shows every
item's slides in the arrangement chosen for that item, triggers cues by playlist item and cue
index, and follows the live slide over ProPresenter's HTTP `/v1` API on the local network.

**Unofficial.** This app is a community project. It is not affiliated with, endorsed by or
supported by Renewed Vision. ProPresenter is a trademark of Renewed Vision.

## What 0.3.0 provides

- **Connect.** Finds ProPresenter on the network or takes a host and port you type, and reconnects
  to the saved host when the app starts.
- **Presentation.** The playlist tree with its folders; a playlist screen with a type icon per item
  and coloured headers; a slide grid or list for each item, numbered in that item's arrangement,
  with the live and next slides marked, a group strip, a slide-size setting and Previous and Next;
  the libraries with search and a grid for any presentation in them; a card that starts a media,
  audio or live-video item. Changes made to a playlist in ProPresenter arrive without a refresh.
- **Remote.** The live slide and the next one, large; a cue list; Previous and Next; the next item
  of the playlist, which you can cue before making it live; a banner with Re-sync when ProPresenter
  plays the presentation in another arrangement. From 840 dp the cue list is always shown and the
  item controls sit in a card at the side.
- **Macros.** A tile per macro, grouped by collection; a tap triggers it.
- **Timers.** A card per timer with its reading, Start or Stop, and Reset.
- **Audio.** The audio playlists, with their folders, in a picker; the tracks of the chosen
  playlist; a tap plays a track; a bar shows what plays, with previous, play or pause, and next.
- **Looks.** A card per look; a tap makes it the live look.
- **Props.** A tile per prop with its thumbnail, grouped by collection; a tap shows or hides it.
- **Clear.** One button for every layer (slide, media, live video, props, messages, announcements,
  audio) and for each clear group.
- **Settings.** Keep the screen on (never, on the Remote tab, always); orientation (follow the
  system, portrait, landscape); auto-connect; disconnect.

The layout follows the screen: a bottom bar on a phone in portrait, a rail on wider screens, and
two panes (the playlist beside its slides) from 840 dp.

## What it does not do yet

- Shuffle, seek or skip within an audio track
- Messages
- The stage display
- Editing timers
- Editing anything stored in ProPresenter: the app only reads and triggers

## Requirements

- Android 10 or later
- ProPresenter 21.4.2 with the network API enabled in its Network settings. Other ProPresenter
  versions are untested.
- The phone or tablet on the same network as the ProPresenter computer

ProPresenter's network API accepts control from anyone on the local network without a password.
Use it on a network you trust.

## Install

1. Open the [latest release](https://github.com/greenfodor/ProPresenterRemoteCommunityEdition/releases/latest)
   on your Android device and download the `.apk` file.
2. Open the downloaded file. Android asks you to allow installs from the app you downloaded it
   with (your browser or file manager): allow it, then install.
3. Open the app and pick your ProPresenter computer from the list, or type its address and port.

Later versions install over this one and keep your settings.

## Build

You need a JDK (Gradle downloads the Java 25 toolchain it compiles with) and the Android SDK.

```bash
./gradlew build            # compile, static analysis and all unit tests
./gradlew assembleDebug    # the debug build, installed next to a release build
./gradlew assembleRelease  # the minified release build
```

`assembleRelease` signs the APK only when `local.properties` names a signing properties file; see
`CLAUDE.md`. Changes are listed in [CHANGELOG.md](CHANGELOG.md).

## Licence

Apache License 2.0. See [LICENSE](LICENSE).
