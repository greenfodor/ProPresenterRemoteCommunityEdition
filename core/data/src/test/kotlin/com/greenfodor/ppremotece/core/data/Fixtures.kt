package com.greenfodor.ppremotece.core.data

import com.greenfodor.ppremotece.core.data.dto.PlaylistDto
import com.greenfodor.ppremotece.core.data.dto.PlaylistTreeNodeDto
import com.greenfodor.ppremotece.core.data.dto.PresentationResponseDto
import com.greenfodor.ppremotece.core.data.network.ProPresenterJson

object Fixtures {
    const val ARRANGEMENT_TEST_PLAYLIST = "playlist-6f760dbf.json"
    const val SERVICE_PLAYLIST = "playlist-065f53c3.json"
    const val STAGE_10_PLAYLIST = "playlist-stage10.json"
    const val SONG_A = "presentation-08672906.json"
    const val SONG_B = "presentation-ae707301.json"
    const val PLACEHOLDER_SONG = "presentation-1d6c5bd9.json"
    const val SONG_C = "presentation-44a66b34.json"
    const val PLAYLIST_TREE = "playlists.json"
    const val LIBRARIES = "libraries.json"
    const val LIBRARY_ID = "77f0d0b5-95e5-4767-9081-c7a4d8b3b622"
    const val LIBRARY = "library-77f0d0b5.json"

    fun text(name: String): String =
        requireNotNull(javaClass.getResource("/fixtures/$name")) { "missing fixture $name" }.readText()

    fun bytes(name: String): ByteArray =
        requireNotNull(javaClass.getResource("/fixtures/$name")) { "missing fixture $name" }.readBytes()

    fun playlist(name: String): PlaylistDto = ProPresenterJson.decodeFromString(text(name))

    fun presentation(name: String): PresentationResponseDto = ProPresenterJson.decodeFromString(text(name))

    fun playlistTree(): List<PlaylistTreeNodeDto> = ProPresenterJson.decodeFromString(text(PLAYLIST_TREE))
}
