package com.greenfodor.ppremotece.core.data.mapper

import assertk.assertThat
import assertk.assertions.containsExactly
import com.greenfodor.ppremotece.core.data.dto.AudioPlaylistDto
import com.greenfodor.ppremotece.core.data.dto.AudioTrackDto
import com.greenfodor.ppremotece.core.data.dto.IdDto
import org.junit.jupiter.api.Test

class AudioMapperTest {
    @Test
    fun `track names are shown without their audio extension`() {
        val playlist = AudioPlaylistDto(
            id = IdDto("pl", "Audio Playlist 01", 0),
            items = listOf(
                AudioTrackDto(IdDto("t-0", "Track 01.mp3", 0), artist = "Artist 01", duration = 183.4),
                AudioTrackDto(IdDto("t-1", "Track 02. Artist 02", 1)),
                AudioTrackDto(IdDto("t-2", "Track 03.WAV", 2))
            )
        )

        assertThat(playlist.toTracks().map { it.name }).containsExactly("Track 01", "Track 02. Artist 02", "Track 03")
    }
}
