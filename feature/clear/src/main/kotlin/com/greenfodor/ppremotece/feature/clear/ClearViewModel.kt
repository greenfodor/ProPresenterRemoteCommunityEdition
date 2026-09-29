package com.greenfodor.ppremotece.feature.clear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenfodor.ppremotece.core.designsystem.ui.toUiText
import com.greenfodor.ppremotece.core.domain.live.LiveStateRepository
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.live.clearAll
import com.greenfodor.ppremotece.core.domain.model.ClearGroup
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.EmptyResult
import com.greenfodor.ppremotece.core.domain.result.onFailure
import com.greenfodor.ppremotece.core.domain.result.onSuccess
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L
private const val ARMED_MILLIS = 3_000L

/**
 * Clear options: a layer or clear group is cleared on one tap; Clear All is armed by a first tap
 * and sends every layer clear on a second tap within 3 s. The layers with content come from the
 * live state and the clear groups are read each time the sheet opens. Failed clears are reported
 * as events, Clear All with the number of layers that failed.
 */
class ClearViewModel(
    private val client: ProPresenterClient,
    liveStateRepository: LiveStateRepository
) : ViewModel() {
    private val groups = MutableStateFlow<List<ClearGroup>>(emptyList())
    private val armed = MutableStateFlow(false)
    private var disarm: Job? = null

    val state: StateFlow<ClearState> =
        combine(
            liveStateRepository.liveState.map { it.layers }.distinctUntilChanged(),
            groups,
            armed
        ) { layers, groups, armed ->
            ClearState(activeLayers = layers, groups = groups, armed = armed)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ClearState())

    private val _events = Channel<ClearEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: ClearAction) {
        when (action) {
            ClearAction.OnSheetOpen -> viewModelScope.launch {
                client.clearGroups().onSuccess { groups.value = it }.onFailure { showError(it) }
            }
            is ClearAction.OnLayerClick -> send { client.clearLayer(action.layer) }
            is ClearAction.OnGroupClick -> send { client.triggerClearGroup(action.uuid) }
            ClearAction.OnClearAllClick -> onClearAllClick()
        }
    }

    private fun onClearAllClick() {
        disarm?.cancel()
        if (armed.value) {
            armed.value = false
            viewModelScope.launch {
                val failed = client.clearAll()
                if (failed > 0) _events.send(ClearEvent.LayersFailed(failed))
            }
        } else {
            armed.value = true
            disarm = viewModelScope.launch {
                delay(ARMED_MILLIS)
                armed.value = false
            }
        }
    }

    private fun send(clear: suspend () -> EmptyResult<DataError.Network>) {
        viewModelScope.launch { clear().onFailure { showError(it) } }
    }

    private suspend fun showError(error: DataError.Network) {
        _events.send(ClearEvent.ShowError(error.toUiText()))
    }
}
