package com.greenfodor.ppremotece.feature.connect

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The Connect screen; it connects to the saved host on start only when [autoConnect] and the auto-connect setting are on. */
@Serializable
data class ConnectRoute(
    val autoConnect: Boolean = true
) : NavKey
