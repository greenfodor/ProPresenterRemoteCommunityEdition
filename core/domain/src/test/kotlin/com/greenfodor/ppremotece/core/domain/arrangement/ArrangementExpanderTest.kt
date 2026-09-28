package com.greenfodor.ppremotece.core.domain.arrangement

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.greenfodor.ppremotece.core.domain.model.Arrangement
import com.greenfodor.ppremotece.core.domain.model.Cue
import com.greenfodor.ppremotece.core.domain.model.Group
import com.greenfodor.ppremotece.core.domain.model.GroupColor
import com.greenfodor.ppremotece.core.domain.model.Presentation
import com.greenfodor.ppremotece.core.domain.model.PresentationRef
import com.greenfodor.ppremotece.core.domain.model.Slide
import com.greenfodor.ppremotece.core.domain.model.SlideSize
import org.junit.jupiter.api.Test

class ArrangementExpanderTest {
    private val red = GroupColor(red = 1f, green = 0f, blue = 0f, alpha = 1f)
    private val verse = group("g-verse", "Verse 1", "V1", "V2", color = red)
    private val chorus = group("g-chorus", "Chorus", "C1", "C2")
    private val bridge = group("g-bridge", "Bridge", "B1")

    private val full = Arrangement("a-full", "Full", listOf("g-verse", "g-chorus", "g-bridge"), totalCues = 5)
    private val chorusTwice = Arrangement("a-twice", "Twice", listOf("g-chorus", "g-bridge", "g-chorus"), totalCues = 5)
    private val placeholder = Arrangement("a-none", "", emptyList(), totalCues = 0)
    private val unnamed = Arrangement("a-unnamed", "", listOf("g-bridge"), totalCues = 1)

    private val presentation = Presentation(
        uuid = "p-1",
        name = "Song",
        groups = listOf(verse, chorus, bridge),
        arrangements = listOf(full, chorusTwice, placeholder, unnamed)
    )

    @Test
    fun `resolved arrangement expands its groups over their slides in order`() {
        val result = ArrangementExpander.expand(presentation, ref("a-full", "Full"))

        assertThat(result.choice).isEqualTo(ArrangementChoice.Resolved(full))
        assertThat(result.cues.map { it.slideText }).containsExactly("V1", "V2", "C1", "C2", "B1")
        assertThat(result.cues.map { it.index }).containsExactly(0, 1, 2, 3, 4)
        assertThat(result.cues.first()).isEqualTo(
            Cue(
                index = 0,
                groupUuid = "g-verse",
                groupName = "Verse 1",
                groupColor = red,
                slideIndexInGroup = 0,
                slideText = "V1",
                enabled = true,
                size = null
            )
        )
        assertThat(result.cues.map { it.slideIndexInGroup }).containsExactly(0, 1, 0, 1, 0)
        assertThat(result.countMismatch).isFalse()
    }

    @Test
    fun `disabled slide keeps its cue index and carries its size`() {
        val size = SlideSize(width = 1920, height = 858)
        val withDisabled = Group(
            uuid = "g-verse",
            name = "Verse 1",
            color = null,
            slides = listOf(
                Slide("V1", size = size),
                Slide("V2", enabled = false, size = size),
                Slide("V3", size = size)
            )
        )
        val presentation = Presentation(
            uuid = "p-3",
            name = "Song",
            groups = listOf(withDisabled, chorus),
            arrangements = listOf(Arrangement("a-a", "A", listOf("g-verse", "g-chorus", "g-verse"), totalCues = 8))
        )

        val result = ArrangementExpander.expand(presentation, ref("a-a", "A"))

        assertThat(result.cues.size).isEqualTo(8)
        assertThat(result.cues.filterNot { it.enabled }.map { it.index }).containsExactly(1, 6)
        assertThat(result.cues[1].slideText).isEqualTo("V2")
        assertThat(result.cues[1].size).isEqualTo(size)
        assertThat(result.countMismatch).isFalse()
    }

    @Test
    fun `dangling group uuid is skipped and flags a count mismatch`() {
        val dangling = Arrangement("a-dangling", "Dangling", listOf("g-verse", "g-gone", "g-bridge"), totalCues = 5)
        val presentation = presentation.copy(arrangements = listOf(dangling))

        val result = ArrangementExpander.expand(presentation, ref("a-dangling", "Dangling"))

        assertThat(result.cues.map { it.slideText }).containsExactly("V1", "V2", "B1")
        assertThat(result.countMismatch).isTrue()
    }

    @Test
    fun `song order never flags a count mismatch`() {
        val result = ArrangementExpander.expand(presentation, ref("a-none", ""))

        assertThat(result.countMismatch).isFalse()
    }

    @Test
    fun `repeated groups produce repeated cues with running indices`() {
        val result = ArrangementExpander.expand(presentation, ref("a-twice", "Twice"))

        assertThat(result.cues.map { it.groupName })
            .containsExactly("Chorus", "Chorus", "Bridge", "Chorus", "Chorus")
        assertThat(result.cues.map { it.index }).containsExactly(0, 1, 2, 3, 4)
    }

    @Test
    fun `placeholder arrangement with no groups falls back to raw group order`() {
        val result = ArrangementExpander.expand(presentation, ref("a-none", ""))

        assertThat(result.choice).isEqualTo(ArrangementChoice.SongOrder)
        assertThat(result.cues.map { it.slideText }).containsExactly("V1", "V2", "C1", "C2", "B1")
    }

    @Test
    fun `empty arrangement uuid falls back to raw group order`() {
        val result = ArrangementExpander.expand(presentation, ref("", ""))

        assertThat(result.choice).isEqualTo(ArrangementChoice.SongOrder)
        assertThat(result.cues.map { it.slideText }).containsExactly("V1", "V2", "C1", "C2", "B1")
    }

    @Test
    fun `empty arrangement uuid ignores the arrangement name`() {
        val result = ArrangementExpander.expand(presentation, ref("", "Full"))

        assertThat(result.choice).isEqualTo(ArrangementChoice.SongOrder)
        assertThat(result.cues.map { it.slideText }).containsExactly("V1", "V2", "C1", "C2", "B1")
    }

    @Test
    fun `unknown uuid falls back to the arrangement with the same name`() {
        val result = ArrangementExpander.expand(presentation, ref("a-missing", "Twice"))

        assertThat(result.choice).isEqualTo(ArrangementChoice.Resolved(chorusTwice))
        assertThat(result.cues.size).isEqualTo(5)
    }

    @Test
    fun `unknown uuid and unknown name fall back to raw group order`() {
        val result = ArrangementExpander.expand(presentation, ref("a-missing", "Missing"))

        assertThat(result.choice).isEqualTo(ArrangementChoice.SongOrder)
    }

    @Test
    fun `unnamed arrangement with groups stays resolved`() {
        val result = ArrangementExpander.expand(presentation, ref("a-unnamed", ""))

        assertThat(result.choice).isEqualTo(ArrangementChoice.Resolved(unnamed))
        assertThat(result.cues.map { it.slideText }).containsExactly("B1")
    }

    @Test
    fun `arrangement uuid shared across presentations resolves inside the given presentation`() {
        val other = Presentation(
            uuid = "p-2",
            name = "Other song",
            groups = listOf(group("g-other", "Tag", "T1", "T2")),
            arrangements = listOf(Arrangement("a-full", "Tag only", listOf("g-other"), totalCues = 2))
        )
        val sharedRef = ref("a-full", "Full")

        val first = ArrangementExpander.expand(presentation, sharedRef)
        val second = ArrangementExpander.expand(other, sharedRef)

        assertThat(first.cues.size).isEqualTo(5)
        assertThat(second.choice).isInstanceOf<ArrangementChoice.Resolved>()
        assertThat(second.cues.map { it.slideText }).containsExactly("T1", "T2")
    }

    private fun group(uuid: String, name: String, vararg texts: String, color: GroupColor? = null) =
        Group(uuid = uuid, name = name, color = color, slides = texts.map { Slide(text = it) })

    private fun ref(arrangementUuid: String, arrangementName: String) =
        PresentationRef(presentationUuid = "p-1", arrangementUuid = arrangementUuid, arrangementName = arrangementName)
}
