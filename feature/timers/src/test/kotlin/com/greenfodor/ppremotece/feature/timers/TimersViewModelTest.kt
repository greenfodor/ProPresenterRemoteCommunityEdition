package com.greenfodor.ppremotece.feature.timers

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.greenfodor.ppremotece.core.designsystem.ui.UiMessages
import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.live.LiveStateRepository
import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.model.ConnectionStatus
import com.greenfodor.ppremotece.core.domain.model.LiveCue
import com.greenfodor.ppremotece.core.domain.model.LiveState
import com.greenfodor.ppremotece.core.domain.model.Timer
import com.greenfodor.ppremotece.core.domain.model.TimerOperation
import com.greenfodor.ppremotece.core.domain.model.TimerReading
import com.greenfodor.ppremotece.core.domain.model.TimerState
import com.greenfodor.ppremotece.core.domain.model.TimerType
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.EmptyResult
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.core.domain.timers.LiveTimer
import com.greenfodor.ppremotece.core.domain.timers.TimerIcon
import com.greenfodor.ppremotece.core.domain.timers.TimersRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class TimersViewModelTest {
    private val stopped = LiveTimer(
        Timer("t-0", "Timer 01", 0, TimerType.COUNTDOWN_TO_TIME, allowsOverrun = true),
        TimerReading("t-0", "08:30:00", TimerState.STOPPED)
    )
    private val running = LiveTimer(
        Timer("t-1", "Timer 02", 1, TimerType.ELAPSED, allowsOverrun = false),
        TimerReading("t-1", "00:00:05", TimerState.RUNNING)
    )
    private val repository = object : TimersRepository {
        override val timers = MutableStateFlow<Loadable<List<LiveTimer>>>(Loadable.Loaded(listOf(stopped, running)))
    }
    private val live = object : LiveStateRepository {
        override val liveState = MutableStateFlow(LiveState.Initial.copy(connection = ConnectionStatus.CONNECTED))
        override val lastLive = MutableStateFlow<LiveCue?>(null)
    }
    private val client = FakeTimerClient()
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val messages = UiMessages()

    private fun viewModel() = TimersViewModel(repository, live, client, messages)

    private val TimersState.loaded: List<TimerUi> get() = (timers as Loadable.Loaded).value

    @Test
    fun `each timer is shown as its card`() = runTest {
        viewModel().state.test {
            val timers = expectMostRecentItem().loaded
            assertThat(timers.map { it.name }).containsExactly("Timer 01", "Timer 02")
            assertThat(timers.map { it.card.icon }).containsExactly(TimerIcon.ALARM, TimerIcon.AVG_PACE)
            assertThat(timers.map { it.card.running }).containsExactly(false, true)
        }
    }

    @Test
    fun `timers are not loaded until the repository loads them`() = runTest {
        repository.timers.value = Loadable.NotLoaded

        viewModel().state.test {
            assertThat(expectMostRecentItem().timers).isEqualTo(Loadable.NotLoaded)
            repository.timers.value = Loadable.Loaded(emptyList())
            assertThat(awaitItem().timers).isEqualTo(Loadable.Loaded(emptyList()))
        }
    }

    @Test
    fun `timers the server rejected are unavailable`() = runTest {
        repository.timers.value = Loadable.Unavailable

        viewModel().state.test {
            assertThat(expectMostRecentItem().timers).isEqualTo(Loadable.Unavailable)
        }
    }

    @Test
    fun `readouts are dimmed while the stream reconnects`() = runTest {
        viewModel().state.test {
            assertThat(expectMostRecentItem().dimmed).isFalse()
            live.liveState.value = live.liveState.value.copy(connection = ConnectionStatus.RECONNECTING)
            assertThat(awaitItem().dimmed).isTrue()
            live.liveState.value = live.liveState.value.copy(connection = ConnectionStatus.CONNECTED)
            assertThat(awaitItem().dimmed).isFalse()
        }
    }

    @Test
    fun `the toggle starts a stopped timer and stops a running one`() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(TimersAction.OnToggleClick("t-0"))
        viewModel.onAction(TimersAction.OnToggleClick("t-1"))

        assertThat(client.operations).containsExactly("t-0" to TimerOperation.START, "t-1" to TimerOperation.STOP)
    }

    @Test
    fun `a toggle tap waits for the timer's next reading after a successful start`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onAction(TimersAction.OnToggleClick("t-0"))
        viewModel.onAction(TimersAction.OnToggleClick("t-0"))
        repository.timers.value = Loadable.Loaded(
            listOf(stopped.copy(reading = TimerReading("t-0", "08:29:59", TimerState.RUNNING)), running)
        )
        viewModel.onAction(TimersAction.OnToggleClick("t-0"))

        assertThat(client.operations).containsExactly("t-0" to TimerOperation.START, "t-0" to TimerOperation.STOP)
    }

    @Test
    fun `a timer takes taps again when no reading follows a successful start within two seconds`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.onAction(TimersAction.OnToggleClick("t-0"))
            advanceTimeBy(2_001)
            viewModel.onAction(TimersAction.OnToggleClick("t-0"))

            assertThat(client.operations).containsExactly("t-0" to TimerOperation.START, "t-0" to TimerOperation.START)
        }

    @Test
    fun `a failed operation frees the timer before its message is shown`() = runTest(dispatcher) {
        client.result = Result.Failure(DataError.Network.TIMEOUT)
        val viewModel = viewModel()

        viewModel.onAction(TimersAction.OnResetClick("t-0"))
        viewModel.onAction(TimersAction.OnResetClick("t-0"))

        assertThat(client.operations).containsExactly("t-0" to TimerOperation.RESET, "t-0" to TimerOperation.RESET)
    }

    @Test
    fun `reset resets the timer`() = runTest {
        viewModel().onAction(TimersAction.OnResetClick("t-1"))

        assertThat(client.operations).containsExactly("t-1" to TimerOperation.RESET)
    }

    @Test
    fun `taps on a timer are ignored while its request is in flight`() = runTest(dispatcher) {
        client.gate = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.onAction(TimersAction.OnToggleClick("t-0"))
        viewModel.onAction(TimersAction.OnToggleClick("t-0"))
        viewModel.onAction(TimersAction.OnResetClick("t-0"))
        viewModel.onAction(TimersAction.OnResetClick("t-1"))
        client.gate.complete(Unit)
        repository.timers.value = Loadable.Loaded(
            listOf(stopped.copy(reading = TimerReading("t-0", "08:29:59", TimerState.RUNNING)), running)
        )
        viewModel.onAction(TimersAction.OnResetClick("t-0"))

        assertThat(client.operations).containsExactly(
            "t-0" to TimerOperation.START,
            "t-1" to TimerOperation.RESET,
            "t-0" to TimerOperation.RESET
        )
    }

    @Test
    fun `a failed operation shows its message with the timer's name`() = runTest {
        client.result = Result.Failure(DataError.Network.TIMEOUT)
        val viewModel = viewModel()

        messages.messages.test {
            viewModel.onAction(TimersAction.OnToggleClick("t-1"))
            val stop = awaitItem() as UiText.StringResource
            viewModel.onAction(TimersAction.OnToggleClick("t-0"))
            val start = awaitItem() as UiText.StringResource
            viewModel.onAction(TimersAction.OnResetClick("t-0"))
            val reset = awaitItem() as UiText.StringResource

            assertThat(listOf(stop.id, start.id, reset.id))
                .containsExactly(R.string.timers_error_stop, R.string.timers_error_start, R.string.timers_error_reset)
            assertThat(stop.args).isEqualTo(listOf<Any>("Timer 02"))
            assertThat(reset.args).isEqualTo(listOf<Any>("Timer 01"))
        }
    }

    /** Records timer operations; every other call is not served. */
    private class FakeTimerClient : ProPresenterClient by notServed() {
        val operations = mutableListOf<Pair<String, TimerOperation>>()
        var gate = CompletableDeferred(Unit)
        var result: EmptyResult<DataError.Network> = Result.Success(Unit)

        override suspend fun timerOperation(uuid: String, operation: TimerOperation): EmptyResult<DataError.Network> {
            operations += uuid to operation
            gate.await()
            return result
        }
    }

    private companion object {
        fun notServed(): ProPresenterClient =
            Proxy.newProxyInstance(
                ProPresenterClient::class.java.classLoader,
                arrayOf(ProPresenterClient::class.java)
            ) {
                _,
                method,
                _
                ->
                throw UnsupportedOperationException(method.name)
            } as ProPresenterClient
    }
}
