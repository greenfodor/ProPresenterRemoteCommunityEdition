package com.greenfodor.ppremotece.core.data

import com.greenfodor.ppremotece.core.data.dto.PlaylistDto
import com.greenfodor.ppremotece.core.data.dto.PlaylistTreeNodeDto
import com.greenfodor.ppremotece.core.data.dto.PresentationResponseDto
import com.greenfodor.ppremotece.core.data.network.ProPresenterJson

object Fixtures {
    const val ARRANGEMENT_TEST_PLAYLIST = "playlist-6f760dbf.json"
    const val SERVICE_PLAYLIST = "playlist-065f53c3.json"
    const val SONG_A = "presentation-08672906.json"
    const val SONG_B = "presentation-ae707301.json"
    const val PLAYLIST_TREE = "playlists.json"

    fun text(name: String): String =
        requireNotNull(javaClass.getResource("/fixtures/$name")) { "missing fixture $name" }.readText()

    fun playlist(name: String): PlaylistDto = ProPresenterJson.decodeFromString(text(name))

    fun presentation(name: String): PresentationResponseDto = ProPresenterJson.decodeFromString(text(name))

    fun playlistTree(): List<PlaylistTreeNodeDto> = ProPresenterJson.decodeFromString(text(PLAYLIST_TREE))
}
