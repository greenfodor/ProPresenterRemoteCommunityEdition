package com.greenfodor.ppremotece.feature.remote

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.core.designsystem.ui.SyntheticThumbnails
import com.greenfodor.ppremotece.core.domain.model.Arrangement
import com.greenfodor.ppremotece.core.domain.model.ConnectionStatus
import com.greenfodor.ppremotece.core.domain.model.Group
import com.greenfodor.ppremotece.core.domain.model.GroupColor
import com.greenfodor.ppremotece.core.domain.model.LiveCue
import com.greenfodor.ppremotece.core.domain.model.LiveSlide
import com.greenfodor.ppremotece.core.domain.model.LiveState
import com.greenfodor.ppremotece.core.domain.model.Playlist
import com.greenfodor.ppremotece.core.domain.model.PlaylistItem
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemType
import com.greenfodor.ppremotece.core.domain.model.Presentation
import com.greenfodor.ppremotece.core.domain.model.PresentationRef
import com.greenfodor.ppremotece.core.domain.model.Slide
import com.greenfodor.ppremotece.core.domain.model.SlideSize
import com.greenfodor.ppremotece.core.domain.model.SlideText
import com.greenfodor.ppremotece.core.domain.remote.RemoteBox
import com.greenfodor.ppremotece.core.domain.remote.RemoteDisplay
import com.greenfodor.ppremotece.core.domain.remote.RemoteInputs
import com.greenfodor.ppremotece.core.domain.remote.RemoteStatus
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailRequest

private const val PLAYLIST = "pl"
private const val SONG = "song-a"
private val size = SlideSize(1920, 858)
private val chorusColor = GroupColor(red = 0.8f, green = 0f, blue = 0.3f, alpha = 1f)

private val song = Presentation(
    uuid = SONG,
    name = "Song A",
    groups = listOf(
        Group("v", "Verse 1", null, List(3) { Slide("Verse 1 · ${it + 1}", size = size) }),
        Group("c", "Chorus", chorusColor, List(2) { Slide("Chorus · ${it + 1}", size = size) })
    ),
    arrangements = listOf(Arrangement("full", "Full", listOf("v", "c"), totalCues = 5))
)

private val keys = List(3) { PlaylistItemKey(PLAYLIST, it) }

private val playlist = Playlist(
    uuid = PLAYLIST,
    name = "Arrangement Test",
    items = listOf(
        PlaylistItem(keys[0], "Song A", PlaylistItemType.PRESENTATION, PresentationRef(SONG, "full", "Full")),
        PlaylistItem(keys[1], "Loop", PlaylistItemType.MEDIA, null),
        PlaylistItem(keys[2], "Song A", PlaylistItemType.PRESENTATION, PresentationRef(SONG, "full", "Full"))
    )
)

private val connected = LiveState(ConnectionStatus.CONNECTED, item = null, slide = null)

private fun live(item: Int, cue: Int) = connected.copy(item = keys[item], slide = LiveSlide(SONG, cue, 5))

private fun state(inputs: RemoteInputs): RemoteState {
    val display = RemoteDisplay.reduce(inputs, playlist, mapOf(SONG to song))

    fun thumbnail(box: RemoteBox) =
        (box as? RemoteBox.Slide)?.let {
            ThumbnailRequest(
                "http://192.0.2.14:60113/v1/playlist/pl/0/thumbnail/${it.cue.index}?quality=400",
                "k${it.cue.index}"
            )
        }
    return RemoteState(display, thumbnail(display.current), thumbnail(display.next))
}

@Composable
private fun Preview(state: RemoteState, sideBySide: Boolean = false, reconnecting: Boolean = false) {
    PPRemoteTheme {
        SyntheticThumbnails {
            RemoteScreen(state = state, onAction = {}, sideBySide = sideBySide, reconnecting = reconnecting)
        }
    }
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun LivePreview() {
    Preview(state(RemoteInputs(live(0, 1), LiveCue(keys[0], SONG, 1))))
}

@Preview(widthDp = 1173, heightDp = 527)
@Composable
private fun LiveSideBySidePreview() {
    Preview(state(RemoteInputs(live(0, 1), LiveCue(keys[0], SONG, 1))), sideBySide = true)
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun LastCueReconnectingPreview() {
    Preview(state(RemoteInputs(live(2, 4), LiveCue(keys[2], SONG, 4))), reconnecting = true)
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun CuedPreview() {
    Preview(state(RemoteInputs(live(0, 1), LiveCue(keys[0], SONG, 1), cued = keys[2])))
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun MediaLivePreview() {
    Preview(state(RemoteInputs(connected, LiveCue(keys[0], SONG, 1), mediaLive = keys[1])))
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun TextOnlyPreview() {
    val outside = connected.copy(slide = LiveSlide(SONG, 1, 26), slideText = SlideText("Text 03", "Text 04"))
    Preview(state(RemoteInputs(outside, lastLive = null)))
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun NothingLivePreview() {
    Preview(RemoteState(RemoteDisplay(status = RemoteStatus.NOTHING_LIVE)))
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun LoadingPreview() {
    Preview(RemoteState())
}
