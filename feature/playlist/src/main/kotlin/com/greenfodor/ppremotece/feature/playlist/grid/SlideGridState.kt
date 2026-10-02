package com.greenfodor.ppremotece.feature.playlist.grid

import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.arrangement.ArrangementBanner
import com.greenfodor.ppremotece.core.domain.arrangement.GroupSequence
import com.greenfodor.ppremotece.core.domain.arrangement.ResyncTarget
import com.greenfodor.ppremotece.core.domain.layout.GridStep
import com.greenfodor.ppremotece.core.domain.layout.ViewMode
import com.greenfodor.ppremotece.core.domain.layout.WidthClass
import com.greenfodor.ppremotece.core.domain.model.GroupColor
import com.greenfodor.ppremotece.core.domain.thumbnail.DEFAULT_SLIDE_ASPECT
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailRequest
import com.greenfodor.ppremotece.feature.playlist.ArrangementLabel

data class SlideGridState(
    val title: String = "",
    val label: ArrangementLabel? = null,
    val cues: List<CueUi> = emptyList(),
    val aspect: Float = DEFAULT_SLIDE_ASPECT,
    val countMismatch: Boolean = false,
    val banner: ArrangementBanner? = null,
    val resync: ResyncTarget? = null,
    val groupSequence: GroupSequence = GroupSequence(emptyList(), livePill = null),
    val liveCueIndex: Int? = null,
    val nextCueIndex: Int? = null,
    val thumbnailGeneration: Int = 0,
    val stepsEnabled: Boolean = false,
    val gridStep: GridStep? = null,
    val viewMode: ViewMode? = null,
    val isLoading: Boolean = true,
    val error: UiText? = null
)

data class CueUi(
    val index: Int,
    val groupName: String,
    val groupColor: GroupColor?,
    val text: String,
    val label: String,
    val enabled: Boolean,
    val thumbnail: ThumbnailRequest? = null
)

sealed interface SlideGridAction {
    /** The slide size and view mode actions. */
    sealed interface Layout : SlideGridAction

    data class OnCueClick(
        val index: Int
    ) : SlideGridAction

    data object OnNextClick : SlideGridAction

    data class OnFirstVisibleCueChange(
        val cueIndex: Int
    ) : SlideGridAction

    data object OnPreviousClick : SlideGridAction

    data object OnResyncClick : SlideGridAction

    data class OnGroupPillClick(
        val firstCueIndex: Int
    ) : SlideGridAction

    data object OnRetryClick : SlideGridAction

    data object OnReloadClick : SlideGridAction

    data class OnWidthClassChange(
        val widthClass: WidthClass
    ) : Layout

    data class OnGridStepChange(
        val step: GridStep
    ) : Layout

    data object OnGridStepChangeFinished : Layout

    data class OnViewModeChange(
        val mode: ViewMode
    ) : Layout
}

sealed interface SlideGridEvent {
    data class ScrollToCue(
        val cueIndex: Int
    ) : SlideGridEvent

    data class ShowError(
        val message: UiText
    ) : SlideGridEvent
}
