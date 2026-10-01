package com.greenfodor.ppremotece.feature.remote

import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.model.Cue
import com.greenfodor.ppremotece.core.domain.model.CueSource
import com.greenfodor.ppremotece.core.domain.remote.BoxMark
import com.greenfodor.ppremotece.core.domain.remote.RemoteDisplay
import com.greenfodor.ppremotece.core.domain.remote.RemoteStatus
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailRequest

/**
 * The Remote tab's [display], the thumbnail requests of its current and next boxes, a read
 * [error], and the cue [sidebar] of [sidebarSource] with the position of its live or cued cue in
 * [sidebarFocus].
 */
data class RemoteState(
    val display: RemoteDisplay = RemoteDisplay(status = RemoteStatus.LOADING),
    val currentThumbnail: ThumbnailRequest? = null,
    val nextThumbnail: ThumbnailRequest? = null,
    val error: UiText? = null,
    val sidebar: List<SidebarCueUi> = emptyList(),
    val sidebarFocus: Int? = null,
    val sidebarSource: CueSource? = null
)

/** A cue of the cue sidebar with its mark and thumbnail request. */
data class SidebarCueUi(
    val cue: Cue,
    val mark: BoxMark,
    val thumbnail: ThumbnailRequest?
)

sealed interface RemoteAction {
    data object OnCurrentClick : RemoteAction

    data object OnNextBoxClick : RemoteAction

    data object OnNextClick : RemoteAction

    data object OnPreviousClick : RemoteAction

    data object OnPreviousItemClick : RemoteAction

    data object OnNextItemClick : RemoteAction

    data object OnBackToLiveClick : RemoteAction

    data object OnRetryClick : RemoteAction

    data class OnSidebarCueClick(
        val index: Int
    ) : RemoteAction

    data class OnCurrentBoxSized(
        val px: Int
    ) : RemoteAction

    data class OnNextBoxSized(
        val px: Int
    ) : RemoteAction
}

sealed interface RemoteEvent {
    data class ShowError(
        val message: UiText
    ) : RemoteEvent
}
