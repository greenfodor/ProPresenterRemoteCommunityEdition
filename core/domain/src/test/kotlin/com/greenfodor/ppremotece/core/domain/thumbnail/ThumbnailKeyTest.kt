package com.greenfodor.ppremotece.core.domain.thumbnail

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.matches
import com.greenfodor.ppremotece.core.domain.model.Cue
import com.greenfodor.ppremotece.core.domain.model.Group
import com.greenfodor.ppremotece.core.domain.model.Presentation
import com.greenfodor.ppremotece.core.domain.model.Slide
import com.greenfodor.ppremotece.core.domain.model.SlideSize
import org.junit.jupiter.api.Test

class ThumbnailKeyTest {
    private val size = SlideSize(width = 1920, height = 858)
    private val chorusFirst = cue(index = 1, slideIndexInGroup = 0, text = "Chorus · 1")
    private val chorusFirstRepeat = chorusFirst.copy(index = 5)

    @Test
    fun `key has the documented layout`() {
        val key = ThumbnailKey.of("Host 01", "p-1", chorusFirst)

        assertThat(key).matches(Regex("^thumb:v1:Host 01:p-1:g-chorus:0:[0-9a-f]{40}$"))
    }

    @Test
    fun `repeats of a group slide share one key`() {
        assertThat(ThumbnailKey.of("Host 01", "p-1", chorusFirstRepeat))
            .isEqualTo(ThumbnailKey.of("Host 01", "p-1", chorusFirst))
    }

    @Test
    fun `a text change changes the key`() {
        assertThat(ThumbnailKey.of("Host 01", "p-1", chorusFirst.copy(slideText = "Chorus · 1 edited")))
            .isNotEqualTo(ThumbnailKey.of("Host 01", "p-1", chorusFirst))
    }

    @Test
    fun `a size change changes the key`() {
        assertThat(ThumbnailKey.of("Host 01", "p-1", chorusFirst.copy(size = SlideSize(1920, 1080))))
            .isNotEqualTo(ThumbnailKey.of("Host 01", "p-1", chorusFirst))
    }

    @Test
    fun `the host is part of the key`() {
        assertThat(ThumbnailKey.of("Host 02", "p-1", chorusFirst))
            .isNotEqualTo(ThumbnailKey.of("Host 01", "p-1", chorusFirst))
    }

    @Test
    fun `the digest is sha1 of text, width and height`() {
        val key = ThumbnailKey.of("Host 01", "p-1", cue(index = 0, slideIndexInGroup = 0, text = "abc"))

        assertThat(key.substringAfterLast(':')).isEqualTo(sha1Hex("abc" + "1920" + "858"))
    }

    @Test
    fun `aspect follows the slide size`() {
        assertThat(slideAspect(presentationOf(SlideSize(1920, 858)))).isCloseTo(1920f / 858f, TOLERANCE)
    }

    @Test
    fun `aspect is clamped to 4 by 3 and 3 by 1`() {
        assertThat(slideAspect(presentationOf(SlideSize(1000, 1000)))).isCloseTo(4f / 3f, TOLERANCE)
        assertThat(slideAspect(presentationOf(SlideSize(4000, 1000)))).isCloseTo(3f, TOLERANCE)
    }

    @Test
    fun `aspect falls back to 16 by 9 without a size`() {
        assertThat(slideAspect(presentationOf(null))).isCloseTo(16f / 9f, TOLERANCE)
        assertThat(slideAspect(presentationOf(SlideSize(1920, 0)))).isCloseTo(16f / 9f, TOLERANCE)
        assertThat(slideAspect(Presentation("p-1", "Song", emptyList(), emptyList()))).isCloseTo(16f / 9f, TOLERANCE)
    }

    private fun cue(index: Int, slideIndexInGroup: Int, text: String) =
        Cue(
            index = index,
            groupUuid = "g-chorus",
            groupName = "Chorus",
            groupColor = null,
            slideIndexInGroup = slideIndexInGroup,
            slideText = text,
            enabled = true,
            size = size
        )

    private fun presentationOf(size: SlideSize?) =
        Presentation(
            uuid = "p-1",
            name = "Song",
            groups = listOf(Group("g-1", "Verse 1", null, listOf(Slide("V1", size = size)))),
            arrangements = emptyList()
        )

    private fun sha1Hex(value: String): String =
        java.security.MessageDigest.getInstance("SHA-1").digest(value.toByteArray()).joinToString("") {
            "%02x".format(it)
        }

    private companion object {
        const val TOLERANCE = 0.001f
    }
}
