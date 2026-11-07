package com.greenfodor.ppremotece.feature.remote

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greenfodor.ppremotece.core.domain.model.CueSource
import com.greenfodor.ppremotece.core.domain.model.LiveCue
import com.greenfodor.ppremotece.core.domain.remote.RemoteInputs

private fun liveState(locked: Boolean) =
    previewState(
        RemoteInputs(previewLive(0, 1), LiveCue(CueSource.PlaylistItem(previewKeys[0]), PREVIEW_SONG, 1))
    ).copy(locked = locked)

@Preview(widthDp = 527, heightDp = 1173)
@Composable
private fun PhoneLockedPreview() {
    RemotePreview(liveState(locked = true))
}

@Preview(widthDp = 527, heightDp = 1173)
@Composable
private fun PhoneUnlockedPreview() {
    RemotePreview(liveState(locked = false))
}

@Preview(widthDp = 1173, heightDp = 527)
@Composable
private fun WideLockedPreview() {
    RemotePreview(liveState(locked = true), expanded = true)
}

@Preview(widthDp = 1173, heightDp = 527)
@Composable
private fun WideUnlockedPreview() {
    RemotePreview(liveState(locked = false), expanded = true)
}
