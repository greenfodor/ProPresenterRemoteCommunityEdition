package com.greenfodor.ppremotece.feature.playlist

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.greenfodor.ppremotece.core.domain.arrangement.ArrangementChoice
import com.greenfodor.ppremotece.core.domain.model.Arrangement
import org.junit.jupiter.api.Test

class ArrangementLabelTest {
    @Test
    fun `named arrangement is labelled with its name`() {
        val choice = ArrangementChoice.Resolved(Arrangement("a-1", "Chorus Only", listOf("g-1"), totalCues = 7))

        assertThat(choice.toArrangementLabel()).isEqualTo(ArrangementLabel.Named("Chorus Only"))
    }

    @Test
    fun `arrangement with groups and no name is labelled unnamed`() {
        val choice = ArrangementChoice.Resolved(Arrangement("a-1", "", listOf("g-1"), totalCues = 2))

        assertThat(choice.toArrangementLabel()).isEqualTo(ArrangementLabel.Unnamed)
    }

    @Test
    fun `item without an arrangement has no label`() {
        assertThat(ArrangementChoice.SongOrder.toArrangementLabel()).isNull()
    }
}
