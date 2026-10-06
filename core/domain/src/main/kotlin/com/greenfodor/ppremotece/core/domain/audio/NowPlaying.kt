package com.greenfodor.ppremotece.core.domain.audio

import com.greenfodor.ppremotece.core.domain.model.ActiveAudio
import com.greenfodor.ppremotece.core.domain.model.Transport
import com.greenfodor.ppremotece.core.domain.transport.formatDuration

/** What the now-playing bar's middle button does. */
enum class TransportButton {
    PLAY,
    PAUSE
}

/**
 * The now-playing bar: the loaded audio's [name], its [readout] `m:ss / m:ss` and [progress]
 * 0…1, the middle [button] and which buttons are enabled. Nothing is [loaded] for [Nothing].
 */
data class NowPlaying(
    val loaded: Boolean,
    val name: String,
    val button: TransportButton,
    val playPauseEnabled: Boolean,
    val skipEnabled: Boolean,
    val progress: Float,
    val readout: String
) {
    companion object {
        /** The bar with nothing loaded: every button disabled. */
        val Nothing = NowPlaying(
            loaded = false,
            name = "",
            button = TransportButton.PLAY,
            playPauseEnabled = false,
            skipEnabled = false,
            progress = 0f,
            readout = ""
        )
    }
}

/**
 * The now-playing bar for the audio [transport] at [positionSeconds]. With nothing loaded (no
 * transport or an empty uuid) it is [NowPlaying.Nothing]. Otherwise the button pauses while the
 * audio plays and plays while it is paused, and previous and next are enabled only while an audio
 * playlist track is [active].
 */
fun nowPlaying(transport: Transport?, positionSeconds: Double?, active: ActiveAudio?): NowPlaying {
    if (transport == null || transport.uuid.isEmpty()) return NowPlaying.Nothing
    val position = positionSeconds ?: 0.0
    val duration = transport.durationSeconds
    return NowPlaying(
        loaded = true,
        name = transport.name,
        button = if (transport.isPlaying) TransportButton.PAUSE else TransportButton.PLAY,
        playPauseEnabled = true,
        skipEnabled = active != null,
        progress = if (duration > 0.0) (position / duration).coerceIn(0.0, 1.0).toFloat() else 0f,
        readout = "${formatDuration(position.toInt())} / ${formatDuration(duration.toInt())}"
    )
}
