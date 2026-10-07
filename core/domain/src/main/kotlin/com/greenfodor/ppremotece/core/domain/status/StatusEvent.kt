package com.greenfodor.ppremotece.core.domain.status

import com.greenfodor.ppremotece.core.domain.model.ActiveAudio
import com.greenfodor.ppremotece.core.domain.model.AudioNode
import com.greenfodor.ppremotece.core.domain.model.LiveSlide
import com.greenfodor.ppremotece.core.domain.model.Look
import com.greenfodor.ppremotece.core.domain.model.MacroCollection
import com.greenfodor.ppremotece.core.domain.model.OutputLayer
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.PropCollection
import com.greenfodor.ppremotece.core.domain.model.SlideText
import com.greenfodor.ppremotece.core.domain.model.Timer
import com.greenfodor.ppremotece.core.domain.model.TimerReading
import com.greenfodor.ppremotece.core.domain.model.Transport

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

    /** An error frame's messages, as sent, each naming a url the server rejected: `URL: {url}. Error: …`. */
    data class Rejected(
        val messages: List<String>
    ) : StatusEvent

    /** The looks, from a `looks` frame. */
    data class Looks(
        val looks: List<Look>
    ) : StatusEvent

    /** The live look, from a `look/current` frame. */
    data class CurrentLook(
        val look: Look
    ) : StatusEvent

    /** The prop collections, from a `prop_collections` frame. */
    data class PropCollections(
        val collections: List<PropCollection>
    ) : StatusEvent

    /** What the presentation layer's transport has loaded, from a `transport/presentation/current` frame. */
    data class PresentationTransport(
        val transport: Transport
    ) : StatusEvent

    /** What the audio layer's transport has loaded, from a `transport/audio/current` frame. */
    data class AudioTransport(
        val transport: Transport
    ) : StatusEvent

    /** How far into the loaded audio the audio layer is, in seconds, from a `transport/audio/time` frame. */
    data class AudioTime(
        val seconds: Double
    ) : StatusEvent

    /** The audio bin's tree of folders and playlists, from an `audio/playlists` frame. */
    data class AudioPlaylists(
        val nodes: List<AudioNode>
    ) : StatusEvent

    /** The audio playlist track that plays, null without one, from an `audio/playlist/active` frame. */
    data class ActiveAudioChanged(
        val active: ActiveAudio?
    ) : StatusEvent

    data class Heartbeat(
        val epochSeconds: Long
    ) : StatusEvent

    data class Unknown(
        val url: String?
    ) : StatusEvent
}
