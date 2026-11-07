package com.greenfodor.ppremotece.feature.playlist.grid

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.greenfodor.ppremotece.core.domain.arrangement.ArrangementBanner
import com.greenfodor.ppremotece.feature.playlist.ArrangementLabel
import com.greenfodor.ppremotece.feature.playlist.R
import org.junit.jupiter.api.Test

class GridHeaderTextTest {
    @Test
    fun `a presentation live without an arrangement reads Live in Master`() {
        val banner = ArrangementBanner(arrangementName = null, isSongOrder = true, totalCues = 11)

        assertThat(banner.textPlural()).isEqualTo(R.plurals.grid_banner_master)
    }

    @Test
    fun `a named or other live arrangement keeps its banner text`() {
        assertThat(ArrangementBanner("Chorus Only", isSongOrder = false, totalCues = 7).textPlural())
            .isEqualTo(R.plurals.grid_banner_named)
        assertThat(ArrangementBanner(null, isSongOrder = false, totalCues = 9).textPlural())
            .isEqualTo(R.plurals.grid_banner_other)
    }

    @Test
    fun `the re-sync line names Master for an item without an arrangement label`() {
        assertThat(null.orMaster()).isEqualTo(ArrangementLabel.Master)
        assertThat(ArrangementLabel.Master.orMaster()).isEqualTo(ArrangementLabel.Master)
    }

    @Test
    fun `the re-sync line names this item's arrangement`() {
        assertThat(ArrangementLabel.Named("Chorus Only").orMaster()).isEqualTo(ArrangementLabel.Named("Chorus Only"))
        assertThat(ArrangementLabel.Unnamed.orMaster()).isEqualTo(ArrangementLabel.Unnamed)
    }
}
