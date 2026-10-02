package com.greenfodor.ppremotece.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenfodor.ppremotece.core.designsystem.ui.toUiText
import com.greenfodor.ppremotece.core.domain.live.ConnectionRepository
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.EmptyResult
import com.greenfodor.ppremotece.core.domain.result.onFailure
import com.greenfodor.ppremotece.core.domain.settings.AppPreferences
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * Settings: writes the keep-awake mode and the auto-connect switch to [AppPreferences] (a failed
 * write shows a message), shows the connected host, and disconnects after the Disconnect dialog is
 * confirmed.
 */
class SettingsViewModel(
    private val appPreferences: AppPreferences,
    private val connectionRepository: ConnectionRepository
) : ViewModel() {
    private val confirmingDisconnect = MutableStateFlow(false)

    private val _events = Channel<SettingsEvent>()
    val events = _events.receiveAsFlow()

    val state: StateFlow<SettingsState> =
        combine(
            appPreferences.keepAwake(),
            appPreferences.autoConnect(),
            connectionRepository.connectedHost,
            confirmingDisconnect
        ) { keepAwake, autoConnect, connected, confirming ->
            SettingsState(
                keepAwake = keepAwake,
                autoConnect = autoConnect,
                hostName = connected?.host?.name.orEmpty(),
                hostAddress = connected?.host?.let { "${it.address}:${it.port}" }.orEmpty(),
                hostDescription = connected?.version?.hostDescription.orEmpty(),
                confirmingDisconnect = confirming
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsState())

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.OnKeepAwakeChange -> save { appPreferences.setKeepAwake(action.mode) }
            is SettingsAction.OnAutoConnectChange -> save { appPreferences.setAutoConnect(action.enabled) }
            SettingsAction.OnDisconnectClick -> confirmingDisconnect.value = true
            SettingsAction.OnDisconnectDismiss -> confirmingDisconnect.value = false
            SettingsAction.OnDisconnectConfirm -> {
                confirmingDisconnect.value = false
                viewModelScope.launch {
                    connectionRepository.disconnect()
                    _events.send(SettingsEvent.Disconnected)
                }
            }
        }
    }

    private fun save(write: suspend () -> EmptyResult<DataError.Local>) {
        viewModelScope.launch {
            write().onFailure { _events.send(SettingsEvent.ShowError(it.toUiText())) }
        }
    }
}
