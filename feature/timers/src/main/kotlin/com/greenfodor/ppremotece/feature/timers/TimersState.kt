package com.greenfodor.ppremotece.feature.timers

import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.timers.TimerCard

data class TimersState(
    val timers: List<TimerUi> = emptyList()
)

data class TimerUi(
    val uuid: String,
    val name: String,
    val card: TimerCard
)

sealed interface TimersAction {
    data class OnToggleClick(
        val uuid: String
    ) : TimersAction

    data class OnResetClick(
        val uuid: String
    ) : TimersAction
}

sealed interface TimersEvent {
    data class ShowError(
        val message: UiText
    ) : TimersEvent
}
