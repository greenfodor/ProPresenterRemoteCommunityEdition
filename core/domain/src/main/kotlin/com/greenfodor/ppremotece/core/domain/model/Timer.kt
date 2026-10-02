package com.greenfodor.ppremotece.core.domain.model

enum class TimerType {
    COUNTDOWN,
    COUNTDOWN_TO_TIME,
    ELAPSED
}

/** A timer configured in ProPresenter. */
data class Timer(
    val uuid: String,
    val name: String,
    val index: Int,
    val type: TimerType,
    val allowsOverrun: Boolean
)

/** A timer's state as `timers/current` names it; `overrun` reads as [OVERRAN], anything unknown as [UNKNOWN]. */
enum class TimerState(
    val apiName: String
) {
    STOPPED("stopped"),
    RUNNING("running"),
    COMPLETE("complete"),
    OVERRUNNING("overrunning"),
    OVERRAN("overran"),
    UNKNOWN("");

    companion object {
        fun fromApiName(name: String?): TimerState =
            if (name == "overrun") OVERRAN else entries.firstOrNull { it.apiName == name && it != UNKNOWN } ?: UNKNOWN
    }
}

/** A timer's current [time], as ProPresenter formats it, and its [state]. */
data class TimerReading(
    val uuid: String,
    val time: String,
    val state: TimerState
)

/** An operation sent as `GET /v1/timer/{uuid}/{apiName}`. */
enum class TimerOperation(
    val apiName: String
) {
    START("start"),
    STOP("stop"),
    RESET("reset")
}
