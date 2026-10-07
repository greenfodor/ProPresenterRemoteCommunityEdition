package com.greenfodor.ppremotece.core.domain.live

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.greenfodor.ppremotece.core.domain.model.ConnectionStatus
import com.greenfodor.ppremotece.core.domain.model.Cue
import com.greenfodor.ppremotece.core.domain.model.CueSource
import com.greenfodor.ppremotece.core.domain.model.LiveSlide
import com.greenfodor.ppremotece.core.domain.model.LiveState
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import org.junit.jupiter.api.Test

class LiveCueTest {
    private val item = PlaylistItemKey("pl-1", 6)
    private val source = CueSource.PlaylistItem(item)
    private val cues = (0..7).map { cue(it, enabled = it != 1 && it != 6) }

    @Test
    fun `live cue is marked only for the live item and its presentation`() {
        val live = liveAt(3)

        assertThat(liveCueIndex(live, source, presentationUuid = "p-1", cues)).isEqualTo(3)
        assertThat(
            liveCueIndex(live, CueSource.PlaylistItem(PlaylistItemKey("pl-1", 0)), presentationUuid = "p-1", cues)
        ).isNull()
        assertThat(liveCueIndex(live, source, presentationUuid = "p-2", cues)).isNull()
        assertThat(liveCueIndex(live.copy(slide = null), source, presentationUuid = "p-1", cues)).isNull()
    }

    @Test
    fun `next is the cue after the live one`() {
        assertThat(markedCue(liveAt(2), lastLive = null, source, "p-1", cues)?.next).isEqualTo(3)
    }

    @Test
    fun `next skips a disabled cue`() {
        assertThat(markedCue(liveAt(0), lastLive = null, source, "p-1", cues)?.next).isEqualTo(2)
        assertThat(markedCue(liveAt(5), lastLive = null, source, "p-1", cues)?.next).isEqualTo(7)
    }

    @Test
    fun `there is no next on the last cue`() {
        assertThat(markedCue(liveAt(7), lastLive = null, source, "p-1", cues)?.next).isNull()
    }

    @Test
    fun `there is no next when only disabled cues follow`() {
        val lastDisabled = cues.map { if (it.index == 7) it.copy(enabled = false) else it }

        assertThat(markedCue(liveAt(6), lastLive = null, source, "p-1", lastDisabled)?.next).isNull()
    }

    @Test
    fun `there is no next when the item is not live`() {
        assertThat(markedCue(liveAt(2).copy(slide = null), lastLive = null, source, "p-1", cues)?.next).isNull()
        assertThat(markedCue(liveAt(2).copy(item = null), lastLive = null, source, "p-1", cues)?.next).isNull()
    }

    @Test
    fun `there is no next for another item`() {
        assertThat(
            markedCue(liveAt(2), lastLive = null, CueSource.PlaylistItem(PlaylistItemKey("pl-1", 5)), "p-1", cues)?.next
        ).isNull()
        assertThat(markedCue(liveAt(2), lastLive = null, source, "p-2", cues)?.next).isNull()
    }

    @Test
    fun `a presentation is live only outside a playlist with its cue count`() {
        val presentation = CueSource.Presentation("p-1")
        val outside = liveAt(3).copy(item = null)

        assertThat(liveCueIndex(outside, presentation, "p-1", cues)).isEqualTo(3)
        assertThat(markedCue(outside, lastLive = null, presentation, "p-1", cues)?.next).isEqualTo(4)
        assertThat(liveCueIndex(liveAt(3), presentation, "p-1", cues)).isNull()
        assertThat(liveCueIndex(outside, CueSource.Presentation("p-2"), "p-2", cues)).isNull()
        assertThat(liveCueIndex(outside, presentation, "p-1", cues.take(7))).isNull()
    }

    @Test
    fun `next and previous over a cue list skip disabled cues`() {
        assertThat(nextCueIndex(cues, after = 0)).isEqualTo(2)
        assertThat(nextCueIndex(cues, after = 5)).isEqualTo(7)
        assertThat(previousCueIndex(cues, before = 2)).isEqualTo(0)
        assertThat(previousCueIndex(cues, before = 7)).isEqualTo(5)
    }

    @Test
    fun `no next after the last cue and no previous before the first`() {
        assertThat(nextCueIndex(cues, after = 7)).isNull()
        assertThat(previousCueIndex(cues, before = 0)).isNull()
        assertThat(previousCueIndex(cues.map { it.copy(enabled = it.index != 0) }, before = 1)).isNull()
    }

    private fun liveAt(index: Int) =
        LiveState(ConnectionStatus.CONNECTED, item, LiveSlide(presentationUuid = "p-1", index = index, totalCues = 8))

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
