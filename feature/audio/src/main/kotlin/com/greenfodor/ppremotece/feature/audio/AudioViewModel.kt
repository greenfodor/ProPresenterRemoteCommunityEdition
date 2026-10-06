package com.greenfodor.ppremotece.feature.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenfodor.ppremotece.core.designsystem.ui.UiMessages
import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.designsystem.ui.toUiText
import com.greenfodor.ppremotece.core.domain.audio.AudioRepository
import com.greenfodor.ppremotece.core.domain.audio.TransportButton
import com.greenfodor.ppremotece.core.domain.audio.nowPlaying
import com.greenfodor.ppremotece.core.domain.live.LiveStateRepository
import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.live.map
import com.greenfodor.ppremotece.core.domain.live.orEmpty
import com.greenfodor.ppremotece.core.domain.model.ActiveAudio
import com.greenfodor.ppremotece.core.domain.model.AudioTrack
import com.greenfodor.ppremotece.core.domain.model.ConnectionStatus
import com.greenfodor.ppremotece.core.domain.model.Transport
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.EmptyResult
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.core.domain.result.onFailure
import com.greenfodor.ppremotece.core.domain.transport.formatDuration
import com.greenfodor.ppremotece.core.domain.trigger.InFlightTriggers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * Audio: the audio bin's playlists of the [AudioRepository], as loaded, with the tracks of the
 * chosen one and the now-playing bar ([nowPlaying]). The chosen playlist is the one picked from the
 * menu or by a track tap, else the active one, else the one already shown, else the first; its
 * tracks are read when it is chosen, on each `audio/playlists` frame and on Retry, and a failed
 * re-read keeps the tracks already shown and posts its error. A tap plays a track; taps on a track
 * are ignored while its request is in flight, and a failure posts "Couldn't play {track}". The
 * track that the active audio names is marked playing, or paused while the loaded audio is not
 * playing, and not marked while nothing is loaded. The bar's
 * middle button pauses or resumes and its outer buttons play the previous or next track; a
 * disabled button sends nothing and a failure posts "Couldn't play", "Couldn't pause" or
 * "Couldn't skip". The readout is dimmed while the live stream reconnects.
 */
class AudioViewModel(
    private val audioRepository: AudioRepository,
    liveStateRepository: LiveStateRepository,
    private val client: ProPresenterClient,
    private val messages: UiMessages
) : ViewModel() {
    private val triggers = InFlightTriggers()
    private val picked = MutableStateFlow<String?>(null)
    private val retries = MutableStateFlow(0)
    private val trackList = MutableStateFlow(TrackList())

    /** The tracks read for [playlistUuid], or why they could not be read. */
    private data class TrackList(
        val playlistUuid: String? = null,
        val tracks: List<AudioTrack> = emptyList(),
        val loading: Boolean = false,
        val error: DataError.Network? = null
    )

    /** What the audio layer plays: the active track, the transport and its position. */
    private data class Playback(
        val active: ActiveAudio?,
        val transport: Transport?,
        val position: Double?
    )

    /** The active track and whether its audio plays; [playing] is null while nothing is loaded. */
    private data class Marked(
        val active: ActiveAudio?,
        val playing: Boolean?
    )

    /** Everything on the screen that does not move with the audio position. */
    private data class Rows(
        val playlists: Loadable<List<AudioPlaylistUi>>,
        val selected: String?,
        val tracks: List<AudioTrackUi>,
        val loading: Boolean,
        val error: DataError.Network?
    )

    @Volatile
    private var shown: String? = null

    private val selected: StateFlow<String?> =
        combine(audioRepository.audioPlaylists, picked, audioRepository.activeAudio) { playlists, picked, active ->
            val uuids = playlists.orEmpty().map { it.uuid }
            val next = picked?.takeIf { it in uuids }
                ?: active?.playlistUuid?.takeIf { it in uuids }
                ?: shown?.takeIf { it in uuids }
                ?: uuids.firstOrNull()
            next.also { shown = it }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val playback =
        combine(
            audioRepository.activeAudio,
            audioRepository.audioTransport,
            audioRepository.audioPosition,
            ::Playback
        )

    private val marked =
        combine(
            audioRepository.activeAudio,
            audioRepository.audioTransport.map { it?.takeIf { loaded -> loaded.uuid.isNotEmpty() }?.isPlaying }
                .distinctUntilChanged(),
            ::Marked
        )

    private val rows =
        combine(audioRepository.audioPlaylists, selected, trackList, marked) { playlists, selected, list, marked ->
            val read = list.takeIf { it.playlistUuid == selected }
            Rows(
                playlists = playlists.map { all -> all.map { AudioPlaylistUi(it.uuid, it.name) } },
                selected = selected,
                tracks = read?.tracks.orEmpty().map { it.toUi(selected, marked) },
                loading = if (read == null) selected != null else read.loading,
                error = read?.error
            )
        }.distinctUntilChanged()

    private val reconnecting =
        liveStateRepository.liveState.map { it.connection == ConnectionStatus.RECONNECTING }.distinctUntilChanged()

    val state: StateFlow<AudioState> =
        combine(rows, playback, reconnecting) { rows, playback, reconnecting ->
            AudioState(
                playlists = rows.playlists,
                selectedUuid = rows.selected,
                tracks = rows.tracks,
                tracksLoading = rows.loading,
                tracksError = rows.error?.toUiText(),
                bar = nowPlaying(playback.transport, playback.position, playback.active),
                dimmed = reconnecting
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AudioState())

    init {
        viewModelScope.launch {
            combine(selected, audioRepository.audioPlaylistFrames, retries) { uuid, _, _ -> uuid }
                .collectLatest(::readTracks)
        }
    }

    fun onAction(action: AudioAction) {
        when (action) {
            is AudioAction.OnPlaylistSelect -> picked.value = action.uuid
            is AudioAction.OnTrackClick -> playTrack(action.index)
            AudioAction.OnPlayPauseClick -> playOrPause()
            AudioAction.OnNextClick -> skip("next") { client.audioNext() }
            AudioAction.OnPreviousClick -> skip("previous") { client.audioPrevious() }
            AudioAction.OnRetryClick -> retries.update { it + 1 }
        }
    }

    private suspend fun readTracks(playlistUuid: String?) {
        if (playlistUuid == null) {
            trackList.value = TrackList()
            return
        }
        trackList.update { shown ->
            val kept = shown.tracks.takeIf { shown.playlistUuid == playlistUuid }.orEmpty()
            TrackList(playlistUuid, kept, loading = true)
        }
        val kept = trackList.value.tracks
        trackList.value = when (val read = client.audioPlaylist(playlistUuid)) {
            is Result.Success -> TrackList(playlistUuid, read.data)
            is Result.Failure -> if (kept.isEmpty()) {
                TrackList(playlistUuid, error = read.error)
            } else {
                messages.post(read.error.toUiText())
                TrackList(playlistUuid, kept)
            }
        }
    }

    private fun AudioTrack.toUi(playlistUuid: String?, marked: Marked): AudioTrackUi {
        val active = marked.active?.takeIf { it.playlistUuid == playlistUuid && it.trackUuid == uuid }
        val mark = when {
            active == null || marked.playing == null -> TrackMark.NONE
            marked.playing -> TrackMark.PLAYING
            else -> TrackMark.PAUSED
        }
        return AudioTrackUi(uuid, name, index, artist, formatDuration(durationSeconds), mark)
    }

    private fun playTrack(index: Int) {
        val list = trackList.value
        val playlistUuid = list.playlistUuid?.takeIf { it == selected.value } ?: return
        val track = list.tracks.firstOrNull { it.index == index } ?: return
        picked.value = playlistUuid
        send("track/$playlistUuid/$index", UiText.StringResource(R.string.audio_error_play_track, listOf(track.name))) {
            client.triggerAudioTrack(playlistUuid, index)
        }
    }

    private fun playOrPause() {
        val bar = state.value.bar
        if (!bar.playPauseEnabled) return
        if (bar.button == TransportButton.PAUSE) {
            send(PLAY_PAUSE_KEY, UiText.StringResource(R.string.audio_error_pause)) { client.audioPause() }
        } else {
            send(PLAY_PAUSE_KEY, UiText.StringResource(R.string.audio_error_play)) { client.audioPlay() }
        }
    }

    private fun skip(key: String, call: suspend () -> EmptyResult<DataError.Network>) {
        if (state.value.bar.skipEnabled) send(key, UiText.StringResource(R.string.audio_error_skip), call)
    }

    /** Sends [call] unless one for [key] is in flight, posting [failure] when it fails. */
    private fun send(key: String, failure: UiText, call: suspend () -> EmptyResult<DataError.Network>) {
        viewModelScope.launch {
            triggers.run(key) { call().onFailure { messages.post(failure) } }
        }
    }

    private companion object {
        const val PLAY_PAUSE_KEY = "play-pause"
    }
}
