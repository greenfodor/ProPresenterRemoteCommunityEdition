package com.greenfodor.ppremotece.feature.timers

import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.timers.TimerCard

/** The timers, not loaded until the stream's first `timers` frame, and whether their readouts are [dimmed]. */
data class TimersState(
    val timers: Loadable<List<TimerUi>> = Loadable.NotLoaded,
    val dimmed: Boolean = false
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
