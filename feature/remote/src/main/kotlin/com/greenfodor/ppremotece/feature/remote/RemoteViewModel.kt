package com.greenfodor.ppremotece.feature.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenfodor.ppremotece.core.domain.live.LiveStateRepository
import com.greenfodor.ppremotece.core.domain.model.ConnectionStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private const val STOP_TIMEOUT_MILLIS = 5_000L

data class RemoteState(
    val connection: ConnectionStatus = ConnectionStatus.CONNECTING
)

/** The Remote tab: the connection status of the live stream. */
class RemoteViewModel(
    liveStateRepository: LiveStateRepository
) : ViewModel() {
    val state: StateFlow<RemoteState> =
        liveStateRepository.liveState
            .map { RemoteState(connection = it.connection) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RemoteState())
}
