package com.greenfodor.ppremotece.core.domain.timers

import com.greenfodor.ppremotece.core.domain.model.Timer
import com.greenfodor.ppremotece.core.domain.model.TimerReading
import kotlinx.coroutines.flow.StateFlow

/** A timer with its latest reading, null until one arrives. */
data class LiveTimer(
    val timer: Timer,
    val reading: TimerReading?
)

/** The connected host's timers from its status stream; empty while disconnected. */
interface TimersRepository {
    val timers: StateFlow<List<LiveTimer>>
}

/** Each of [timers], in order and once per uuid, with its reading from [readings] by uuid. */
fun joinTimers(timers: List<Timer>, readings: List<TimerReading>): List<LiveTimer> {
    val byUuid = readings.associateBy { it.uuid }
    return timers.distinctBy { it.uuid }.map { LiveTimer(it, byUuid[it.uuid]) }
}
