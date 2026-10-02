package com.greenfodor.ppremotece.feature.macros

import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.model.GroupColor
import com.greenfodor.ppremotece.core.domain.model.ServerIcon

/** The macro collections as sections; [showHeaders] is false when there is a single collection. */
data class MacrosState(
    val sections: List<MacroSectionUi> = emptyList(),
    val showHeaders: Boolean = false
)

data class MacroSectionUi(
    val uuid: String,
    val name: String,
    val macros: List<MacroUi>
)

/** A macro tile; [confirmed] while its check shows after a successful trigger. */
data class MacroUi(
    val uuid: String,
    val name: String,
    val color: GroupColor?,
    val icon: ServerIcon?,
    val confirmed: Boolean
)

sealed interface MacrosAction {
    data class OnMacroClick(
        val uuid: String
    ) : MacrosAction
}

sealed interface MacrosEvent {
    data class ShowError(
        val message: UiText
    ) : MacrosEvent
}
