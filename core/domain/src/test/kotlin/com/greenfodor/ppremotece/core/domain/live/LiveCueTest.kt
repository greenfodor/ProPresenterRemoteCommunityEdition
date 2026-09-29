package com.greenfodor.ppremotece.core.domain.live

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.greenfodor.ppremotece.core.domain.model.ConnectionStatus
import com.greenfodor.ppremotece.core.domain.model.Cue
import com.greenfodor.ppremotece.core.domain.model.LiveSlide
import com.greenfodor.ppremotece.core.domain.model.LiveState
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import org.junit.jupiter.api.Test

class LiveCueTest {
    private val item = PlaylistItemKey("pl-1", 6)
    private val cues = (0..7).map { cue(it, enabled = it != 1 && it != 6) }

    @Test
    fun `live cue is marked only for the live item and its presentation`() {
        val live = liveAt(3)

        assertThat(liveCueIndex(live, item, presentationUuid = "p-1")).isEqualTo(3)
        assertThat(liveCueIndex(live, PlaylistItemKey("pl-1", 0), presentationUuid = "p-1")).isNull()
        assertThat(liveCueIndex(live, item, presentationUuid = "p-2")).isNull()
        assertThat(liveCueIndex(live.copy(slide = null), item, presentationUuid = "p-1")).isNull()
    }

    @Test
    fun `next is the cue after the live one`() {
        assertThat(nextCueIndex(liveAt(2), item, "p-1", cues)).isEqualTo(3)
    }

    @Test
    fun `next skips a disabled cue`() {
        assertThat(nextCueIndex(liveAt(0), item, "p-1", cues)).isEqualTo(2)
        assertThat(nextCueIndex(liveAt(5), item, "p-1", cues)).isEqualTo(7)
    }

    @Test
    fun `there is no next on the last cue`() {
        assertThat(nextCueIndex(liveAt(7), item, "p-1", cues)).isNull()
    }

    @Test
    fun `there is no next when only disabled cues follow`() {
        val lastDisabled = cues.map { if (it.index == 7) it.copy(enabled = false) else it }

        assertThat(nextCueIndex(liveAt(6), item, "p-1", lastDisabled)).isNull()
    }

    @Test
    fun `there is no next when the item is not live`() {
        assertThat(nextCueIndex(liveAt(2).copy(slide = null), item, "p-1", cues)).isNull()
        assertThat(nextCueIndex(liveAt(2).copy(item = null), item, "p-1", cues)).isNull()
    }

    @Test
    fun `there is no next for another item`() {
        assertThat(nextCueIndex(liveAt(2), PlaylistItemKey("pl-1", 5), "p-1", cues)).isNull()
        assertThat(nextCueIndex(liveAt(2), item, "p-2", cues)).isNull()
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
            size = null
        )
}
