package com.greenfodor.ppremotece.feature.clear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenfodor.ppremotece.core.designsystem.ui.toUiText
import com.greenfodor.ppremotece.core.domain.live.LiveStateRepository
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.model.ClearGroup
import com.greenfodor.ppremotece.core.domain.model.ClearGroupIcon
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.EmptyResult
import com.greenfodor.ppremotece.core.domain.result.onFailure
import com.greenfodor.ppremotece.core.domain.result.onSuccess
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * Clear options: a layer or clear group is cleared on one tap. The clear groups are read each time
 * the sheet opens; each group's icon is read once, and again on a later open if it failed. The
 * layers with content come from the live state; failed clears are reported as events.
 */
class ClearViewModel(
    private val client: ProPresenterClient,
    liveStateRepository: LiveStateRepository
) : ViewModel() {
    private val groups = MutableStateFlow<List<ClearGroup>>(emptyList())
    private val icons = MutableStateFlow<Map<String, ClearGroupIcon>>(emptyMap())
    private val iconsReading = mutableSetOf<String>()

    val state: StateFlow<ClearState> =
        combine(
            liveStateRepository.liveState.map { it.layers }.distinctUntilChanged(),
            groups,
            icons
        ) { layers, groups, icons ->
            ClearState(activeLayers = layers, groups = groups, icons = icons)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ClearState())

    private val _events = Channel<ClearEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: ClearAction) {
        when (action) {
            ClearAction.OnSheetOpen -> viewModelScope.launch {
                client.clearGroups()
                    .onSuccess {
                        groups.value = it
                        readIcons(it)
                    }.onFailure { showError(it) }
            }
            is ClearAction.OnLayerClick -> send { client.clearLayer(action.layer) }
            is ClearAction.OnGroupClick -> send { client.triggerClearGroup(action.uuid) }
        }
    }

    private fun readIcons(groups: List<ClearGroup>) {
        groups.filterNot { it.uuid in icons.value || it.uuid in iconsReading }.forEach { group ->
            iconsReading += group.uuid
            viewModelScope.launch {
                client.clearGroupIcon(group.uuid).onSuccess { icon -> icons.update { it + (group.uuid to icon) } }
                iconsReading -= group.uuid
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
