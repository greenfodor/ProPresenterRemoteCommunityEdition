package com.greenfodor.ppremotece.feature.playlist

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object PlaylistsRoute : NavKey

@Serializable
data class SlideGridRoute(
    val playlistUuid: String,
    val itemIndex: Int
) : NavKey
