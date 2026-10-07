package com.greenfodor.ppremotece.feature.remote

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.core.designsystem.ui.SyntheticThumbnails
import com.greenfodor.ppremotece.core.domain.model.Arrangement
import com.greenfodor.ppremotece.core.domain.model.ConnectionStatus
import com.greenfodor.ppremotece.core.domain.model.CueSource
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
internal const val PREVIEW_SONG = "song-a"
private val size = SlideSize(1920, 858)
private val chorusColor = GroupColor(red = 0.8f, green = 0f, blue = 0.3f, alpha = 1f)

private val song = Presentation(
    uuid = PREVIEW_SONG,
    name = "Song A",
    groups = listOf(
        Group("v", "Verse 1", null, List(3) { Slide("Verse 1 · ${it + 1}", size = size) }),
        Group("c", "Chorus", chorusColor, List(2) { Slide("Chorus · ${it + 1}", size = size) })
    ),
    arrangements = listOf(Arrangement("full", "Full", listOf("v", "c"), totalCues = 5))
)

internal val previewKeys = List(3) { PlaylistItemKey(PLAYLIST, it) }

private val playlist = Playlist(
    uuid = PLAYLIST,
    name = "Arrangement Test",
    items = listOf(
        PlaylistItem(
            previewKeys[0],
            "Song A",
            PlaylistItemType.PRESENTATION,
            PresentationRef(PREVIEW_SONG, "full", "Full")
        ),
        PlaylistItem(previewKeys[1], "Loop", PlaylistItemType.MEDIA, null),
        PlaylistItem(
            previewKeys[2],
            "Song A",
            PlaylistItemType.PRESENTATION,
            PresentationRef(PREVIEW_SONG, "full", "Full")
        )
    )
)

private val connected = LiveState(ConnectionStatus.CONNECTED, item = null, slide = null)

/** A cleared output: nothing live. */
internal val previewCleared = connected

internal fun previewLive(item: Int, cue: Int) = connected.copy(
    item = previewKeys[item],
    slide = LiveSlide(PREVIEW_SONG, cue, 5)
)

internal fun previewState(inputs: RemoteInputs): RemoteState {
    val display = RemoteDisplay.reduce(inputs, playlist, mapOf(PREVIEW_SONG to song))

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
internal fun RemotePreview(state: RemoteState, expanded: Boolean = false, reconnecting: Boolean = false) {
    PPRemoteTheme {
        SyntheticThumbnails {
            RemoteScreen(state = state, onAction = {}, expanded = expanded, reconnecting = reconnecting)
        }
    }
}

@Preview(widthDp = 527, heightDp = 1173)
@Composable
private fun LivePreview() {
    RemotePreview(
        previewState(RemoteInputs(previewLive(0, 1), LiveCue(CueSource.PlaylistItem(previewKeys[0]), PREVIEW_SONG, 1)))
    )
}

@Preview(widthDp = 527, heightDp = 1173)
@Composable
private fun ClearedPreview() {
    RemotePreview(
        previewState(RemoteInputs(previewCleared, LiveCue(CueSource.PlaylistItem(previewKeys[0]), PREVIEW_SONG, 1)))
    )
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun LastCueReconnectingPreview() {
    RemotePreview(
        previewState(RemoteInputs(previewLive(2, 4), LiveCue(CueSource.PlaylistItem(previewKeys[2]), PREVIEW_SONG, 4))),
        reconnecting = true
    )
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun CuedPreview() {
    RemotePreview(
        previewState(
            RemoteInputs(
                previewLive(0, 1),
                LiveCue(CueSource.PlaylistItem(previewKeys[0]), PREVIEW_SONG, 1),
                cued = previewKeys[2]
            )
        )
    )
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun MediaLivePreview() {
    RemotePreview(
        previewState(
            RemoteInputs(
                connected,
                LiveCue(CueSource.PlaylistItem(previewKeys[0]), PREVIEW_SONG, 1),
                mediaLive = previewKeys[1]
            )
        )
    )
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun TextOnlyPreview() {
    val outside = connected.copy(slide = LiveSlide(PREVIEW_SONG, 1, 26), slideText = SlideText("Text 03", "Text 04"))
    RemotePreview(previewState(RemoteInputs(outside, lastLive = null)))
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun NothingLivePreview() {
    RemotePreview(RemoteState(RemoteDisplay(status = RemoteStatus.NOTHING_LIVE)))
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun LoadingPreview() {
    RemotePreview(RemoteState())
}
