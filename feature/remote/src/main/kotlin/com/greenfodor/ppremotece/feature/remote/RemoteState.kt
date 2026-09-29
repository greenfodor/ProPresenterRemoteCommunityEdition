package com.greenfodor.ppremotece.feature.remote

import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.remote.RemoteDisplay
import com.greenfodor.ppremotece.core.domain.remote.RemoteStatus
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailRequest

/** The Remote tab's [display], the thumbnail requests of its current and next boxes, and a read [error]. */
data class RemoteState(
    val display: RemoteDisplay = RemoteDisplay(status = RemoteStatus.LOADING),
    val currentThumbnail: ThumbnailRequest? = null,
    val nextThumbnail: ThumbnailRequest? = null,
    val error: UiText? = null
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
}

sealed interface RemoteEvent {
    data class ShowError(
        val message: UiText
    ) : RemoteEvent
}
