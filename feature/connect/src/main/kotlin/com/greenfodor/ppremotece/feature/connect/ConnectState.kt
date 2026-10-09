package com.greenfodor.ppremotece.feature.connect

import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.model.ProPresenterHost

data class ConnectState(
    val address: String = "",
    val port: String = "",
    val discovery: DiscoveryStatus = DiscoveryStatus.WAITING_FOR_PERMISSION,
    val discoveredHosts: List<ProPresenterHost> = emptyList(),
    val isConnecting: Boolean = false,
    val error: UiText? = null,
    val savedHost: ProPresenterHost? = null,
    val target: ConnectTarget? = null,
    val manualOpen: Boolean = false
) {
    /** The discovered hosts without the one at the saved host's address and port, which the last-used card shows. */
    val otherHosts: List<ProPresenterHost>
        get() {
            val saved = savedHost ?: return discoveredHosts
            return discoveredHosts.filterNot { it.address == saved.address && it.port == saved.port }
        }
}

/** The card or form a connect attempt was started from: it shows the progress, then the error. */
sealed interface ConnectTarget {
    data object LastUsed : ConnectTarget

    data class Discovered(
        val host: ProPresenterHost
    ) : ConnectTarget

    data object Manual : ConnectTarget
}

enum class DiscoveryStatus {
    WAITING_FOR_PERMISSION,
    SEARCHING,
    FAILED
}

sealed interface ConnectAction {
    data class OnStart(
        val permissionGranted: Boolean,
        val autoConnect: Boolean
    ) : ConnectAction

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

    data object OnSavedHostClick : ConnectAction

    data object OnManualToggle : ConnectAction

    data class OnDiscoveredHostClick(
        val host: ProPresenterHost
    ) : ConnectAction
}

sealed interface ConnectEvent {
    data object RequestLocalNetworkPermission : ConnectEvent

    data object Connected : ConnectEvent

    /** Start-up has nothing left to wait for: no auto-connect was started, or it has succeeded or failed. */
    data object StartupResolved : ConnectEvent
}
