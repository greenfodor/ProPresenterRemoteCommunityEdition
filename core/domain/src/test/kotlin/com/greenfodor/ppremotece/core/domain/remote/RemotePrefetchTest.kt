package com.greenfodor.ppremotece.core.domain.remote

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import com.greenfodor.ppremotece.core.domain.arrangement.ArrangementExpander
import com.greenfodor.ppremotece.core.domain.model.CueSource
import com.greenfodor.ppremotece.core.domain.model.SlideText
import com.greenfodor.ppremotece.core.domain.remote.RemoteFixtures.SONG_A
import com.greenfodor.ppremotece.core.domain.remote.RemoteFixtures.SONG_C
import com.greenfodor.ppremotece.core.domain.remote.RemoteFixtures.key
import com.greenfodor.ppremotece.core.domain.remote.RemoteFixtures.liveAt
import com.greenfodor.ppremotece.core.domain.remote.RemoteFixtures.playlist
import com.greenfodor.ppremotece.core.domain.remote.RemoteFixtures.presentations
import com.greenfodor.ppremotece.core.domain.remote.RemoteFixtures.remembered
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailQuality
import org.junit.jupiter.api.Test

class RemotePrefetchTest {
    private val wide = 1000

    private fun live(item: Int, presentation: String, cue: Int) =
        RemoteDisplay.reduce(
            RemoteInputs(liveAt(item, presentation, cue), lastLive = remembered(item, presentation, cue)),
            playlist,
            presentations
        )

    private fun target(item: Int, presentation: String, cue: Int, quality: ThumbnailQuality) =
        ThumbnailTarget(
            source = CueSource.PlaylistItem(key(item)),
            presentationUuid = presentation,
            cue = ArrangementExpander.expand(
                presentations.getValue(presentation),
                checkNotNull(playlist.items[item].presentation)
            ).cues[cue],
            quality = quality
        )

    @Test
    fun `the cue after next at the boxes' quality`() {
        val prefetch = remotePrefetch(live(5, SONG_C, 0), wide)

        assertThat(prefetch).containsExactly(target(5, SONG_C, 3, ThumbnailQuality.Box(1000)))
    }

    @Test
    fun `unmeasured boxes warm the cue after next at the grid quality`() {
        val prefetch = remotePrefetch(live(5, SONG_C, 0), width = 0)

        assertThat(prefetch).containsExactly(target(5, SONG_C, 3, ThumbnailQuality.Grid))
    }

    @Test
    fun `the cue after next skips a disabled cue`() {
        val prefetch = remotePrefetch(live(5, SONG_C, 4), wide)

        assertThat(prefetch).containsExactly(target(5, SONG_C, 7, ThumbnailQuality.Box(1000)))
    }

    @Test
    fun `a next cue with nothing after it warms nothing`() {
        assertThat(remotePrefetch(live(0, SONG_A, 3), wide)).isEmpty()
    }

    @Test
    fun `an empty next box warms nothing`() {
        assertThat(remotePrefetch(live(1, SONG_A, 1), wide)).isEmpty()
    }

    @Test
    fun `text boxes warm nothing`() {
        val display = RemoteDisplay.reduce(
            RemoteInputs(
                liveAt(0, SONG_C, 0, SlideText("Text 01", "Text 02")),
                lastLive = remembered(0, SONG_C, 0)
            ),
            playlist,
            presentations
        )

        assertThat(remotePrefetch(display, wide)).isEmpty()
    }

    @Test
    fun `an item card warms nothing`() {
        val display = RemoteDisplay.reduce(
            RemoteInputs(liveAt(0, SONG_A, 0), lastLive = remembered(0, SONG_A, 0), cued = key(4)),
            playlist,
            presentations
        )

        assertThat(remotePrefetch(display, wide)).isEmpty()
    }

    @Test
    fun `cues without thumbnails warm nothing`() {
        assertThat(remotePrefetch(live(7, SONG_C, 0), wide)).isEmpty()
    }
}
