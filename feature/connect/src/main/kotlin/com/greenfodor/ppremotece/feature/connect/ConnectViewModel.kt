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
import com.greenfodor.ppremotece.core.domain.settings.AppPreferences
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Connect screen: browses for hosts once the local network permission is granted and, when the
 * route allows it and the auto-connect setting is on, connects to the saved host on start;
 * otherwise the saved host's address and port are filled in. The permission is requested on start and again on each connect attempt
 * while it is missing; a failed auto-connect leaves its address and port filled in.
 */
class ConnectViewModel(
    private val connectionRepository: ConnectionRepository,
    private val hostDiscovery: HostDiscovery,
    private val appPreferences: AppPreferences
) : ViewModel() {
    private val _state = MutableStateFlow(ConnectState())
    val state = _state.asStateFlow()

    private val _events = Channel<ConnectEvent>()
    val events = _events.receiveAsFlow()

    private var started = false
    private var permissionGranted = false
    private var discoveryStarted = false
    private var pendingHost: ProPresenterHost? = null

    fun onAction(action: ConnectAction) {
        when (action) {
            is ConnectAction.OnStart -> start(action.permissionGranted, action.autoConnect)
            is ConnectAction.OnPermissionResult -> onPermissionResult(action.granted)
            is ConnectAction.OnAddressChange -> _state.update { it.copy(address = action.address, error = null) }
            is ConnectAction.OnPortChange -> _state.update {
                it.copy(port = action.port.filter(Char::isDigit), error = null)
            }
            ConnectAction.OnConnectClick -> connectToEnteredHost()
            is ConnectAction.OnDiscoveredHostClick -> connect(action.host)
        }
    }

    private fun start(granted: Boolean, autoConnect: Boolean) {
        if (started) return
        started = true
        permissionGranted = granted
        if (granted) startDiscovery()
        viewModelScope.launch {
            val savedHost = connectionRepository.savedHost()
            if (savedHost != null && autoConnect && appPreferences.autoConnect().first()) {
                connect(savedHost)
                return@launch
            }
            savedHost?.let { host -> _state.update { it.copy(address = host.address, port = host.port.toString()) } }
            if (!granted) _events.send(ConnectEvent.RequestLocalNetworkPermission)
        }
    }

    private fun onPermissionResult(granted: Boolean) {
        permissionGranted = granted
        val host = pendingHost
        pendingHost = null
        if (granted) {
            startDiscovery()
            host?.let(::connect)
        }
    }

    private fun startDiscovery() {
        if (discoveryStarted) return
        discoveryStarted = true
        _state.update { it.copy(discovery = DiscoveryStatus.SEARCHING) }
        viewModelScope.launch {
            hostDiscovery.discoveredHosts()
                .catch { _state.update { it.copy(discovery = DiscoveryStatus.FAILED) } }
                .collect { hosts -> _state.update { it.copy(discoveredHosts = hosts) } }
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
        _state.update { it.copy(address = host.address, port = host.port.toString(), error = null) }
        if (!permissionGranted) {
            pendingHost = host
            viewModelScope.launch { _events.send(ConnectEvent.RequestLocalNetworkPermission) }
            return
        }
        _state.update { it.copy(isConnecting = true) }
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
