package com.greenfodor.ppremotece.feature.settings

import com.greenfodor.ppremotece.core.domain.settings.KeepAwake

/**
 * The Settings screen: the keep-awake mode, the auto-connect switch, the connected host's name,
 * `address:port` and description (empty while disconnected), and whether the Disconnect dialog is shown.
 */
data class SettingsState(
    val keepAwake: KeepAwake = KeepAwake.Default,
    val autoConnect: Boolean = true,
    val hostName: String = "",
    val hostAddress: String = "",
    val hostDescription: String = "",
    val confirmingDisconnect: Boolean = false
)

sealed interface SettingsAction {
    data class OnKeepAwakeChange(
        val mode: KeepAwake
    ) : SettingsAction

    data class OnAutoConnectChange(
        val enabled: Boolean
    ) : SettingsAction

    data object OnDisconnectClick : SettingsAction

    data object OnDisconnectConfirm : SettingsAction

    data object OnDisconnectDismiss : SettingsAction
}

sealed interface SettingsEvent {
    data object Disconnected : SettingsEvent
}
