package com.greenfodor.ppremotece.core.domain.status

import com.greenfodor.ppremotece.core.domain.model.CountDownTarget
import com.greenfodor.ppremotece.core.domain.model.GroupColor
import com.greenfodor.ppremotece.core.domain.model.Look
import com.greenfodor.ppremotece.core.domain.model.Macro
import com.greenfodor.ppremotece.core.domain.model.MacroCollection
import com.greenfodor.ppremotece.core.domain.model.Timer
import com.greenfodor.ppremotece.core.domain.model.TimerReading
import com.greenfodor.ppremotece.core.domain.model.TimerState
import com.greenfodor.ppremotece.core.domain.model.TimerType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull

private val COLOR_CHANNELS = listOf("red", "green", "blue", "alpha")
private val TIMER_TYPES = mapOf(
    "countdown" to TimerType.COUNTDOWN,
    "count_down_to_time" to TimerType.COUNTDOWN_TO_TIME,
    "elapsed" to TimerType.ELAPSED
)

internal fun JsonElement?.toTimer(): Timer? {
    val id = child("id")
    val uuid = id.child("uuid").stringOrNull() ?: return null
    val type = TIMER_TYPES.entries.firstOrNull { (key, _) -> child(key) != null }?.value ?: TimerType.UNKNOWN
    return Timer(
        uuid = uuid,
        name = id.child("name").stringOrNull().orEmpty(),
        index = id.child("index").intOrNull() ?: 0,
        type = type,
        allowsOverrun = (child("allows_overrun") as? JsonPrimitive)?.booleanOrNull ?: false,
        target = child("count_down_to_time").toCountDownTarget()
    )
}

private fun JsonElement?.toCountDownTarget(): CountDownTarget? {
    val seconds = child("time_of_day").intOrNull() ?: return null
    return CountDownTarget(timeOfDaySeconds = seconds, period = child("period").stringOrNull().orEmpty())
}

internal fun JsonElement?.toLook(): Look? {
    val id = child("id")
    val uuid = id.child("uuid").stringOrNull() ?: return null
    return Look(
        uuid = uuid,
        name = id.child("name").stringOrNull().orEmpty(),
        index =
            id.child("index").intOrNull() ?: 0
    )
}

internal fun JsonElement?.toTimerReading(): TimerReading? {
    val uuid = child("id").child("uuid").stringOrNull() ?: return null
    return TimerReading(
        uuid = uuid,
        time = child("time").stringOrNull().orEmpty(),
        state = TimerState.fromApiName(child("state").stringOrNull())
    )
}

internal fun JsonElement?.toMacroCollection(): MacroCollection? {
    val id = child("id")
    return id.child("uuid").stringOrNull()?.let { uuid ->
        MacroCollection(
            uuid = uuid,
            name = id.child("name").stringOrNull().orEmpty(),
            index = id.child("index").intOrNull() ?: 0,
            macros = (child("macros") as? JsonArray).orEmpty().mapNotNull { it.toMacro() }
        )
    }
}

private fun JsonElement?.toMacro(): Macro? {
    val id = child("id")
    return id.child("uuid").stringOrNull()?.let { uuid ->
        Macro(
            uuid = uuid,
            name = id.child("name").stringOrNull().orEmpty(),
            index = id.child("index").intOrNull() ?: 0,
            color = child("color").toColor(),
            imageType = child("image_type").stringOrNull()
        )
    }
}

private fun JsonElement?.toColor(): GroupColor? =
    COLOR_CHANNELS.map { (child(it) as? JsonPrimitive)?.floatOrNull }
        .filterNotNull()
        .takeIf { it.size == COLOR_CHANNELS.size }
        ?.let { (red, green, blue, alpha) -> GroupColor(red = red, green = green, blue = blue, alpha = alpha) }
