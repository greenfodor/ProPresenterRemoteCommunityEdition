package com.greenfodor.ppremotece.feature.timers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.model.TimerOperation
import com.greenfodor.ppremotece.core.domain.result.onFailure
import com.greenfodor.ppremotece.core.domain.timers.LiveTimer
import com.greenfodor.ppremotece.core.domain.timers.TimersRepository
import com.greenfodor.ppremotece.core.domain.timers.timerCard
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * Timers: one card per timer of the [TimersRepository] ([timerCard]). Start/Stop and Reset send
 * the timer operation once per tap; taps on a timer are ignored while its request is in flight,
 * and a failure shows "Couldn't {start|stop|reset} {timer}".
 */
class TimersViewModel(
    private val timersRepository: TimersRepository,
    private val client: ProPresenterClient
) : ViewModel() {
    private val inFlight = mutableSetOf<String>()

    private val _events = Channel<TimersEvent>()
    val events = _events.receiveAsFlow()

    val state: StateFlow<TimersState> =
        timersRepository.timers
            .map { timers ->
                TimersState(timers.map { TimerUi(it.timer.uuid, it.timer.name, timerCard(it.timer, it.reading)) })
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TimersState())

    fun onAction(action: TimersAction) {
        when (action) {
            is TimersAction.OnToggleClick -> operate(action.uuid) { timerCard(it.timer, it.reading).toggle }
            is TimersAction.OnResetClick -> operate(action.uuid) { TimerOperation.RESET }
        }
    }

    private fun operate(uuid: String, operationOf: (LiveTimer) -> TimerOperation) {
        val timer = timersRepository.timers.value.firstOrNull { it.timer.uuid == uuid } ?: return
        if (!inFlight.add(uuid)) return
        val operation = operationOf(timer)
        viewModelScope.launch {
            client.timerOperation(uuid, operation).onFailure {
                _events.send(TimersEvent.ShowError(UiText.StringResource(errorOf(operation), listOf(timer.timer.name))))
            }
            inFlight.remove(uuid)
        }
    }

    private fun errorOf(operation: TimerOperation): Int =
        when (operation) {
            TimerOperation.START -> R.string.timers_error_start
            TimerOperation.STOP -> R.string.timers_error_stop
            TimerOperation.RESET -> R.string.timers_error_reset
        }
}
