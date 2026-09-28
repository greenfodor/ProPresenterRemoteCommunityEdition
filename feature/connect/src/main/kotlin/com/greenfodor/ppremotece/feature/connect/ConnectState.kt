package com.greenfodor.ppremotece.feature.connect

import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.model.ProPresenterHost

data class ConnectState(
    val address: String = "",
    val port: String = "",
    val discoveredHosts: List<ProPresenterHost> = emptyList(),
    val isConnecting: Boolean = false,
    val error: UiText? = null
)

sealed interface ConnectAction {
    data class OnPermissionResult(
        val granted: Boolean
    ) : ConnectAction

    data class OnAddressChange(
        val address: String
    ) : ConnectAction

    data class OnPortChange(
        val port: String
    ) : ConnectAction

    data object OnConnectClick : ConnectAction

    data class OnDiscoveredHostClick(
        val host: ProPresenterHost
    ) : ConnectAction
}

sealed interface ConnectEvent {
    data object Connected : ConnectEvent
}
