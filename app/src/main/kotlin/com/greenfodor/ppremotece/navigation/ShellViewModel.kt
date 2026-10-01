package com.greenfodor.ppremotece.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenfodor.ppremotece.core.domain.live.LiveStateRepository
import com.greenfodor.ppremotece.core.domain.model.ConnectionStatus
import com.greenfodor.ppremotece.core.domain.settings.AppPreferences
import com.greenfodor.ppremotece.core.domain.settings.KeepAwake
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * Whether the live stream is reconnecting; collecting it keeps the stream open while the shell is
 * shown. On creation it reconnects to the saved host when no host is connected. [keepAwake] is the
 * keep-awake setting.
 */
class ShellViewModel(
    liveStateRepository: LiveStateRepository,
    restore: suspend () -> Unit,
    appPreferences: AppPreferences
) : ViewModel() {
    init {
        viewModelScope.launch { restore() }
    }

    val reconnecting: StateFlow<Boolean> =
        liveStateRepository.liveState
            .map { it.connection == ConnectionStatus.RECONNECTING }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    val keepAwake: StateFlow<KeepAwake> =
        appPreferences.keepAwake()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), KeepAwake.Default)
}
