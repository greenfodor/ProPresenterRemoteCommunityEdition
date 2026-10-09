package com.greenfodor.ppremotece.core.data.live

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.greenfodor.ppremotece.core.data.network.FakeProPresenter
import com.greenfodor.ppremotece.core.data.network.HttpClientFactory
import com.greenfodor.ppremotece.core.data.network.KtorProPresenterClient
import com.greenfodor.ppremotece.core.data.network.StreamEnd
import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.model.StageLayout
import com.greenfodor.ppremotece.core.domain.model.StageScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockWebServer
import mockwebserver3.junit5.StartStop
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The stage screens, layouts and layout map from the status stream, and a rejected stage url. */
class StageStreamTest {
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
    fun `the stage values are not loaded before their first frames`() = runBlocking<Unit> {
        assertThat(repository.stageScreens.value).isEqualTo(Loadable.NotLoaded)
        assertThat(repository.stageLayouts.value).isEqualTo(Loadable.NotLoaded)
        assertThat(repository.stageLayoutMap.value).isEqualTo(Loadable.NotLoaded)
    }

    @Test
    fun `the frames sent at subscribe load the screens, the layouts and the map`() = runBlocking<Unit> {
        fake.enqueueStream(fake.frames(listOf(MAP_FRAME, SCREENS_FRAME, LAYOUTS_FRAME)))
        val collector = launch { repository.liveState.collect {} }

        val layouts = withTimeout(5.seconds) { repository.stageLayouts.first { it is Loadable.Loaded } }
        val screens = withTimeout(5.seconds) { repository.stageScreens.first { it is Loadable.Loaded } }
        val map = withTimeout(5.seconds) { repository.stageLayoutMap.first { it is Loadable.Loaded } }
        collector.cancel()

        assertThat(screens).isEqualTo(
            Loadable.Loaded(listOf(StageScreen("s-0", "Stage Screen 01"), StageScreen("s-1", "Stage Screen 02")))
        )
        assertThat(layouts).isEqualTo(
            Loadable.Loaded(listOf(StageLayout("l-0", "Layout 01"), StageLayout("l-1", "Layout 02")))
        )
        assertThat(map).isEqualTo(Loadable.Loaded(mapOf("s-0" to "l-0", "s-1" to "l-1")))
    }

    @Test
    fun `a later layout map frame replaces the map`() = runBlocking<Unit> {
        fake.enqueueStream(fake.frames(listOf(MAP_FRAME, SCREENS_FRAME, LAYOUTS_FRAME), listOf(CHANGED_MAP_FRAME)))
        val collector = launch { repository.liveState.collect {} }

        val changed = Loadable.Loaded(mapOf("s-0" to "l-1", "s-1" to "l-1"))
        assertThat(withTimeout(5.seconds) { repository.stageLayoutMap.first { it == changed } }).isEqualTo(changed)
        collector.cancel()
    }

    @Test
    fun `a rejected stage url leaves the three stage values unavailable and is not subscribed again`() =
        runBlocking<Unit> {
            fake.enqueueStream(
                fake.frames(
                    listOf(MAP_FRAME, SCREENS_FRAME, LAYOUTS_FRAME, """["URL: stage/layouts. Error: 404 Not Found"]"""),
                    end = StreamEnd.EOF
                )
            )
            fake.enqueueStream(fake.frames(listOf(FakeProPresenter.HEARTBEAT_FRAME)))
            val collector = launch { repository.liveState.collect {} }

            assertThat(withTimeout(5.seconds) { repository.stageLayouts.first { it == Loadable.Unavailable } })
                .isEqualTo(Loadable.Unavailable)
            withTimeout(5.seconds) {
                while (fake.count("POST", "/v1/status/updates") < 2) delay(5.milliseconds)
            }
            collector.cancel()

            assertThat(repository.stageScreens.value).isEqualTo(Loadable.Unavailable)
            assertThat(repository.stageLayoutMap.value).isEqualTo(Loadable.Unavailable)
            val bodies = generateSequence { server.takeRequest(1, TimeUnit.SECONDS) }
                .filter { it.url.encodedPath == "/v1/status/updates" }
                .map { it.body?.utf8() }
                .toList()
            assertThat(bodies.take(2))
                .containsExactly(SUBSCRIPTIONS_BODY, SUBSCRIPTIONS_BODY.replace(""","stage/layouts"""", ""))
        }

    private companion object {
        const val SUBSCRIPTIONS_BODY = """["status/slide","timer/system_time","playlist/active","status/layers",""" +
            """"timers","timers/current","macro_collections","looks","look/current","prop_collections",""" +
            """"transport/presentation/current","transport/audio/current",""" +
            """"transport/audio/time","audio/playlists","audio/playlist/active",""" +
            """"stage/layout_map","stage/screens","stage/layouts"]"""
        const val SCREENS_FRAME = """{"url":"stage/screens","data":[""" +
            """{"uuid":"s-0","name":"Stage Screen 01","index":0},""" +
            """{"uuid":"s-1","name":"Stage Screen 02","index":1}]}"""
        const val LAYOUTS_FRAME = """{"url":"stage/layouts","data":[""" +
            """{"id":{"uuid":"l-0","name":"Layout 01","index":0}},""" +
            """{"id":{"uuid":"l-1","name":"Layout 02","index":1}}]}"""
        const val MAP_FRAME = """{"url":"stage/layout_map","data":[""" +
            """{"screen":{"uuid":"s-0","name":"Stage Screen 01","index":0},""" +
            """"layout":{"uuid":"l-0","name":"Layout 01","index":0}},""" +
            """{"screen":{"uuid":"s-1","name":"Stage Screen 02","index":1},""" +
            """"layout":{"uuid":"l-1","name":"Layout 02","index":1}}]}"""
        const val CHANGED_MAP_FRAME = """{"url":"stage/layout_map","data":[""" +
            """{"screen":{"uuid":"s-0","name":"Stage Screen 01","index":0},""" +
            """"layout":{"uuid":"l-1","name":"Layout 02","index":1}},""" +
            """{"screen":{"uuid":"s-1","name":"Stage Screen 02","index":1},""" +
            """"layout":{"uuid":"l-1","name":"Layout 02","index":1}}]}"""
    }
}
