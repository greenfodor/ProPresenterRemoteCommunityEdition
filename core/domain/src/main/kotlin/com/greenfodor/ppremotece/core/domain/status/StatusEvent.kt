package com.greenfodor.ppremotece.core.domain.status

import com.greenfodor.ppremotece.core.domain.model.LiveSlide
import com.greenfodor.ppremotece.core.domain.model.MacroCollection
import com.greenfodor.ppremotece.core.domain.model.OutputLayer
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.SlideText
import com.greenfodor.ppremotece.core.domain.model.Timer
import com.greenfodor.ppremotece.core.domain.model.TimerReading

/** A decoded `status/updates` frame. */
sealed interface StatusEvent {
    data class SlideChanged(
        val text: SlideText?
    ) : StatusEvent

    data class SlideIndex(
        val slide: LiveSlide?
    ) : StatusEvent

    data class PresentationActive(
        val presentationUuid: String?
    ) : StatusEvent

    /** The live playlist item and the uuid of the presentation it plays, when the frame names them. */
    data class PlaylistActive(
        val item: PlaylistItemKey?,
        val presentationUuid: String? = null
    ) : StatusEvent

    /** The output layers that have content. */
    data class Layers(
        val active: Set<OutputLayer>
    ) : StatusEvent

    /** The configured timers, from a `timers` frame. */
    data class Timers(
        val timers: List<Timer>
    ) : StatusEvent

    /** Every timer's current reading, from a `timers/current` frame. */
    data class TimerReadings(
        val readings: List<TimerReading>
    ) : StatusEvent

    /** The macro collections, from a `macro_collections` frame. */
    data class MacroCollections(
        val collections: List<MacroCollection>
    ) : StatusEvent

    /** An error frame naming a subscribed url the server rejected, as sent: `URL: {url}. Error: …`. */
    data class Rejected(
        val message: String
    ) : StatusEvent

    data class Heartbeat(
        val epochSeconds: Long
    ) : StatusEvent

    data class Unknown(
        val url: String?
    ) : StatusEvent
}
