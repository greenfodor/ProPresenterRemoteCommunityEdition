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

/** The items of a playlist, in the list pane. */
@Serializable
data class PlaylistRoute(
    val playlistUuid: String
) : NavKey

/** The card of a media, audio or live-video playlist item. */
@Serializable
data class PlaylistItemRoute(
    val playlistUuid: String,
    val itemIndex: Int
) : NavKey

/** The slide grid of a library presentation, in its current arrangement. */
@Serializable
data class LibraryGridRoute(
    val presentationUuid: String
) : NavKey
