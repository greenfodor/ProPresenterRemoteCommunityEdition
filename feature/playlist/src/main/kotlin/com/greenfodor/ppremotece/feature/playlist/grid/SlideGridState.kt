package com.greenfodor.ppremotece.feature.playlist.grid

import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.model.GroupColor
import com.greenfodor.ppremotece.core.domain.model.LiveState
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.feature.playlist.ArrangementLabel

data class SlideGridState(
    val title: String = "",
    val label: ArrangementLabel? = null,
    val presentationUuid: String? = null,
    val cues: List<CueUi> = emptyList(),
    val liveCueIndex: Int? = null,
    val isLoading: Boolean = true,
    val error: UiText? = null
)

data class CueUi(
    val index: Int,
    val groupName: String,
    val groupColor: GroupColor?,
    val text: String
)

sealed interface SlideGridAction {
    data class OnCueClick(
        val index: Int
    ) : SlideGridAction

    data object OnNextClick : SlideGridAction

    data object OnPreviousClick : SlideGridAction

    data object OnRetryClick : SlideGridAction
}

sealed interface SlideGridEvent {
    data class ShowError(
        val message: UiText
    ) : SlideGridEvent
}

/** The index of the live cue when [live] is showing this playlist item and its presentation, else null. */
fun liveCueIndex(live: LiveState, item: PlaylistItemKey, presentationUuid: String?): Int? =
    live.slide?.takeIf {
        live.item == item && presentationUuid != null && it.presentationUuid == presentationUuid
    }?.index
