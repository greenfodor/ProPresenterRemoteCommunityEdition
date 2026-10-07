package com.greenfodor.ppremotece.feature.audio

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.core.domain.audio.NowPlaying
import com.greenfodor.ppremotece.core.domain.audio.TransportButton
import com.greenfodor.ppremotece.core.domain.live.Loadable

private val PreviewPlaylists = Loadable.Loaded(
    listOf(
        AudioPickerRowUi.Playlist("p-0", "Audio Playlist 01", depth = 0),
        AudioPickerRowUi.Playlist("p-1", "Audio Playlist 02", depth = 0),
        AudioPickerRowUi.Heading("f-0", "Folder A", depth = 0),
        AudioPickerRowUi.Playlist("p-2", "Audio Playlist 03", depth = 1),
        AudioPickerRowUi.Heading("f-1", "Folder B", depth = 1),
        AudioPickerRowUi.Playlist("p-3", "Audio Playlist 04", depth = 2)
    )
)

private fun tracks(mark: TrackMark) =
    listOf(
        AudioTrackUi("t-0", "Track 01", 0, "Artist 01", "3:20", TrackMark.NONE),
        AudioTrackUi("t-1", "Track 02", 1, "Artist 02", "3:48", mark),
        AudioTrackUi(
            "t-2",
            "Track 03 with a name long enough to be cut short on one line",
            2,
            "Artist 03",
            "1:02:05",
            TrackMark.NONE
        )
    )

private fun bar(button: TransportButton) =
    NowPlaying(
        available = true,
        loaded = true,
        name = "Media 04",
        button = button,
        playPauseEnabled = true,
        skipEnabled = true,
        progress = 0.3f,
        readout = "0:41 / 2:18"
    )

@Preview(widthDp = 527, heightDp = 600)
@Composable
private fun AudioPlayingPreview() {
    PPRemoteTheme {
        AudioScreen(
            state = AudioState(PreviewPlaylists, "p-0", tracks(TrackMark.PLAYING), bar = bar(TransportButton.PAUSE)),
            onAction = {}
        )
    }
}

@Preview(widthDp = 527, heightDp = 600)
@Composable
private fun AudioPausedReconnectingPreview() {
    PPRemoteTheme {
        AudioScreen(
            state = AudioState(
                playlists = PreviewPlaylists,
                selectedUuid = "p-0",
                tracks = tracks(TrackMark.PAUSED),
                bar = bar(TransportButton.PLAY),
                dimmed = true
            ),
            onAction = {},
            reconnecting = true
        )
    }
}

@Preview(widthDp = 527, heightDp = 600)
@Composable
private fun AudioNothingPlayingPreview() {
    PPRemoteTheme {
        AudioScreen(state = AudioState(PreviewPlaylists, "p-1", tracks(TrackMark.NONE)), onAction = {})
    }
}

@Preview(widthDp = 527, heightDp = 600)
@Composable
private fun AudioBarUnavailablePreview() {
    PPRemoteTheme {
        AudioScreen(
            state = AudioState(
                PreviewPlaylists,
                "p-0",
                tracks(TrackMark.NONE),
                bar = NowPlaying.Unavailable
            ),
            onAction = {
            }
        )
    }
}

@Preview(widthDp = 527, heightDp = 400)
@Composable
private fun AudioNoPlaylistsPreview() {
    PPRemoteTheme {
        AudioScreen(state = AudioState(Loadable.Loaded(emptyList())), onAction = {})
    }
}

@Preview(widthDp = 527, heightDp = 400)
@Composable
private fun AudioNoTracksPreview() {
    PPRemoteTheme {
        AudioScreen(state = AudioState(PreviewPlaylists, "p-2"), onAction = {})
    }
}

@Preview(widthDp = 527, heightDp = 600)
@Composable
private fun AudioPickerOpenPreview() {
    PPRemoteTheme {
        AudioScreen(
            state = AudioState(PreviewPlaylists, "p-0", tracks(TrackMark.NONE)),
            onAction = {},
            pickerOpen = true
        )
    }
}

@Preview(widthDp = 360)
@Composable
private fun TrackRowLongNamePreview() {
    PPRemoteTheme {
        TrackRow(track = tracks(TrackMark.PLAYING)[2].copy(mark = TrackMark.PLAYING), onClick = {})
    }
}
