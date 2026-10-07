package com.greenfodor.ppremotece.feature.audio

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.greenfodor.ppremotece.core.designsystem.ui.UiMessages
import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.audio.AudioRepository
import com.greenfodor.ppremotece.core.domain.audio.NowPlaying
import com.greenfodor.ppremotece.core.domain.audio.TransportButton
import com.greenfodor.ppremotece.core.domain.live.LiveStateRepository
import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.model.ActiveAudio
import com.greenfodor.ppremotece.core.domain.model.AudioPlaylist
import com.greenfodor.ppremotece.core.domain.model.AudioTrack
import com.greenfodor.ppremotece.core.domain.model.ConnectionStatus
import com.greenfodor.ppremotece.core.domain.model.LiveCue
import com.greenfodor.ppremotece.core.domain.model.LiveState
import com.greenfodor.ppremotece.core.domain.model.Transport
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.EmptyResult
import com.greenfodor.ppremotece.core.domain.result.Result
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class AudioViewModelTest {
    private val playlists =
        listOf(AudioPlaylist("p-0", "Audio Playlist 01", 0), AudioPlaylist("p-1", "Audio Playlist 02", 1))
    private val audio = object : AudioRepository {
        override val audioPlaylists =
            MutableStateFlow<Loadable<List<AudioPlaylist>>>(Loadable.Loaded(this@AudioViewModelTest.playlists))
        override val activeAudio = MutableStateFlow<ActiveAudio?>(null)
        override val audioTransport = MutableStateFlow<Loadable<Transport>>(Loadable.NotLoaded)
        override val audioPosition = MutableStateFlow<Double?>(null)
    }
    private val live = object : LiveStateRepository {
        override val liveState = MutableStateFlow(LiveState(ConnectionStatus.CONNECTED, item = null, slide = null))
        override val lastLive = MutableStateFlow<LiveCue?>(null)
    }
    private val client = FakeAudioClient()
    private val messages = UiMessages()
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        client.tracks["p-0"] = listOf(track("a-0", "Track 01", 0), track("a-1", "Track 02", 1))
        client.tracks["p-1"] =
            listOf(track("b-0", "Track 03", 0), track("b-1", "Track 04", 1), track("b-2", "Track 05", 2))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = AudioViewModel(audio, live, client, messages)

    private fun track(uuid: String, name: String, index: Int) =
        AudioTrack(uuid, name, index, artist = "Artist 0${index + 1}", durationSeconds = 200 + index)

    private fun playing(isPlaying: Boolean = true, uuid: String = "x-0") =
        Loadable.Loaded(Transport(isPlaying, uuid, "Media 04", "Artist 02", audioOnly = true, durationSeconds = 215.9))

    private suspend fun messageId(): Int {
        var id = 0
        messages.messages.test { id = (awaitItem() as UiText.StringResource).id }
        return id
    }

    @Test
    fun `the picker starts on the first playlist and lists its tracks`() = runTest(dispatcher) {
        viewModel().state.test {
            val state = expectMostRecentItem()
            assertThat((state.playlists as Loadable.Loaded).value.map { it.name })
                .containsExactly("Audio Playlist 01", "Audio Playlist 02")
            assertThat(state.selectedUuid).isEqualTo("p-0")
            assertThat(state.selectedName).isEqualTo("Audio Playlist 01")
            assertThat(state.tracks.map { it.name }).containsExactly("Track 01", "Track 02")
            assertThat(state.tracks.first().artist).isEqualTo("Artist 01")
            assertThat(state.tracks.first().duration).isEqualTo("3:20")
        }
        assertThat(client.reads).containsExactly("p-0")
    }

    @Test
    fun `the picker starts on the active playlist when one is active`() = runTest(dispatcher) {
        audio.activeAudio.value = ActiveAudio("p-1", "b-1", 1)

        viewModel().state.test {
            val state = expectMostRecentItem()
            assertThat(state.selectedUuid).isEqualTo("p-1")
            assertThat(state.tracks.map { it.name }).containsExactly("Track 03", "Track 04", "Track 05")
        }
    }

    @Test
    fun `a chosen playlist is read and stays chosen when another one becomes active`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.state.test {
            expectMostRecentItem()
            viewModel.onAction(AudioAction.OnPlaylistSelect("p-1"))
            assertThat(
                expectMostRecentItem().tracks.map {
                    it.name
                }
            ).containsExactly("Track 03", "Track 04", "Track 05")
            audio.activeAudio.value = ActiveAudio("p-0", "a-0", 0)
            assertThat(viewModel.state.value.selectedUuid).isEqualTo("p-1")
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(client.reads).containsExactly("p-0", "p-1")
    }

    @Test
    fun `the first audio playlists value causes exactly one track read`() = runTest(dispatcher) {
        audio.audioPlaylists.value = Loadable.NotLoaded
        val viewModel = viewModel()

        viewModel.state.test {
            expectMostRecentItem()
            audio.audioPlaylists.value = Loadable.Loaded(playlists)
            assertThat(expectMostRecentItem().tracks.map { it.name }).containsExactly("Track 01", "Track 02")
        }
        assertThat(client.reads).containsExactly("p-0")
    }

    @Test
    fun `an audio playlists value that did not change reads no tracks again`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.state.test {
            expectMostRecentItem()
            audio.audioPlaylists.value = Loadable.Loaded(playlists.toList())
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(client.reads).containsExactly("p-0")
    }

    @Test
    fun `the tracks are read again when the audio playlists value changes`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.state.test {
            expectMostRecentItem()
            client.tracks["p-0"] = listOf(track("a-9", "Track 09", 0))
            audio.audioPlaylists.value = Loadable.Loaded(playlists + AudioPlaylist("p-2", "Audio Playlist 03", 2))
            assertThat(expectMostRecentItem().tracks.map { it.name }).containsExactly("Track 09")
        }
        assertThat(client.reads).containsExactly("p-0", "p-0")
    }

    @Test
    fun `tracks that cannot be read show an error and a retry reads them again`() = runTest(dispatcher) {
        client.readFailure = DataError.Network.TIMEOUT
        val viewModel = viewModel()

        viewModel.state.test {
            assertThat(expectMostRecentItem().tracksError).isNotNull()
            client.readFailure = null
            viewModel.onAction(AudioAction.OnRetryClick)
            val state = expectMostRecentItem()
            assertThat(state.tracksError).isNull()
            assertThat(state.tracks.size).isEqualTo(2)
        }
    }

    @Test
    fun `no playlists, not loaded and unavailable pass through`() = runTest(dispatcher) {
        audio.audioPlaylists.value = Loadable.NotLoaded

        viewModel().state.test {
            assertThat(expectMostRecentItem().playlists).isEqualTo(Loadable.NotLoaded)
            audio.audioPlaylists.value = Loadable.Unavailable
            assertThat(expectMostRecentItem().playlists).isEqualTo(Loadable.Unavailable)
            audio.audioPlaylists.value = Loadable.Loaded(emptyList())
            val empty = expectMostRecentItem()
            assertThat((empty.playlists as Loadable.Loaded).value).isEmpty()
            assertThat(empty.selectedUuid).isNull()
        }
        assertThat(client.reads).isEmpty()
    }

    @Test
    fun `a tap plays the track by the chosen playlist and the track uuid`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.state.test {
            expectMostRecentItem()
            viewModel.onAction(AudioAction.OnPlaylistSelect("p-1"))
            viewModel.onAction(AudioAction.OnTrackClick("b-2"))
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(client.calls).containsExactly("track p-1/b-2")
    }

    @Test
    fun `taps on a track are ignored while its request is in flight`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.state.test {
            expectMostRecentItem()
            client.gate = CompletableDeferred()
            viewModel.onAction(AudioAction.OnTrackClick("a-0"))
            viewModel.onAction(AudioAction.OnTrackClick("a-0"))
            viewModel.onAction(AudioAction.OnTrackClick("a-1"))
            client.gate.complete(Unit)
            viewModel.onAction(AudioAction.OnTrackClick("a-0"))
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(client.calls).containsExactly("track p-0/a-0", "track p-0/a-1", "track p-0/a-0")
    }

    @Test
    fun `a failed track trigger posts its message with the track's name`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.state.test {
            expectMostRecentItem()
            client.result = Result.Failure(DataError.Network.TIMEOUT)
            viewModel.onAction(AudioAction.OnTrackClick("a-1"))
            cancelAndIgnoreRemainingEvents()
        }

        messages.messages.test {
            val message = awaitItem() as UiText.StringResource
            assertThat(message.id).isEqualTo(R.string.audio_error_play_track)
            assertThat(message.args).isEqualTo(listOf<Any>("Track 02"))
        }
    }

    @Test
    fun `the active track's row is marked playing, or paused while the audio is paused`() = runTest(dispatcher) {
        audio.activeAudio.value = ActiveAudio("p-0", "a-1", 1)
        audio.audioTransport.value = playing()

        viewModel().state.test {
            assertThat(expectMostRecentItem().tracks.map { it.mark }).containsExactly(TrackMark.NONE, TrackMark.PLAYING)
            audio.audioTransport.value = playing(isPlaying = false)
            assertThat(expectMostRecentItem().tracks.map { it.mark }).containsExactly(TrackMark.NONE, TrackMark.PAUSED)
        }
    }

    @Test
    fun `the active track is not marked while nothing is loaded`() = runTest(dispatcher) {
        audio.activeAudio.value = ActiveAudio("p-0", "a-1", 1)
        audio.audioTransport.value = playing(isPlaying = false, uuid = "")

        viewModel().state.test {
            assertThat(expectMostRecentItem().tracks.map { it.mark }).containsExactly(TrackMark.NONE, TrackMark.NONE)
        }
    }

    @Test
    fun `the shown playlist stays when the audio is cleared`() = runTest(dispatcher) {
        audio.activeAudio.value = ActiveAudio("p-1", "b-1", 1)
        val viewModel = viewModel()

        viewModel.state.test {
            assertThat(expectMostRecentItem().selectedUuid).isEqualTo("p-1")
            audio.activeAudio.value = null
            assertThat(viewModel.state.value.selectedUuid).isEqualTo("p-1")
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(client.reads).containsExactly("p-1")
    }

    @Test
    fun `an unavailable audio transport gives the unavailable bar and its buttons send nothing`() =
        runTest(dispatcher) {
            audio.activeAudio.value = ActiveAudio("p-0", "a-1", 1)
            audio.audioTransport.value = Loadable.Unavailable
            val viewModel = viewModel()

            viewModel.state.test {
                assertThat(expectMostRecentItem().bar).isEqualTo(NowPlaying.Unavailable)
                viewModel.onAction(AudioAction.OnPlayPauseClick)
                viewModel.onAction(AudioAction.OnNextClick)
                viewModel.onAction(AudioAction.OnPreviousClick)
                cancelAndIgnoreRemainingEvents()
            }

            assertThat(client.calls).isEmpty()
        }

    @Test
    fun `a track tap pins the shown playlist`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.state.test {
            expectMostRecentItem()
            viewModel.onAction(AudioAction.OnTrackClick("a-0"))
            audio.activeAudio.value = ActiveAudio("p-1", "b-1", 1)
            assertThat(viewModel.state.value.selectedUuid).isEqualTo("p-0")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed re-read keeps the tracks and posts its error`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.state.test {
            expectMostRecentItem()
            client.readFailure = DataError.Network.TIMEOUT
            audio.audioPlaylists.value = Loadable.Loaded(playlists + AudioPlaylist("p-2", "Audio Playlist 03", 2))
            val state = viewModel.state.value
            assertThat(state.tracks.map { it.name }).containsExactly("Track 01", "Track 02")
            assertThat(state.tracksError).isNull()
            cancelAndIgnoreRemainingEvents()
        }
        messages.messages.test { assertThat(awaitItem()).isNotNull() }
    }

    @Test
    fun `a newly chosen playlist is loading until its tracks are read`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.state.test {
            expectMostRecentItem()
            client.readGate = CompletableDeferred()
            viewModel.onAction(AudioAction.OnPlaylistSelect("p-1"))
            val reading = expectMostRecentItem()
            assertThat(reading.tracks).isEmpty()
            assertThat(reading.tracksLoading).isTrue()
            client.readGate.complete(Unit)
            assertThat(expectMostRecentItem().tracks.size).isEqualTo(3)
        }
    }

    @Test
    fun `tracks that repeat a uuid are all listed under their own index`() = runTest(dispatcher) {
        client.tracks["p-0"] = listOf(track("a-0", "Track 01", 0), track("a-0", "Track 01", 1))

        viewModel().state.test {
            val tracks = expectMostRecentItem().tracks
            assertThat(tracks.map { it.index }).containsExactly(0, 1)
            assertThat(tracks.map { it.index }.distinct().size).isEqualTo(tracks.size)
        }
    }

    @Test
    fun `a position frame leaves the track rows as they are`() = runTest(dispatcher) {
        audio.activeAudio.value = ActiveAudio("p-0", "a-1", 1)
        audio.audioTransport.value = playing()
        audio.audioPosition.value = 1.0

        viewModel().state.test {
            val before = expectMostRecentItem()
            audio.audioPosition.value = 2.0
            val after = expectMostRecentItem()
            assertThat(after.bar.readout).isEqualTo("0:02 / 3:35")
            assertThat(after.tracks === before.tracks).isTrue()
        }
    }

    @Test
    fun `a track of another playlist with the same index is not marked`() = runTest(dispatcher) {
        val viewModel = viewModel()
        audio.audioTransport.value = playing()

        viewModel.state.test {
            expectMostRecentItem()
            viewModel.onAction(AudioAction.OnPlaylistSelect("p-0"))
            audio.activeAudio.value = ActiveAudio("p-1", "b-1", 1)
            assertThat(expectMostRecentItem().tracks.map { it.mark }).containsExactly(TrackMark.NONE, TrackMark.NONE)
        }
    }

    @Test
    fun `with nothing loaded the bar is empty and its buttons send nothing`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.state.test {
            val bar = expectMostRecentItem().bar
            assertThat(bar.loaded).isFalse()
            viewModel.onAction(AudioAction.OnPlayPauseClick)
            viewModel.onAction(AudioAction.OnNextClick)
            viewModel.onAction(AudioAction.OnPreviousClick)
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(client.calls).isEmpty()
    }

    @Test
    fun `the middle button pauses playing audio and plays paused audio`() = runTest(dispatcher) {
        audio.activeAudio.value = ActiveAudio("p-0", "a-1", 1)
        audio.audioTransport.value = playing()
        audio.audioPosition.value = 41.3
        val viewModel = viewModel()

        viewModel.state.test {
            val bar = expectMostRecentItem().bar
            assertThat(bar.button).isEqualTo(TransportButton.PAUSE)
            assertThat(bar.readout).isEqualTo("0:41 / 3:35")
            viewModel.onAction(AudioAction.OnPlayPauseClick)
            audio.audioTransport.value = playing(isPlaying = false)
            assertThat(expectMostRecentItem().bar.button).isEqualTo(TransportButton.PLAY)
            viewModel.onAction(AudioAction.OnPlayPauseClick)
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(client.calls).containsExactly("pause", "play")
    }

    @Test
    fun `next and previous are sent while an audio playlist is active`() = runTest(dispatcher) {
        audio.activeAudio.value = ActiveAudio("p-0", "a-1", 1)
        audio.audioTransport.value = playing()
        val viewModel = viewModel()

        viewModel.state.test {
            assertThat(expectMostRecentItem().bar.skipEnabled).isTrue()
            viewModel.onAction(AudioAction.OnNextClick)
            viewModel.onAction(AudioAction.OnPreviousClick)
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(client.calls).containsExactly("next", "previous")
    }

    @Test
    fun `audio started outside the audio bin can be paused but not skipped`() = runTest(dispatcher) {
        audio.audioTransport.value = playing()
        val viewModel = viewModel()

        viewModel.state.test {
            val bar = expectMostRecentItem().bar
            assertThat(bar.playPauseEnabled).isTrue()
            assertThat(bar.skipEnabled).isFalse()
            viewModel.onAction(AudioAction.OnNextClick)
            viewModel.onAction(AudioAction.OnPreviousClick)
            viewModel.onAction(AudioAction.OnPlayPauseClick)
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(client.calls).containsExactly("pause")
    }

    @Test
    fun `each failed bar call posts its own message`() = runTest(dispatcher) {
        audio.activeAudio.value = ActiveAudio("p-0", "a-1", 1)
        audio.audioTransport.value = playing()
        client.result = Result.Failure(DataError.Network.TIMEOUT)
        val viewModel = viewModel()

        viewModel.state.test {
            expectMostRecentItem()
            viewModel.onAction(AudioAction.OnPlayPauseClick)
            assertThat(messageId()).isEqualTo(R.string.audio_error_pause)
            viewModel.onAction(AudioAction.OnNextClick)
            assertThat(messageId()).isEqualTo(R.string.audio_error_skip)
            viewModel.onAction(AudioAction.OnPreviousClick)
            assertThat(messageId()).isEqualTo(R.string.audio_error_skip)
            audio.audioTransport.value = playing(isPlaying = false)
            expectMostRecentItem()
            viewModel.onAction(AudioAction.OnPlayPauseClick)
            assertThat(messageId()).isEqualTo(R.string.audio_error_play)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the readout is dimmed while the stream reconnects`() = runTest(dispatcher) {
        viewModel().state.test {
            assertThat(expectMostRecentItem().dimmed).isFalse()
            live.liveState.value = live.liveState.value.copy(connection = ConnectionStatus.RECONNECTING)
            assertThat(expectMostRecentItem().dimmed).isTrue()
        }
    }

    /** Serves [tracks] and records the audio calls; every other call is not served. */
    private class FakeAudioClient : ProPresenterClient by notServed() {
        val tracks = mutableMapOf<String, List<AudioTrack>>()
        val reads = mutableListOf<String>()
        val calls = mutableListOf<String>()
        var readFailure: DataError.Network? = null
        var readGate = CompletableDeferred(Unit)
        var gate = CompletableDeferred(Unit)
        var result: EmptyResult<DataError.Network> = Result.Success(Unit)

        override suspend fun audioPlaylist(uuid: String): Result<List<AudioTrack>, DataError.Network> {
            reads += uuid
            readGate.await()
            return readFailure?.let { Result.Failure(it) } ?: Result.Success(tracks.getValue(uuid))
        }

        override suspend fun triggerAudioTrack(playlistUuid: String, trackUuid: String) =
            call("track $playlistUuid/$trackUuid")

        override suspend fun audioNext() = call("next")

        override suspend fun audioPrevious() = call("previous")

        override suspend fun audioPlay() = call("play")

        override suspend fun audioPause() = call("pause")

        private suspend fun call(name: String): EmptyResult<DataError.Network> {
            calls += name
            gate.await()
            return result
        }
    }

    private companion object {
        fun notServed(): ProPresenterClient =
            Proxy.newProxyInstance(
                ProPresenterClient::class.java.classLoader,
                arrayOf(ProPresenterClient::class.java)
            ) { _, method, _ -> throw UnsupportedOperationException(method.name) } as ProPresenterClient
    }
}
