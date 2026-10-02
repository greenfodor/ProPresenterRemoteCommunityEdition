package com.greenfodor.ppremotece.core.domain.timers

import com.greenfodor.ppremotece.core.domain.model.Timer
import com.greenfodor.ppremotece.core.domain.model.TimerOperation
import com.greenfodor.ppremotece.core.domain.model.TimerReading
import com.greenfodor.ppremotece.core.domain.model.TimerState
import com.greenfodor.ppremotece.core.domain.model.TimerType

/** The Material Symbols icon of a timer type. */
enum class TimerIcon {
    TIMER,
    ALARM,
    AVG_PACE
}

/**
 * What a timer card shows: its [icon], the [readout] as the server sent it (empty before the first
 * reading), whether it is [running] and [overrun], and the operation of its Start/Stop [toggle].
 */
data class TimerCard(
    val icon: TimerIcon,
    val readout: String,
    val running: Boolean,
    val overrun: Boolean,
    val toggle: TimerOperation
)

/**
 * The card of [timer] with [reading]: running while RUNNING or OVERRUNNING; overrun while
 * OVERRUNNING or OVERRAN, or when the time is negative; the toggle stops a running timer and starts
 * any other.
 */
fun timerCard(timer: Timer, reading: TimerReading?): TimerCard {
    val state = reading?.state
    val running = state == TimerState.RUNNING || state == TimerState.OVERRUNNING
    return TimerCard(
        icon = when (timer.type) {
            TimerType.COUNTDOWN -> TimerIcon.TIMER
            TimerType.COUNTDOWN_TO_TIME -> TimerIcon.ALARM
            TimerType.ELAPSED -> TimerIcon.AVG_PACE
        },
        readout = reading?.time.orEmpty(),
        running = running,
        overrun = state == TimerState.OVERRUNNING ||
            state == TimerState.OVERRAN ||
            reading?.time?.startsWith("-") == true,
        toggle = if (running) TimerOperation.STOP else TimerOperation.START
    )
}
