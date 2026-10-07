package com.greenfodor.ppremotece.core.domain.live

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.greenfodor.ppremotece.core.domain.model.ConnectionStatus
import com.greenfodor.ppremotece.core.domain.model.Cue
import com.greenfodor.ppremotece.core.domain.model.CueSource
import com.greenfodor.ppremotece.core.domain.model.LiveCue
import com.greenfodor.ppremotece.core.domain.model.LiveSlide
import com.greenfodor.ppremotece.core.domain.model.LiveState
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import org.junit.jupiter.api.Test

class CueMarksTest {
    private val item = PlaylistItemKey("pl-1", 6)
    private val source = CueSource.PlaylistItem(item)
    private val otherSource = CueSource.PlaylistItem(PlaylistItemKey("pl-1", 0))
    private val library = CueSource.Presentation("p-1")
    private val cues = (0..7).map { cue(it, enabled = it != 1 && it != 6) }
    private val cleared = LiveState(ConnectionStatus.CONNECTED, item = null, slide = null)

    @Test
    fun `a live cue is marked live with the next enabled cue`() {
        val marked = markedCue(liveAt(0), remembered(source, 0), source, "p-1", cues)

        assertThat(marked).isEqualTo(MarkedCue(index = 0, cleared = false, next = 2))
    }

    @Test
    fun `a cleared slide marks the remembered cue cleared with the next enabled cue`() {
        val marked = markedCue(cleared, remembered(source, 5), source, "p-1", cues)

        assertThat(marked).isEqualTo(MarkedCue(index = 5, cleared = true, next = 7))
    }

    @Test
    fun `the grid of another item has no mark`() {
        assertThat(markedCue(cleared, remembered(source, 5), otherSource, "p-1", cues)).isNull()
        assertThat(markedCue(liveAt(3), remembered(source, 3), otherSource, "p-1", cues)).isNull()
        assertThat(markedCue(cleared, remembered(source, 5), source, "p-2", cues)).isNull()
    }

    @Test
    fun `nothing remembered marks nothing`() {
        assertThat(markedCue(cleared, lastLive = null, source, "p-1", cues)).isNull()
    }

    @Test
    fun `a remembered cue is not marked while another item's slide is live`() {
        val live = liveAt(2).copy(item = otherSource.key)

        assertThat(markedCue(live, remembered(source, 5), source, "p-1", cues)).isNull()
    }

    @Test
    fun `library mode marks the presentation live outside a playlist or remembered from there`() {
        val outside = liveAt(3).copy(item = null)

        assertThat(markedCue(outside, lastLive = null, library, "p-1", cues))
            .isEqualTo(MarkedCue(index = 3, cleared = false, next = 4))
        assertThat(markedCue(cleared, remembered(library, 3), library, "p-1", cues))
            .isEqualTo(MarkedCue(index = 3, cleared = true, next = 4))
        assertThat(markedCue(cleared, remembered(source, 3), library, "p-1", cues)).isNull()
        assertThat(markedCue(cleared, remembered(library, 3), CueSource.Presentation("p-2"), "p-2", cues)).isNull()
    }

    @Test
    fun `before the first report of a connection the remembered cue keeps its live mark`() {
        val marked = markedCue(LiveState.Initial, remembered(source, 5), source, "p-1", cues)

        assertThat(marked).isEqualTo(MarkedCue(index = 5, cleared = false, next = 7))
    }

    @Test
    fun `a remembered cue that is not in the cue list marks nothing`() {
        assertThat(markedCue(cleared, remembered(source, 8), source, "p-1", cues)).isNull()
    }

    @Test
    fun `a cleared cue of a list with a count mismatch steps with trigger next and previous`() {
        val steps = cueSteps(MarkedCue(index = 2, cleared = true, next = 3), source, cues, countMismatch = true)

        assertThat(steps).isEqualTo(CueSteps(next = CueStep.Relative, previous = CueStep.Relative))
    }

    @Test
    fun `a live cue steps with trigger next and previous`() {
        val steps = cueSteps(MarkedCue(index = 3, cleared = false, next = 4), source, cues, countMismatch = false)

        assertThat(steps).isEqualTo(CueSteps(next = CueStep.Relative, previous = CueStep.Relative))
    }

    @Test
    fun `a cleared cue steps to the enabled cues around it`() {
        assertThat(cueSteps(MarkedCue(index = 2, cleared = true, next = 3), source, cues, countMismatch = false))
            .isEqualTo(CueSteps(next = CueStep.Explicit(3), previous = CueStep.Explicit(0)))
        assertThat(cueSteps(MarkedCue(index = 5, cleared = true, next = 7), source, cues, countMismatch = false))
            .isEqualTo(CueSteps(next = CueStep.Explicit(7), previous = CueStep.Explicit(4)))
    }

    @Test
    fun `a cleared first or last cue has no step past the end`() {
        assertThat(
            cueSteps(MarkedCue(index = 0, cleared = true, next = 2), source, cues, countMismatch = false).previous
        )
            .isEqualTo(CueStep.Disabled)
        assertThat(
            cueSteps(MarkedCue(index = 7, cleared = true, next = null), source, cues, countMismatch = false).next
        )
            .isEqualTo(CueStep.Disabled)
    }

    @Test
    fun `with nothing marked a playlist item steps with trigger next and previous and a presentation not at all`() {
        assertThat(cueSteps(marked = null, source, cues, countMismatch = false))
            .isEqualTo(CueSteps(next = CueStep.Relative, previous = CueStep.Relative))
        assertThat(cueSteps(marked = null, library, cues, countMismatch = false))
            .isEqualTo(CueSteps(next = CueStep.Disabled, previous = CueStep.Disabled))
    }

    private fun liveAt(index: Int) =
        LiveState(ConnectionStatus.CONNECTED, item, LiveSlide(presentationUuid = "p-1", index = index, totalCues = 8))

    private fun remembered(source: CueSource, index: Int) = LiveCue(source, presentationUuid = "p-1", cueIndex = index)

    private fun cue(index: Int, enabled: Boolean) =
        Cue(
            index = index,
            groupUuid = "g-1",
            groupName = "Verse 1",
            groupColor = null,
            slideIndexInGroup = 0,
            slideText = "",
            enabled = enabled,
            size = null,
            startsGroup = index == 0
        )
}
