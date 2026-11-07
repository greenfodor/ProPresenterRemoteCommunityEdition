package com.greenfodor.ppremotece.core.domain.audio

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class TrackDisplayNameTest {
    private val extensions = listOf(
        "mp3",
        "m4a",
        "m4b",
        "aac",
        "wav",
        "wave",
        "aif",
        "aiff",
        "aifc",
        "flac",
        "ogg",
        "oga",
        "opus",
        "wma",
        "caf"
    )

    private fun mixedCase(extension: String) =
        extension.mapIndexed { i, c -> if (i % 2 == 0) c.uppercaseChar() else c }.joinToString("")

    @Test
    fun `each known audio extension is removed in lower case`() {
        assertThat(extensions.map { trackDisplayName("Track 01.$it") }.toSet()).containsOnly("Track 01")
    }

    @Test
    fun `each known audio extension is removed in upper case`() {
        assertThat(extensions.map { trackDisplayName("Track 01.${it.uppercase()}") }.toSet()).containsOnly("Track 01")
    }

    @Test
    fun `each known audio extension is removed in mixed case`() {
        assertThat(extensions.map { trackDisplayName("Track 01.${mixedCase(it)}") }.toSet()).containsOnly("Track 01")
    }

    @Test
    fun `only the last extension is removed and an inner dot is kept`() {
        assertThat(trackDisplayName("Track 01. Artist 01.mp3")).isEqualTo("Track 01. Artist 01")
        assertThat(trackDisplayName("Track 01.mp3.mp3")).isEqualTo("Track 01.mp3")
    }

    @Test
    fun `a name with an inner dot and no extension is unchanged`() {
        assertThat(trackDisplayName("Track 01. Artist 01")).isEqualTo("Track 01. Artist 01")
    }

    @Test
    fun `a name without an extension is unchanged`() {
        assertThat(trackDisplayName("Track 01")).isEqualTo("Track 01")
    }

    @Test
    fun `an unknown extension is kept`() {
        val names = listOf("Track 01.mp4", "Track 01.txt", "Track 01mp3", "Track 01.")

        assertThat(names.map(::trackDisplayName)).isEqualTo(names)
    }

    @Test
    fun `a name that is only an extension is unchanged`() {
        assertThat(trackDisplayName(".mp3")).isEqualTo(".mp3")
        assertThat(trackDisplayName(".MP3")).isEqualTo(".MP3")
        assertThat(trackDisplayName("")).isEqualTo("")
    }
}
