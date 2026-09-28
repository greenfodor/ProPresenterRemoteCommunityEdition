package com.greenfodor.ppremotece.feature.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.designsystem.ui.toUiText
import com.greenfodor.ppremotece.core.domain.live.ConnectionRepository
import com.greenfodor.ppremotece.core.domain.live.HostDiscovery
import com.greenfodor.ppremotece.core.domain.model.ProPresenterHost
import com.greenfodor.ppremotece.core.domain.result.onFailure
import com.greenfodor.ppremotece.core.domain.result.onSuccess
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Connect screen: once the local network permission has been answered, browses for hosts and,
 * if a host is saved, connects to it. A failed auto-connect leaves its address and port filled in.
 */
class ConnectViewModel(
    private val connectionRepository: ConnectionRepository,
    private val hostDiscovery: HostDiscovery
) : ViewModel() {
    private val _state = MutableStateFlow(ConnectState())
    val state = _state.asStateFlow()

    private val _events = Channel<ConnectEvent>()
    val events = _events.receiveAsFlow()

    private var started = false

    fun onAction(action: ConnectAction) {
        when (action) {
            is ConnectAction.OnPermissionResult -> start(action.granted)
            is ConnectAction.OnAddressChange -> _state.update { it.copy(address = action.address, error = null) }
            is ConnectAction.OnPortChange -> _state.update {
                it.copy(port = action.port.filter(Char::isDigit), error = null)
            }
            ConnectAction.OnConnectClick -> connectToEnteredHost()
            is ConnectAction.OnDiscoveredHostClick -> connect(action.host)
        }
    }

    private fun start(permissionGranted: Boolean) {
        if (started) return
        started = true
        if (!permissionGranted) {
            _state.update { it.copy(error = UiText.StringResource(R.string.connect_error_permission)) }
        }
        viewModelScope.launch {
            hostDiscovery.discoveredHosts()
                .catch { emit(emptyList()) }
                .collect { hosts -> _state.update { it.copy(discoveredHosts = hosts) } }
        }
        viewModelScope.launch {
            connectionRepository.savedHost()?.let(::connect)
        }
    }

    private fun connectToEnteredHost() {
        val address = _state.value.address.trim()
        val port = _state.value.port.toIntOrNull()
        when {
            address.isEmpty() -> _state.update {
                it.copy(error = UiText.StringResource(R.string.connect_error_address))
            }
            port == null || port !in PORT_RANGE ->
                _state.update { it.copy(error = UiText.StringResource(R.string.connect_error_port)) }
            else -> connect(ProPresenterHost(name = address, address = address, port = port))
        }
    }

    private fun connect(host: ProPresenterHost) {
        if (_state.value.isConnecting) return
        _state.update {
            it.copy(address = host.address, port = host.port.toString(), isConnecting = true, error = null)
        }
        viewModelScope.launch {
            connectionRepository.connect(host)
                .onSuccess { _events.send(ConnectEvent.Connected) }
                .onFailure { error -> _state.update { it.copy(error = error.toUiText()) } }
            _state.update { it.copy(isConnecting = false) }
        }
    }

    private companion object {
        val PORT_RANGE = 1..65_535
    }
}
