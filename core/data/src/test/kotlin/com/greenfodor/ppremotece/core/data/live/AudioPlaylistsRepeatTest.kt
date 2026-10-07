package com.greenfodor.ppremotece.core.data.live

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.greenfodor.ppremotece.core.data.network.FakeProPresenter
import com.greenfodor.ppremotece.core.data.network.HttpClientFactory
import com.greenfodor.ppremotece.core.data.network.KtorProPresenterClient
import com.greenfodor.ppremotece.core.data.network.StreamEnd
import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.model.AudioFolder
import com.greenfodor.ppremotece.core.domain.model.AudioNode
import com.greenfodor.ppremotece.core.domain.model.AudioPlaylist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockWebServer
import mockwebserver3.junit5.StartStop
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The audio tree with folders, and the count of trees repeated after a reconnect. */
class AudioPlaylistsRepeatTest {
    @StartStop
    private val server = MockWebServer()

    private val fake = FakeProPresenter()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: StreamingLiveStateRepository

    @BeforeEach
    fun setUp() {
        server.dispatcher = fake
        val client = KtorProPresenterClient(HttpClientFactory.create(), server.url("/").toString())
        repository = StreamingLiveStateRepository(
            client = client,
            scope = scope,
            watchdogTimeout = 10.seconds,
            reconnectDelay = { 10.milliseconds },
            log = {}
        )
    }

    @AfterEach
    fun tearDown() {
        fake.releaseStalls()
        scope.cancel()
    }

    @Test
    fun `the audio tree keeps its folders`() = runBlocking<Unit> {
        fake.enqueueStream(fake.frames(listOf(TREE_FRAME)))
        val collector = launch { repository.liveState.collect {} }

        assertThat(awaitTree(TREE)).isEqualTo(Loadable.Loaded(TREE))
        collector.cancel()
    }

    @Test
    fun `the same tree on the first frame after a reconnect is counted once`() = runBlocking<Unit> {
        fake.enqueueStream(fake.frames(listOf(TREE_FRAME), end = StreamEnd.EOF))
        fake.enqueueStream(fake.frames(listOf(TREE_FRAME), listOf(TREE_FRAME), listOf(OTHER_TREE_FRAME)))
        val collector = launch { repository.liveState.collect {} }

        assertThat(withTimeout(5.seconds) { repository.audioPlaylistsRepeats.first { it > 0 } }).isEqualTo(1)
        awaitTree(OTHER_TREE)
        assertThat(repository.audioPlaylistsRepeats.value).isEqualTo(1)
        collector.cancel()
    }

    @Test
    fun `the same tree on a stream opened again after its collectors left is counted once`() = runBlocking<Unit> {
        fake.enqueueStream(fake.frames(listOf(TREE_FRAME)))
        fake.enqueueStream(fake.frames(listOf(TREE_FRAME)))
        val first = launch { repository.liveState.collect {} }
        awaitTree(TREE)
        first.cancel()
        first.join()
        assertThat(repository.audioPlaylistsRepeats.value).isEqualTo(0)

        val second = launch { repository.liveState.collect {} }

        assertThat(withTimeout(5.seconds) { repository.audioPlaylistsRepeats.first { it > 0 } }).isEqualTo(1)
        second.cancel()
    }

    @Test
    fun `the same tree twice without a reconnect is not counted`() = runBlocking<Unit> {
        fake.enqueueStream(fake.frames(listOf(TREE_FRAME), listOf(TREE_FRAME), listOf(OTHER_TREE_FRAME)))
        val collector = launch { repository.liveState.collect {} }

        awaitTree(OTHER_TREE)

        assertThat(repository.audioPlaylistsRepeats.value).isEqualTo(0)
        collector.cancel()
    }

    @Test
    fun `another tree on the first frame after a reconnect is not counted`() = runBlocking<Unit> {
        fake.enqueueStream(fake.frames(listOf(TREE_FRAME), end = StreamEnd.EOF))
        fake.enqueueStream(fake.frames(listOf(OTHER_TREE_FRAME)))
        val collector = launch { repository.liveState.collect {} }

        awaitTree(OTHER_TREE)

        assertThat(repository.audioPlaylistsRepeats.value).isEqualTo(0)
        collector.cancel()
    }

    private suspend fun awaitTree(tree: List<AudioNode>) =
        withTimeout(5.seconds) { repository.audioPlaylists.first { it == Loadable.Loaded(tree) } }

    private companion object {
        val TREE = listOf(
            AudioPlaylist("ap-0", "Audio Playlist 01"),
            AudioFolder("af-0", "Folder A", listOf(AudioPlaylist("ap-1", "Audio Playlist 02")))
        )
        val OTHER_TREE = listOf(AudioPlaylist("ap-0", "Audio Playlist 01"))
        const val TREE_FRAME = """{"url":"audio/playlists","data":[""" +
            """{"id":{"uuid":"ap-0","name":"Audio Playlist 01","index":0},"type":"playlist","children":[]},""" +
            """{"id":{"uuid":"af-0","name":"Folder A","index":1},"type":"group","children":[""" +
            """{"id":{"uuid":"ap-1","name":"Audio Playlist 02","index":0},"type":"playlist","children":[]}]}]}"""
        const val OTHER_TREE_FRAME = """{"url":"audio/playlists","data":[""" +
            """{"id":{"uuid":"ap-0","name":"Audio Playlist 01","index":0},"type":"playlist","children":[]}]}"""
    }
}
