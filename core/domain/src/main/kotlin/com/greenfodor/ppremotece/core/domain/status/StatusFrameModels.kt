package com.greenfodor.ppremotece.core.domain.status

import com.greenfodor.ppremotece.core.domain.model.CountDownTarget
import com.greenfodor.ppremotece.core.domain.model.GroupColor
import com.greenfodor.ppremotece.core.domain.model.Look
import com.greenfodor.ppremotece.core.domain.model.Macro
import com.greenfodor.ppremotece.core.domain.model.MacroCollection
import com.greenfodor.ppremotece.core.domain.model.Prop
import com.greenfodor.ppremotece.core.domain.model.PropCollection
import com.greenfodor.ppremotece.core.domain.model.StageLayout
import com.greenfodor.ppremotece.core.domain.model.StageScreen
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

/** A look with its uuid and index; null without either. */
internal fun JsonElement?.toLook(): Look? {
    val id = child("id")
    val uuid = id.child("uuid").stringOrNull()
    val index = id.child("index").intOrNull()
    return if (uuid != null && index != null) {
        Look(uuid = uuid, name = id.child("name").stringOrNull().orEmpty(), index = index)
    } else {
        null
    }
}

internal fun JsonElement?.toPropCollection(): PropCollection? {
    val id = child("id")
    return id.child("uuid").stringOrNull()?.let { uuid ->
        PropCollection(
            uuid = uuid,
            name = id.child("name").stringOrNull().orEmpty(),
            index = id.child("index").intOrNull() ?: 0,
            props = (child("props") as? JsonArray).orEmpty().mapNotNull { it.toProp() }
        )
    }
}

private fun JsonElement?.toProp(): Prop? {
    val id = child("id")
    return id.child("uuid").stringOrNull()?.let { uuid ->
        Prop(
            uuid = uuid,
            name = id.child("name").stringOrNull().orEmpty(),
            index = id.child("index").intOrNull() ?: 0,
            isActive = (child("is_active") as? JsonPrimitive)?.booleanOrNull ?: false,
            transitionName = child("transition").child("name").stringOrNull()
        )
    }
}

/** The event of a `stage/screens`, `stage/layouts` or `stage/layout_map` frame; null for any other [url]. */
internal fun stageEventOf(url: String?, data: JsonElement?): StatusEvent? {
    val list = data as? JsonArray ?: return null
    return when (url) {
        "stage/screens" -> StatusEvent.StageScreens(
            list.mapNotNull { screen ->
                screen.child("uuid").stringOrNull()?.let {
                    StageScreen(it, screen.child("name").stringOrNull().orEmpty())
                }
            }
        )
        "stage/layouts" -> StatusEvent.StageLayouts(
            list.mapNotNull { layout ->
                val id = layout.child("id")
                id.child("uuid").stringOrNull()?.let { StageLayout(it, id.child("name").stringOrNull().orEmpty()) }
            }
        )
        "stage/layout_map" -> StatusEvent.StageLayoutMap(
            list.mapNotNull { entry ->
                val screen = entry.child("screen").child("uuid").stringOrNull()
                val layout = entry.child("layout").child("uuid").stringOrNull()
                if (screen != null && layout != null) screen to layout else null
            }.toMap()
        )
        else -> null
    }
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
