package com.greenfodor.ppremotece.feature.settings

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The list of destinations that do not fit in the bar or rail. */
@Serializable
data object MoreRoute : NavKey

@Serializable
data object SettingsRoute : NavKey
