package com.greenfodor.ppremotece.feature.remote

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greenfodor.ppremotece.core.domain.model.CueSource
import com.greenfodor.ppremotece.core.domain.model.LiveCue
import com.greenfodor.ppremotece.core.domain.remote.RemoteInputs

@Preview(widthDp = 1173, heightDp = 527)
@Composable
private fun LiveExpandedPreview() {
    RemotePreview(
        previewState(RemoteInputs(previewLive(0, 1), LiveCue(CueSource.PlaylistItem(previewKeys[0]), PREVIEW_SONG, 1))),
        expanded = true
    )
}

@Preview(widthDp = 1173, heightDp = 527)
@Composable
private fun ClearedExpandedPreview() {
    RemotePreview(
        previewState(RemoteInputs(previewCleared, LiveCue(CueSource.PlaylistItem(previewKeys[0]), PREVIEW_SONG, 1))),
        expanded = true
    )
}

@Preview(widthDp = 1173, heightDp = 527)
@Composable
private fun CuedExpandedPreview() {
    RemotePreview(
        previewState(
            RemoteInputs(
                previewLive(0, 1),
                LiveCue(CueSource.PlaylistItem(previewKeys[0]), PREVIEW_SONG, 1),
                cued = previewKeys[2]
            )
        ),
        expanded = true
    )
}

@Preview(widthDp = 1173, heightDp = 527)
@Composable
private fun EndOfPlaylistExpandedPreview() {
    RemotePreview(
        previewState(RemoteInputs(previewLive(2, 4), LiveCue(CueSource.PlaylistItem(previewKeys[2]), PREVIEW_SONG, 4))),
        expanded = true
    )
}
