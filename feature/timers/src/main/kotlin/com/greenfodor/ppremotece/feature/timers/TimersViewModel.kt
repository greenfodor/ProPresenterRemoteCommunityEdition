package com.greenfodor.ppremotece.feature.timers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenfodor.ppremotece.core.designsystem.ui.UiMessages
import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.live.LiveStateRepository
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.live.map
import com.greenfodor.ppremotece.core.domain.live.orEmpty
import com.greenfodor.ppremotece.core.domain.model.ConnectionStatus
import com.greenfodor.ppremotece.core.domain.model.TimerOperation
import com.greenfodor.ppremotece.core.domain.result.onFailure
import com.greenfodor.ppremotece.core.domain.result.onSuccess
import com.greenfodor.ppremotece.core.domain.timers.LiveTimer
import com.greenfodor.ppremotece.core.domain.timers.TimersRepository
import com.greenfodor.ppremotece.core.domain.timers.timerCard
import com.greenfodor.ppremotece.core.domain.trigger.InFlightTriggers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val STOP_TIMEOUT_MILLIS = 5_000L
private const val READING_WAIT_MILLIS = 2_000L

/**
 * Timers: one card per timer of the [TimersRepository] ([timerCard]), as loaded, with readouts
 * dimmed while the live stream reconnects. Start/Stop and Reset send
 * the timer operation once per tap; taps on a timer are ignored while its request is in flight
 * and, after a successful Start or Stop, until the timer's next reading or [READING_WAIT_MILLIS].
 * A failure posts "Couldn't {start|stop|reset} {timer}".
 */
class TimersViewModel(
    private val timersRepository: TimersRepository,
    liveStateRepository: LiveStateRepository,
    private val client: ProPresenterClient,
    private val messages: UiMessages
) : ViewModel() {
    private val triggers = InFlightTriggers()

    val state: StateFlow<TimersState> =
        combine(
            timersRepository.timers,
            liveStateRepository.liveState.map { it.connection == ConnectionStatus.RECONNECTING }.distinctUntilChanged()
        ) { timers, reconnecting ->
            TimersState(
                timers = timers.map { list ->
                    list.map { TimerUi(it.timer.uuid, it.timer.name, timerCard(it.timer, it.reading)) }
                },
                dimmed = reconnecting
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TimersState())

    fun onAction(action: TimersAction) {
        when (action) {
            is TimersAction.OnToggleClick -> operate(action.uuid) { timerCard(it.timer, it.reading).toggle }
            is TimersAction.OnResetClick -> operate(action.uuid) { TimerOperation.RESET }
        }
    }

    private fun operate(uuid: String, operationOf: (LiveTimer) -> TimerOperation) {
        val timer = timersRepository.timers.value.orEmpty().firstOrNull { it.timer.uuid == uuid } ?: return
        viewModelScope.launch {
            triggers.run(uuid) {
                val operation = operationOf(timer)
                client
                    .timerOperation(uuid, operation)
                    .onSuccess {
                        if (operation != TimerOperation.RESET) awaitNextReading(timer)
                    }.onFailure {
                        messages.post(UiText.StringResource(errorOf(operation), listOf(timer.timer.name)))
                    }
            }
        }
    }

    private suspend fun awaitNextReading(timer: LiveTimer) {
        withTimeoutOrNull(READING_WAIT_MILLIS) {
            timersRepository.timers.first { timers ->
                timers.orEmpty().firstOrNull { it.timer.uuid == timer.timer.uuid }?.reading != timer.reading
            }
        }
    }

    private fun errorOf(operation: TimerOperation): Int =
        when (operation) {
            TimerOperation.START -> R.string.timers_error_start
            TimerOperation.STOP -> R.string.timers_error_stop
            TimerOperation.RESET -> R.string.timers_error_reset
        }
}
