package com.greenfodor.ppremotece.feature.timers

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
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
        override val timers = MutableStateFlow(listOf(stopped, running))
    }
    private val client = FakeTimerClient()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = TimersViewModel(repository, client)

    @Test
    fun `each timer is shown as its card`() = runTest {
        viewModel().state.test {
            val timers = expectMostRecentItem().timers
            assertThat(timers.map { it.name }).containsExactly("Timer 01", "Timer 02")
            assertThat(timers.map { it.card.icon }).containsExactly(TimerIcon.ALARM, TimerIcon.AVG_PACE)
            assertThat(timers.map { it.card.running }).containsExactly(false, true)
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
    fun `reset resets the timer`() = runTest {
        viewModel().onAction(TimersAction.OnResetClick("t-1"))

        assertThat(client.operations).containsExactly("t-1" to TimerOperation.RESET)
    }

    @Test
    fun `taps on a timer are ignored while its request is in flight`() = runTest {
        client.gate = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.onAction(TimersAction.OnToggleClick("t-0"))
        viewModel.onAction(TimersAction.OnToggleClick("t-0"))
        viewModel.onAction(TimersAction.OnResetClick("t-0"))
        viewModel.onAction(TimersAction.OnResetClick("t-1"))
        client.gate.complete(Unit)
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

        viewModel.events.test {
            viewModel.onAction(TimersAction.OnToggleClick("t-1"))
            val stop = (awaitItem() as TimersEvent.ShowError).message as UiText.StringResource
            viewModel.onAction(TimersAction.OnToggleClick("t-0"))
            val start = (awaitItem() as TimersEvent.ShowError).message as UiText.StringResource
            viewModel.onAction(TimersAction.OnResetClick("t-0"))
            val reset = (awaitItem() as TimersEvent.ShowError).message as UiText.StringResource

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
