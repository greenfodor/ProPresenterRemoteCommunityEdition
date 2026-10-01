package com.greenfodor.ppremotece.feature.clear

import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.model.ClearGroup
import com.greenfodor.ppremotece.core.domain.model.ClearGroupIcon
import com.greenfodor.ppremotece.core.domain.model.OutputLayer

/** The layers that have content, the clear groups shown as pills, and the icons read for the groups by uuid. */
data class ClearState(
    val activeLayers: Set<OutputLayer> = emptySet(),
    val groups: List<ClearGroup> = emptyList(),
    val icons: Map<String, ClearGroupIcon> = emptyMap()
)

sealed interface ClearAction {
    data object OnSheetOpen : ClearAction

    data class OnLayerClick(
        val layer: OutputLayer
    ) : ClearAction

    data class OnGroupClick(
        val uuid: String
    ) : ClearAction
}

sealed interface ClearEvent {
    data class ShowError(
        val message: UiText
    ) : ClearEvent
}
