package com.greenfodor.ppremotece.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenfodor.ppremotece.core.domain.live.LiveStateRepository
import com.greenfodor.ppremotece.core.domain.model.ConnectionStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private const val STOP_TIMEOUT_MILLIS = 5_000L

/** Whether the live stream is reconnecting; collecting it keeps the stream open while the shell is shown. */
class ShellViewModel(
    liveStateRepository: LiveStateRepository
) : ViewModel() {
    val reconnecting: StateFlow<Boolean> =
        liveStateRepository.liveState
            .map { it.connection == ConnectionStatus.RECONNECTING }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)
}
