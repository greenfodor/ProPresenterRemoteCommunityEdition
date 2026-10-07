package com.greenfodor.ppremotece.core.domain.audio

import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.live.orNull
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
 * 0…1, the middle [button] and which buttons are enabled. Nothing is [loaded] for [Nothing], and
 * the bar is not [available] for [Unavailable].
 */
data class NowPlaying(
    val available: Boolean,
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
            available = true,
            loaded = false,
            name = "",
            button = TransportButton.PLAY,
            playPauseEnabled = false,
            skipEnabled = false,
            progress = 0f,
            readout = ""
        )

        /** The bar when ProPresenter does not report the audio transport: every button disabled. */
        val Unavailable = Nothing.copy(available = false)
    }
}

/**
 * The now-playing bar for the audio [transport] at [positionSeconds]. An unavailable transport
 * gives [NowPlaying.Unavailable]; with nothing loaded (no transport yet or an empty uuid) it is
 * [NowPlaying.Nothing]. Otherwise the button pauses while the audio plays and plays while it is
 * paused, and previous and next are enabled only while an audio playlist track is [active].
 */
fun nowPlaying(transport: Loadable<Transport>, positionSeconds: Double?, active: ActiveAudio?): NowPlaying {
    val loaded = transport.orNull()?.takeIf { it.uuid.isNotEmpty() }
    return when {
        transport == Loadable.Unavailable -> NowPlaying.Unavailable
        loaded == null -> NowPlaying.Nothing
        else -> loadedBar(loaded, positionSeconds ?: 0.0, active)
    }
}

private fun loadedBar(loaded: Transport, position: Double, active: ActiveAudio?): NowPlaying {
    val duration = loaded.durationSeconds
    return NowPlaying(
        available = true,
        loaded = true,
        name = loaded.name,
        button = if (loaded.isPlaying) TransportButton.PAUSE else TransportButton.PLAY,
        playPauseEnabled = true,
        skipEnabled = active != null,
        progress = if (duration > 0.0) (position / duration).coerceIn(0.0, 1.0).toFloat() else 0f,
        readout = "${formatDuration(position.toInt())} / ${formatDuration(duration.toInt())}"
    )
}
