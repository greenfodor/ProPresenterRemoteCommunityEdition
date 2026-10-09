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
 * route allows it, the auto-connect setting is on and the last disconnect did not ask to stay
 * disconnected, connects to the saved host on start; otherwise the saved host's address and port
 * fill the empty fields, and connecting to them keeps the saved host's name. The permission is
 * requested on start and again on each connect attempt while it is missing; a failed auto-connect
 * leaves its address and port filled in. The saved host is the last-used card, and each attempt
 * names the card or form it was started from ([ConnectState.target]). Manual entry starts open
 * while there is neither a saved nor a discovered host, and opens when the search fails.
 * [ConnectEvent.StartupResolved] is sent once: at once when no auto-connect is started or the
 * permission has to be requested first, else after the auto-connect has succeeded or failed.
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
    private var pendingTarget: ConnectTarget? = null
    private var startupResolved = false
    private var savedHost: ProPresenterHost? = null

    fun onAction(action: ConnectAction) {
        when (action) {
            is ConnectAction.OnStart -> start(action.permissionGranted, action.autoConnect)
            is ConnectAction.OnPermissionResult -> onPermissionResult(action.granted)
            is ConnectAction.OnAddressChange -> _state.update { it.copy(address = action.address, error = null) }
            is ConnectAction.OnPortChange -> _state.update {
                it.copy(port = action.port.filter(Char::isDigit), error = null)
            }
            ConnectAction.OnConnectClick -> connectToEnteredHost()
            ConnectAction.OnSavedHostClick -> savedHost?.let { connect(it, ConnectTarget.LastUsed) }
            ConnectAction.OnManualToggle -> _state.update { it.copy(manualOpen = !it.manualOpen) }
            is ConnectAction.OnDiscoveredHostClick -> connect(action.host, ConnectTarget.Discovered(action.host))
        }
    }

    private fun start(granted: Boolean, autoConnect: Boolean) {
        if (started) return
        started = true
        permissionGranted = granted
        if (granted) startDiscovery()
        viewModelScope.launch {
            val host = connectionRepository.savedHost()
            savedHost = host
            _state.update {
                it.copy(
                    savedHost = host,
                    manualOpen = it.manualOpen || (host == null && it.discoveredHosts.isEmpty())
                )
            }
            if (host != null && autoConnect && autoConnectAllowed()) {
                connect(host, ConnectTarget.LastUsed)
                return@launch
            }
            if (host != null) prefill(host)
            resolveStartup()
            if (!granted) _events.send(ConnectEvent.RequestLocalNetworkPermission)
        }
    }

    private suspend fun autoConnectAllowed(): Boolean =
        appPreferences.autoConnect().first() && !connectionRepository.stayDisconnected()

    private fun onPermissionResult(granted: Boolean) {
        permissionGranted = granted
        val host = pendingHost
        val target = pendingTarget
        pendingHost = null
        pendingTarget = null
        if (granted) {
            startDiscovery()
            if (host != null && target != null) connect(host, target)
        }
    }

    private fun startDiscovery() {
        if (discoveryStarted) return
        discoveryStarted = true
        _state.update { it.copy(discovery = DiscoveryStatus.SEARCHING) }
        viewModelScope.launch {
            hostDiscovery.discoveredHosts()
                .catch { _state.update { it.copy(discovery = DiscoveryStatus.FAILED, manualOpen = true) } }
                .collect { hosts -> _state.update { it.copy(discoveredHosts = hosts) } }
        }
    }

    private fun connectToEnteredHost() {
        val address = _state.value.address.trim()
        val port = _state.value.port.toIntOrNull()
        when {
            address.isEmpty() -> _state.update {
                it.copy(error = UiText.StringResource(R.string.connect_error_address), target = ConnectTarget.Manual)
            }
            port == null || port !in PORT_RANGE -> _state.update {
                it.copy(error = UiText.StringResource(R.string.connect_error_port), target = ConnectTarget.Manual)
            }
            else -> connect(
                savedHost?.takeIf { it.address == address && it.port == port }
                    ?: ProPresenterHost(name = address, address = address, port = port),
                ConnectTarget.Manual
            )
        }
    }

    private fun prefill(host: ProPresenterHost) {
        _state.update {
            if (it.address.isEmpty() && it.port.isEmpty() && !it.isConnecting) {
                it.copy(address = host.address, port = host.port.toString())
            } else {
                it
            }
        }
    }

    private fun connect(host: ProPresenterHost, target: ConnectTarget) {
        if (_state.value.isConnecting) return
        _state.update {
            it.copy(address = host.address, port = host.port.toString(), error = null, target = target)
        }
        if (!permissionGranted) {
            pendingHost = host
            pendingTarget = target
            viewModelScope.launch {
                resolveStartup()
                _events.send(ConnectEvent.RequestLocalNetworkPermission)
            }
            return
        }
        _state.update { it.copy(isConnecting = true) }
        viewModelScope.launch {
            connectionRepository.connect(host)
                .onSuccess { _events.send(ConnectEvent.Connected) }
                .onFailure { error -> _state.update { it.copy(error = error.toUiText()) } }
            _state.update { it.copy(isConnecting = false) }
            resolveStartup()
        }
    }

    private suspend fun resolveStartup() {
        if (startupResolved) return
        startupResolved = true
        _events.send(ConnectEvent.StartupResolved)
    }

    private companion object {
        val PORT_RANGE = 1..65_535
    }
}
