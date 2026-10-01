package com.greenfodor.ppremotece.core.domain.arrangement

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.greenfodor.ppremotece.core.domain.arrangement.SongFixtures.chorusColor
import com.greenfodor.ppremotece.core.domain.arrangement.SongFixtures.cueList
import com.greenfodor.ppremotece.core.domain.model.Arrangement
import org.junit.jupiter.api.Test

class GroupSequenceTest {
    private val verse0 = GroupPill("v", "Verse 1", null, firstCueIndex = 0, occurrence = 0)

    @Test
    fun `each group of the arrangement is a pill and repeats are shown again`() {
        val sequence = groupSequence(cueList("long"), liveCueIndex = null)

        assertThat(sequence.pills).isEqualTo(
            listOf(
                verse0,
                GroupPill("c", "Chorus", chorusColor, firstCueIndex = 2, occurrence = 0),
                GroupPill("v", "Verse 1", null, firstCueIndex = 4, occurrence = 1),
                GroupPill("c", "Chorus", chorusColor, firstCueIndex = 6, occurrence = 1),
                GroupPill("b", "Bridge", null, firstCueIndex = 8, occurrence = 0),
                GroupPill("c", "Chorus", chorusColor, firstCueIndex = 9, occurrence = 2)
            )
        )
        assertThat(sequence.livePill).isNull()
    }

    @Test
    fun `a placeholder arrangement shows the groups in their stored order`() {
        assertThat(groupSequence(cueList("placeholder"), liveCueIndex = null).pills).isEqualTo(
            listOf(
                verse0,
                GroupPill("c", "Chorus", chorusColor, firstCueIndex = 2, occurrence = 0),
                GroupPill("b", "Bridge", null, firstCueIndex = 4, occurrence = 0)
            )
        )
    }

    @Test
    fun `a group repeated back to back is two pills`() {
        val doubled = ArrangementExpander.expand(
            SongFixtures.song.copy(
                arrangements = listOf(
                    Arrangement("cc", "CC", listOf("c", "c"), totalCues = 4)
                )
            ),
            SongFixtures.ref("cc")
        )

        assertThat(groupSequence(doubled, liveCueIndex = null).pills.map { it.firstCueIndex }).isEqualTo(listOf(0, 2))
    }

    @Test
    fun `the live pill is the occurrence holding the live cue`() {
        assertThat(groupSequence(cueList("long"), liveCueIndex = 7).livePill).isEqualTo(3)
        assertThat(groupSequence(cueList("long"), liveCueIndex = 9).livePill).isEqualTo(5)
    }
}
