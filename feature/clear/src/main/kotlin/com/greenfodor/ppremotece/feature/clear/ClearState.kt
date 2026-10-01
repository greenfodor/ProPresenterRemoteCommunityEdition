package com.greenfodor.ppremotece.feature.clear

import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.model.ClearGroup
import com.greenfodor.ppremotece.core.domain.model.ClearGroupIcon
import com.greenfodor.ppremotece.core.domain.model.OutputLayer

/**
 * The layers that have content; ProPresenter's Clear All group behind the big Clear All button,
 * set only when it is the only clear group (null hides the button); the clear groups shown as pills
 * (none when the big button is shown); and
 * the icons read for the groups by uuid; and whether Clear All is armed.
 */
data class ClearState(
    val activeLayers: Set<OutputLayer> = emptySet(),
    val clearAll: ClearGroup? = null,
    val groups: List<ClearGroup> = emptyList(),
    val icons: Map<String, ClearGroupIcon> = emptyMap(),
    val armed: Boolean = false
)

sealed interface ClearAction {
    data object OnSheetOpen : ClearAction

    data object OnSheetDismiss : ClearAction

    data class OnLayerClick(
        val layer: OutputLayer
    ) : ClearAction

    data class OnGroupClick(
        val uuid: String
    ) : ClearAction

    data object OnClearAllClick : ClearAction
}

sealed interface ClearEvent {
    data class ShowError(
        val message: UiText
    ) : ClearEvent
}
