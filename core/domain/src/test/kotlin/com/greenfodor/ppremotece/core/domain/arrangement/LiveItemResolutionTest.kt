package com.greenfodor.ppremotece.core.domain.arrangement

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import com.greenfodor.ppremotece.core.domain.arrangement.SongFixtures.SONG
import com.greenfodor.ppremotece.core.domain.arrangement.SongFixtures.cueList
import com.greenfodor.ppremotece.core.domain.arrangement.SongFixtures.ref
import com.greenfodor.ppremotece.core.domain.arrangement.SongFixtures.song
import com.greenfodor.ppremotece.core.domain.model.PresentationRef
import org.junit.jupiter.api.Test

class LiveItemResolutionTest {
    @Test
    fun `a live playlist item plays its own arrangement`() {
        val resolution = liveItemResolution(song, itemRef = ref("short"))

        assertThat(resolution).isEqualTo(
            LiveItemResolution(song, ref("short"), cueList("short"), arrangementResolved = true)
        )
    }

    @Test
    fun `a presentation outside a playlist plays its current arrangement`() {
        val resolution = liveItemResolution(song, itemRef = null)

        assertThat(resolution).isNotNull().prop(LiveItemResolution::cueList).isEqualTo(cueList("placeholder"))
        assertThat(resolution).isNotNull().prop(LiveItemResolution::arrangement)
            .isEqualTo(PresentationRef(SONG, "placeholder", arrangementName = ""))
        assertThat(resolution).isNotNull().prop(LiveItemResolution::arrangementResolved).isTrue()
    }

    @Test
    fun `an item whose arrangement is not in the presentation plays the song order unresolved`() {
        val resolution = liveItemResolution(song, itemRef = ref("unknown"))

        assertThat(resolution).isNotNull().prop(LiveItemResolution::cueList)
            .prop(CueList::choice).isEqualTo(ArrangementChoice.SongOrder)
        assertThat(resolution).isNotNull().prop(LiveItemResolution::arrangementResolved).isFalse()
    }

    @Test
    fun `an item with no arrangement plays the song order resolved`() {
        val resolution = liveItemResolution(song, itemRef = ref(""))

        assertThat(resolution).isNotNull().prop(LiveItemResolution::cueList)
            .prop(CueList::choice).isEqualTo(ArrangementChoice.SongOrder)
        assertThat(resolution).isNotNull().prop(LiveItemResolution::arrangementResolved).isTrue()
    }

    @Test
    fun `an item of another presentation resolves nothing`() {
        assertThat(liveItemResolution(song, itemRef = PresentationRef("other", "short", "Short"))).isNull()
    }
}
