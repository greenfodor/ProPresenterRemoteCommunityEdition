package com.greenfodor.ppremotece.core.domain.timers

import com.greenfodor.ppremotece.core.domain.model.CountDownTarget
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
 * reading), whether it is [running] and [overrun], the operation of its Start/Stop [toggle], and
 * the [target] label shown as "Counts down to" in place of the readout, when there is one.
 */
data class TimerCard(
    val icon: TimerIcon,
    val readout: String,
    val running: Boolean,
    val overrun: Boolean,
    val toggle: TimerOperation,
    val target: String? = null
)

/**
 * [target] as a clock time: `h:mm AM`/`h:mm PM` for the `am` and `pm` periods, `HH:mm` for
 * `is_24_hour`, `24_hour` and any other period.
 */
fun targetLabel(target: CountDownTarget): String {
    val hours = target.timeOfDaySeconds / SECONDS_PER_HOUR % HOURS_PER_DAY
    val minutes = (target.timeOfDaySeconds / SECONDS_PER_MINUTE % MINUTES_PER_HOUR).toString().padStart(2, '0')
    return when (target.period) {
        "am", "pm" -> "${(hours % HOURS_PER_HALF_DAY).takeIf {
            it != 0
        } ?: HOURS_PER_HALF_DAY}:$minutes ${target.period.uppercase()}"
        else -> "${hours.toString().padStart(2, '0')}:$minutes"
    }
}

private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60
private const val SECONDS_PER_HOUR = 3600
private const val HOURS_PER_DAY = 24
private const val HOURS_PER_HALF_DAY = 12

/**
 * The card of [timer] with [reading]: running while RUNNING or OVERRUNNING; overrun while
 * OVERRUNNING or OVERRAN, or when the time is negative; the toggle stops a running timer and starts
 * any other. A STOPPED count-down-to-time timer whose time is not negative shows its configured
 * target ([targetLabel]); an unknown type shows the `timer` icon.
 */
fun timerCard(timer: Timer, reading: TimerReading?): TimerCard {
    val state = reading?.state
    val running = state == TimerState.RUNNING || state == TimerState.OVERRUNNING
    val negative = reading?.time?.startsWith("-") == true
    return TimerCard(
        icon = when (timer.type) {
            TimerType.COUNTDOWN -> TimerIcon.TIMER
            TimerType.COUNTDOWN_TO_TIME -> TimerIcon.ALARM
            TimerType.ELAPSED -> TimerIcon.AVG_PACE
            TimerType.UNKNOWN -> TimerIcon.TIMER
        },
        readout = reading?.time.orEmpty(),
        running = running,
        overrun = state == TimerState.OVERRUNNING ||
            state == TimerState.OVERRAN ||
            negative,
        toggle = if (running) TimerOperation.STOP else TimerOperation.START,
        target = timer.target
            ?.takeIf { timer.type == TimerType.COUNTDOWN_TO_TIME && state == TimerState.STOPPED && !negative }
            ?.let(::targetLabel)
    )
}
