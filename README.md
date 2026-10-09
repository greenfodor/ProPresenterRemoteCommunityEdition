# ProPresenter Remote CE

An Android remote for ProPresenter, for phones and tablets. It browses the playlists and
libraries, shows and triggers slides, follows what is live, and controls macros, timers, audio,
looks, props, stage layouts and clears, over ProPresenter's HTTP `/v1` API on the local network.
Every playlist item's slides are shown in the arrangement chosen for that item.

**Unofficial.** This app is a community project. It is not affiliated with, endorsed by or
supported by Renewed Vision. ProPresenter is a trademark of Renewed Vision.

## What 0.4.0 provides

- **Connect.** A splash while the app reconnects to the saved host at start; otherwise a Connect
  screen with the last used host as a card, a card per ProPresenter found on the network, and
  fields for a host and port you type.
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
- **Stage.** A card per stage screen with the layout it shows; a tap opens that screen's layouts
  as thumbnail tiles, and a tap on a tile sets that layout.
- **Clear.** A button per layer (slide, media, live video, props, messages, announcements, audio)
  and per clear group.
- **Settings.** Keep the screen on (never, on the Remote tab, always); orientation (follow the
  system, portrait, landscape); auto-connect; disconnect.

The layout follows the screen: a bottom bar on a phone in portrait, a rail on wider screens, and
two panes (the playlist beside its slides) from 840 dp.

## What it does not do yet

- Shuffle, seek or skip within an audio track
- Messages and stage messages
- Switching stage screens on or off
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

### With Obtainium (recommended)

[Obtainium](https://obtainium.imranr.dev/) installs apps straight from their GitHub Releases and
checks them for new versions, so you do not have to watch this page.

1. Install Obtainium on your Android device. It is not on Google Play: get it from the Obtainium
   site linked above. Android asks you to allow installs from the app you download it with.
2. In Obtainium, choose **Add App** and enter this repository's address:
   `https://github.com/greenfodor/ProPresenterRemoteCommunityEdition`
3. Add it, then install the app from Obtainium. Android asks you to allow Obtainium to install
   apps: allow it.

Obtainium can tell you when a new release is out; allow its notifications if you want that.

### By hand

1. Open the [latest release](https://github.com/greenfodor/ProPresenterRemoteCommunityEdition/releases/latest)
   on your Android device and download the `.apk` file.
2. Open the downloaded file. Android asks you to allow installs from the app you downloaded it
   with (your browser or file manager): allow it, then install.

### First start

1. Open the app. If Android asks whether the app may find and connect to devices on your local
   network, allow it: the app cannot reach ProPresenter without it.
2. Tap your ProPresenter computer's card, or open **Enter an address** and type its address and
   port.

A later release installs over the one you have and keeps your settings, whichever way you
installed it. A build you make yourself is signed with another key and does not install over a
release.

## Build

You need JDK 17 or later to run Gradle (it downloads the Java 25 toolchain it compiles with) and
the Android SDK.

```bash
./gradlew build              # compile, static analysis and all unit tests
./gradlew :app:installDebug  # install the debug build, which sits next to a release build
./gradlew assembleRelease    # the minified release build
```

`assembleRelease` signs the APK only when `local.properties` names a signing properties file; see
`CLAUDE.md`. Changes are listed in [CHANGELOG.md](CHANGELOG.md).

## Licence

Apache License 2.0. See [LICENSE](LICENSE).
