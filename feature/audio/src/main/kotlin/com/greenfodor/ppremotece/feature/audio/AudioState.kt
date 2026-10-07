package com.greenfodor.ppremotece.feature.audio

import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.audio.NowPlaying
import com.greenfodor.ppremotece.core.domain.live.Loadable

/**
 * The audio bin: its [playlists], not loaded until the stream's first `audio/playlists` frame, the
 * chosen playlist's [tracks], the now-playing [bar], and whether its readout is [dimmed].
 */
data class AudioState(
    val playlists: Loadable<List<AudioPlaylistUi>> = Loadable.NotLoaded,
    val selectedUuid: String? = null,
    val tracks: List<AudioTrackUi> = emptyList(),
    val tracksLoading: Boolean = false,
    val tracksError: UiText? = null,
    val bar: NowPlaying = NowPlaying.Nothing,
    val dimmed: Boolean = false
) {
    /** The chosen playlist's name; null without one. */
    val selectedName: String?
        get() = (playlists as? Loadable.Loaded)?.value?.firstOrNull { it.uuid == selectedUuid }?.name
}

data class AudioPlaylistUi(
    val uuid: String,
    val name: String
)

/** How a track row is marked. */
enum class TrackMark {
    NONE,
    PLAYING,
    PAUSED
}

/** A track row; [duration] is its length as text. */
data class AudioTrackUi(
    val uuid: String,
    val name: String,
    val index: Int,
    val artist: String,
    val duration: String,
    val mark: TrackMark
)

sealed interface AudioAction {
    data class OnPlaylistSelect(
        val uuid: String
    ) : AudioAction

    data class OnTrackClick(
        val trackUuid: String
    ) : AudioAction

    data object OnPlayPauseClick : AudioAction

    data object OnNextClick : AudioAction

    data object OnPreviousClick : AudioAction

    data object OnRetryClick : AudioAction
}
