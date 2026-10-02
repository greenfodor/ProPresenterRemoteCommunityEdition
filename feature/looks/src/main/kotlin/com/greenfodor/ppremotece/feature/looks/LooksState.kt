package com.greenfodor.ppremotece.feature.looks

import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.live.Loadable

/** The looks, not loaded until the stream's first `looks` frame. */
data class LooksState(
    val looks: Loadable<List<LookUi>> = Loadable.NotLoaded
)

/** A look card; [live] for the look whose index and name match the live look. */
data class LookUi(
    val uuid: String,
    val name: String,
    val live: Boolean
)

sealed interface LooksAction {
    data class OnLookClick(
        val uuid: String
    ) : LooksAction
}

sealed interface LooksEvent {
    data class ShowError(
        val message: UiText
    ) : LooksEvent
}
