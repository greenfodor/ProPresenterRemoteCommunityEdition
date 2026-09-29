package com.greenfodor.ppremotece.feature.clear

import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.model.ClearGroup
import com.greenfodor.ppremotece.core.domain.model.OutputLayer

/** The layers that have content, the clear groups read when the sheet opened, and whether Clear All is armed. */
data class ClearState(
    val activeLayers: Set<OutputLayer> = emptySet(),
    val groups: List<ClearGroup> = emptyList(),
    val armed: Boolean = false
)

sealed interface ClearAction {
    data object OnSheetOpen : ClearAction

    data class OnLayerClick(
        val layer: OutputLayer
    ) : ClearAction

    data class OnGroupClick(
        val uuid: String
    ) : ClearAction

    data object OnClearAllClick : ClearAction
}

sealed interface ClearEvent {
    data class LayersFailed(
        val count: Int
    ) : ClearEvent

    data class ShowError(
        val message: UiText
    ) : ClearEvent
}
