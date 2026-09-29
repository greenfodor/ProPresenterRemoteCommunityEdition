package com.greenfodor.ppremotece.core.domain.remote

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import com.greenfodor.ppremotece.core.domain.arrangement.ArrangementChoice
import com.greenfodor.ppremotece.core.domain.arrangement.ArrangementExpander
import com.greenfodor.ppremotece.core.domain.model.Arrangement
import com.greenfodor.ppremotece.core.domain.model.ConnectionStatus
import com.greenfodor.ppremotece.core.domain.model.Group
import com.greenfodor.ppremotece.core.domain.model.LiveSlide
import com.greenfodor.ppremotece.core.domain.model.LiveState
import com.greenfodor.ppremotece.core.domain.model.PlaylistItem
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemType
import com.greenfodor.ppremotece.core.domain.model.PresentationRef
import com.greenfodor.ppremotece.core.domain.model.Slide
import com.greenfodor.ppremotece.core.domain.model.SlideText
import com.greenfodor.ppremotece.core.domain.remote.RemoteFixtures.PLAYLIST
import com.greenfodor.ppremotece.core.domain.remote.RemoteFixtures.SONG_A
import com.greenfodor.ppremotece.core.domain.remote.RemoteFixtures.SONG_C
import com.greenfodor.ppremotece.core.domain.remote.RemoteFixtures.cleared
import com.greenfodor.ppremotece.core.domain.remote.RemoteFixtures.key
import com.greenfodor.ppremotece.core.domain.remote.RemoteFixtures.liveAt
import com.greenfodor.ppremotece.core.domain.remote.RemoteFixtures.playlist
import com.greenfodor.ppremotece.core.domain.remote.RemoteFixtures.presentations
import com.greenfodor.ppremotece.core.domain.remote.RemoteFixtures.remembered
import com.greenfodor.ppremotece.core.domain.remote.RemoteFixtures.songA
import com.greenfodor.ppremotece.core.domain.remote.RemoteFixtures.songC
import org.junit.jupiter.api.Test

class RemoteDisplayTest {
    private val text = SlideText(current = "Text 03", next = "Text 04")

    private fun reduce(inputs: RemoteInputs) = RemoteDisplay.reduce(inputs, playlist, presentations)

    private fun cue(item: Int, presentation: String, index: Int, mark: BoxMark, thumbnails: Boolean = true) =
        RemoteBox.Slide(
            item = key(item),
            presentationUuid = presentation,
            cue = ArrangementExpander.expand(presentations.getValue(presentation), refOf(item)).cues[index],
            mark = mark,
            thumbnails = thumbnails
        )

    private fun refOf(item: Int) = checkNotNull(playlist.items[item].presentation)

    @Test
    fun `matched live cue shows it with the next enabled cue, the chip and the cue number`() {
        val display = reduce(RemoteInputs(liveAt(0, SONG_A, 2), lastLive = remembered(0, SONG_A, 2)))

        assertThat(display.status).isEqualTo(RemoteStatus.SHOWING)
        assertThat(display.current).isEqualTo(cue(0, SONG_A, 2, BoxMark.LIVE))
        assertThat(display.next).isEqualTo(cue(0, SONG_A, 3, BoxMark.NEXT))
        assertThat(display.header).isEqualTo(
            RemoteHeader("Song A", ArrangementChoice.Resolved(songA.arrangements[0]), cueNumber = 3, cueCount = 5)
        )
        assertThat(display.tapCurrent).isEqualTo(RemoteCommand.TriggerCue(key(0), 2))
        assertThat(display.tapNext).isEqualTo(RemoteCommand.TriggerCue(key(0), 3))
        assertThat(display.nextButton).isEqualTo(RemoteCommand.TriggerCue(key(0), 3))
        assertThat(display.previousButton).isEqualTo(RemoteCommand.TriggerCue(key(0), 1))
        assertThat(display.cued).isFalse()
        assertThat(display.baseItem).isEqualTo(key(0))
    }

    @Test
    fun `next item row names the next item with its arrangement`() {
        val display = reduce(RemoteInputs(liveAt(0, SONG_A, 2), lastLive = null))

        assertThat(display.previousItem).isNull()
        assertThat(display.nextItem).isEqualTo(key(1))
        assertThat(display.nextUp).isEqualTo(
            NextUp(key(1), "Song A", ArrangementChoice.Resolved(songA.arrangements[1]))
        )
        assertThat(display.endOfPlaylist).isFalse()
    }

    @Test
    fun `a live slide of another presentation than the item's shows the stream text without a chip`() {
        val display = reduce(RemoteInputs(liveAt(0, SONG_C, 2, text), lastLive = null))

        assertThat(display.status).isEqualTo(RemoteStatus.SHOWING)
        assertThat(display.current).isEqualTo(RemoteBox.Text("Text 03"))
        assertThat(display.next).isEqualTo(RemoteBox.Text("Text 04"))
        assertThat(display.header).isNull()
        assertThat(display.tapCurrent).isNull()
        assertThat(display.tapNext).isNull()
        assertThat(display.nextItem).isNull()
    }

    @Test
    fun `nothing live since connect shows nothing and sends nothing`() {
        val display = reduce(RemoteInputs(cleared, lastLive = null))

        assertThat(display).isEqualTo(RemoteDisplay(status = RemoteStatus.NOTHING_LIVE))
    }

    @Test
    fun `after a clear the remembered cue is shown and sent as if it were live`() {
        val live = reduce(RemoteInputs(liveAt(0, SONG_A, 2), lastLive = remembered(0, SONG_A, 2)))
        val afterClear = reduce(RemoteInputs(cleared, lastLive = remembered(0, SONG_A, 2)))

        assertThat(afterClear).isEqualTo(live)
        assertThat(afterClear.tapCurrent).isEqualTo(RemoteCommand.TriggerCue(key(0), 2))
        assertThat(afterClear.nextButton).isEqualTo(RemoteCommand.TriggerCue(key(0), 3))
        assertThat(afterClear.previousButton).isEqualTo(RemoteCommand.TriggerCue(key(0), 1))
    }

    @Test
    fun `a cued item shows its cue 0 cued, sends cue 0 and has no previous`() {
        val display = reduce(RemoteInputs(liveAt(0, SONG_A, 2), lastLive = null, cued = key(5)))

        assertThat(display.cued).isTrue()
        assertThat(display.baseItem).isEqualTo(key(0))
        assertThat(display.current).isEqualTo(cue(5, SONG_C, 0, BoxMark.CUED))
        assertThat(display.next).isEqualTo(cue(5, SONG_C, 2, BoxMark.NEXT))
        assertThat(display.header).isEqualTo(
            RemoteHeader("Song C", ArrangementChoice.Resolved(songC.arrangements[0]), cueNumber = 1, cueCount = 8)
        )
        assertThat(display.tapCurrent).isEqualTo(RemoteCommand.TriggerCue(key(5), 0))
        assertThat(display.nextButton).isEqualTo(RemoteCommand.TriggerCue(key(5), 0))
        assertThat(display.tapNext).isEqualTo(RemoteCommand.TriggerCue(key(5), 2))
        assertThat(display.previousButton).isNull()
        assertThat(display.previousItem).isEqualTo(key(4))
        assertThat(display.nextItem).isEqualTo(key(6))
    }

    @Test
    fun `cueing the live item shows its live cue`() {
        val inputs = RemoteInputs(liveAt(0, SONG_A, 2), lastLive = null)

        assertThat(reduce(inputs.copy(cued = key(0)))).isEqualTo(reduce(inputs))
    }

    @Test
    fun `a cued media item shows its card cued and a tap triggers the item`() {
        val display = reduce(RemoteInputs(liveAt(1, SONG_A, 0), lastLive = null, cued = key(4)))

        assertThat(display.current).isEqualTo(RemoteBox.ItemCard("Loop", PlaylistItemType.MEDIA, BoxMark.CUED))
        assertThat(display.tapCurrent).isEqualTo(RemoteCommand.TriggerItem(key(4)))
        assertThat(display.nextButton).isNull()
        assertThat(display.previousButton).isNull()
        assertThat(display.nextItem).isEqualTo(key(5))
    }

    @Test
    fun `the last cue has no next`() {
        val display = reduce(RemoteInputs(liveAt(0, SONG_A, 4), lastLive = null))

        assertThat(display.next).isEqualTo(RemoteBox.Empty)
        assertThat(display.tapNext).isNull()
        assertThat(display.nextButton).isNull()
        assertThat(display.previousButton).isEqualTo(RemoteCommand.TriggerCue(key(0), 3))
    }

    @Test
    fun `cue 0 has no previous`() {
        val display = reduce(RemoteInputs(liveAt(0, SONG_A, 0), lastLive = null))

        assertThat(display.previousButton).isNull()
        assertThat(display.nextButton).isEqualTo(RemoteCommand.TriggerCue(key(0), 1))
    }

    @Test
    fun `next and previous skip disabled cues`() {
        val display = reduce(RemoteInputs(liveAt(5, SONG_C, 7), lastLive = null))

        assertThat(display.previousButton).isEqualTo(RemoteCommand.TriggerCue(key(5), 5))
        assertThat(reduce(RemoteInputs(liveAt(5, SONG_C, 0), lastLive = null)).nextButton)
            .isEqualTo(RemoteCommand.TriggerCue(key(5), 2))
    }

    @Test
    fun `a partly unresolved arrangement steps with trigger next and previous and shows no thumbnails`() {
        val display = reduce(RemoteInputs(liveAt(7, SONG_C, 1), lastLive = null))

        assertThat(display.current).isEqualTo(cue(7, SONG_C, 1, BoxMark.LIVE, thumbnails = false))
        assertThat(display.nextButton).isEqualTo(RemoteCommand.TriggerNext)
        assertThat(display.previousButton).isEqualTo(RemoteCommand.TriggerPrevious)
        assertThat(display.tapCurrent).isEqualTo(RemoteCommand.TriggerCue(key(7), 1))
    }

    @Test
    fun `a live slide outside a playlist shows the text and steps with trigger next and previous`() {
        val live =
            LiveState(ConnectionStatus.CONNECTED, item = null, slide = LiveSlide(SONG_A, 1, 26), slideText = text)
        val display = reduce(RemoteInputs(live, lastLive = remembered(0, SONG_A, 2)))

        assertThat(display.current).isEqualTo(RemoteBox.Text("Text 03"))
        assertThat(display.next).isEqualTo(RemoteBox.Text("Text 04"))
        assertThat(display.nextButton).isEqualTo(RemoteCommand.TriggerNext)
        assertThat(display.previousButton).isEqualTo(RemoteCommand.TriggerPrevious)
        assertThat(display.tapCurrent).isNull()
        assertThat(display.previousItem).isNull()
        assertThat(display.nextItem).isNull()
    }

    @Test
    fun `a media item this app triggered shows its card live with the cue buttons disabled`() {
        val display = reduce(RemoteInputs(cleared, lastLive = remembered(1, SONG_A, 1), mediaLive = key(4)))

        assertThat(display.current).isEqualTo(RemoteBox.ItemCard("Loop", PlaylistItemType.MEDIA, BoxMark.LIVE))
        assertThat(
            display.header
        ).isEqualTo(RemoteHeader("Loop", arrangement = null, cueNumber = null, cueCount = null))
        assertThat(display.tapCurrent).isEqualTo(RemoteCommand.TriggerItem(key(4)))
        assertThat(display.nextButton).isNull()
        assertThat(display.previousButton).isNull()
        assertThat(display.baseItem).isEqualTo(key(4))
        assertThat(display.previousItem).isEqualTo(key(1))
        assertThat(display.nextItem).isEqualTo(key(5))
    }

    @Test
    fun `an app-triggered media item is live while the stream still reports the previous slide`() {
        val display =
            reduce(RemoteInputs(liveAt(1, SONG_A, 1), lastLive = remembered(1, SONG_A, 1), mediaLive = key(4)))

        assertThat(display.current).isEqualTo(RemoteBox.ItemCard("Loop", PlaylistItemType.MEDIA, BoxMark.LIVE))
        assertThat(display.cued).isFalse()
    }

    @Test
    fun `a cued item starts at its first enabled cue`() {
        val songD = songC.copy(
            groups = songC.groups + Group("d", "Tag", null, listOf(Slide("D a", enabled = false), Slide("D b"))),
            arrangements = songC.arrangements + Arrangement("d", "D", listOf("d"), totalCues = 2)
        )
        val withD = playlist.copy(
            items =
                playlist.items +
                    PlaylistItem(key(8), "Song C", PlaylistItemType.PRESENTATION, PresentationRef(SONG_C, "d", "D"))
        )
        val display = RemoteDisplay.reduce(
            RemoteInputs(liveAt(0, SONG_A, 2), lastLive = null, cued = key(8)),
            withD,
            mapOf(SONG_A to songA, SONG_C to songD)
        )

        assertThat((display.current as RemoteBox.Slide).cue.index).isEqualTo(1)
        assertThat(display.tapCurrent).isEqualTo(RemoteCommand.TriggerCue(key(8), 1))
        assertThat(display.nextButton).isEqualTo(RemoteCommand.TriggerCue(key(8), 1))
        assertThat(display.header?.cueNumber).isEqualTo(2)
    }

    @Test
    fun `item steps start from the remembered item`() {
        val display = reduce(RemoteInputs(cleared, lastLive = remembered(5, SONG_C, 3)))

        assertThat(display.previousItem).isEqualTo(key(4))
        assertThat(display.nextItem).isEqualTo(key(6))
        assertThat(display.nextUp).isEqualTo(NextUp(key(6), "Walk-in", arrangement = null))
    }

    @Test
    fun `the last item is the end of the playlist`() {
        val display = reduce(RemoteInputs(liveAt(7, SONG_C, 0), lastLive = null))

        assertThat(display.nextItem).isNull()
        assertThat(display.nextUp).isNull()
        assertThat(display.endOfPlaylist).isTrue()
    }

    @Test
    fun `an unread presentation is loading`() {
        val display = RemoteDisplay.reduce(RemoteInputs(liveAt(0, SONG_A, 2), lastLive = null), playlist, emptyMap())

        assertThat(display).prop(RemoteDisplay::status).isEqualTo(RemoteStatus.LOADING)
        assertThat(display.tapCurrent).isNull()
        assertThat(display.nextButton).isNull()
    }

    @Test
    fun `content needed is the shown item's playlist and the presentations of it and the next item`() {
        val live = RemoteInputs(liveAt(1, SONG_A, 0), lastLive = null)
        val cuedMedia = live.copy(cued = key(4))
        val outside =
            RemoteInputs(LiveState(ConnectionStatus.CONNECTED, null, LiveSlide(SONG_A, 1, 26)), lastLive = null)

        assertThat(RemoteDisplay.playlistNeeded(live)).isEqualTo(PLAYLIST)
        assertThat(RemoteDisplay.presentationsNeeded(live, playlist)).containsExactlyInAnyOrder(SONG_A)
        assertThat(RemoteDisplay.presentationsNeeded(cuedMedia, playlist)).containsExactlyInAnyOrder(SONG_C)
        assertThat(RemoteDisplay.playlistNeeded(outside)).isNull()
        assertThat(RemoteDisplay.presentationsNeeded(live, null).isEmpty()).isTrue()
        assertThat(reduce(outside).current).isInstanceOf(RemoteBox.Text::class)
    }
}
