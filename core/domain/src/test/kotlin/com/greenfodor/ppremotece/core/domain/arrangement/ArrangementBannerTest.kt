package com.greenfodor.ppremotece.core.domain.arrangement

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.greenfodor.ppremotece.core.domain.arrangement.SongFixtures.SONG
import com.greenfodor.ppremotece.core.domain.arrangement.SongFixtures.cueList
import com.greenfodor.ppremotece.core.domain.arrangement.SongFixtures.key
import com.greenfodor.ppremotece.core.domain.arrangement.SongFixtures.ref
import com.greenfodor.ppremotece.core.domain.arrangement.SongFixtures.song
import com.greenfodor.ppremotece.core.domain.model.ConnectionStatus
import com.greenfodor.ppremotece.core.domain.model.CueSource
import com.greenfodor.ppremotece.core.domain.model.LiveSlide
import com.greenfodor.ppremotece.core.domain.model.LiveState
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import org.junit.jupiter.api.Test

class ArrangementBannerTest {
    private val item0 = CueSource.PlaylistItem(key(0))
    private val longCues = cueList("long")

    private fun live(item: PlaylistItemKey?, totalCues: Int, presentation: String = SONG) =
        LiveState(ConnectionStatus.CONNECTED, item, LiveSlide(presentation, index = 2, totalCues = totalCues))

    @Test
    fun `another item live in another arrangement names it with its cue count`() {
        val banner = arrangementBanner(item0, longCues, live(key(1), totalCues = 6), song, liveItemRef = ref("short"))

        assertThat(banner).isEqualTo(ArrangementBanner(arrangementName = "Short", isSongOrder = false, totalCues = 6))
    }

    @Test
    fun `another item live in the same arrangement shows no banner`() {
        assertThat(arrangementBanner(item0, longCues, live(key(1), totalCues = 11), song, liveItemRef = ref("long")))
            .isNull()
    }

    @Test
    fun `the presentation live outside a playlist in its placeholder arrangement is song order`() {
        val banner = arrangementBanner(item0, longCues, live(item = null, totalCues = 5), song, liveItemRef = null)

        assertThat(banner).isEqualTo(ArrangementBanner(arrangementName = null, isSongOrder = true, totalCues = 5))
    }

    @Test
    fun `a live arrangement that does not match the live cue count is compared by count`() {
        val banner = arrangementBanner(item0, longCues, live(item = null, totalCues = 9), song, liveItemRef = null)

        assertThat(banner).isEqualTo(ArrangementBanner(arrangementName = null, isSongOrder = false, totalCues = 9))
        assertThat(liveCueList(song, live(item = null, totalCues = 9), liveItemRef = null)).isNull()
    }

    @Test
    fun `this item live shows no banner`() {
        assertThat(arrangementBanner(item0, longCues, live(key(0), totalCues = 11), song, liveItemRef = ref("long")))
            .isNull()
    }

    @Test
    fun `an unresolved live arrangement is compared by cue count`() {
        val unknown = ref("unknown")

        assertThat(arrangementBanner(item0, longCues, live(key(1), totalCues = 9), song, liveItemRef = unknown))
            .isEqualTo(ArrangementBanner(arrangementName = null, isSongOrder = false, totalCues = 9))
        assertThat(arrangementBanner(item0, longCues, live(key(1), totalCues = 11), song, liveItemRef = unknown))
            .isNull()
        assertThat(arrangementBanner(item0, longCues, live(key(1), totalCues = 9), song, liveItemRef = null))
            .isEqualTo(ArrangementBanner(arrangementName = null, isSongOrder = false, totalCues = 9))
    }

    @Test
    fun `another presentation live shows no banner`() {
        val otherSong = live(key(1), totalCues = 6, presentation = "other")

        assertThat(arrangementBanner(item0, longCues, otherSong, song, liveItemRef = ref("short"))).isNull()
    }

    @Test
    fun `a presentation source shows no banner`() {
        val source = CueSource.Presentation(SONG)

        assertThat(arrangementBanner(source, longCues, live(key(1), totalCues = 6), song, liveItemRef = ref("short")))
            .isNull()
    }
}
