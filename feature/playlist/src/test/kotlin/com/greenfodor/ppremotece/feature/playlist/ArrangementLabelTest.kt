package com.greenfodor.ppremotece.feature.playlist

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.greenfodor.ppremotece.core.domain.arrangement.ArrangementChoice
import com.greenfodor.ppremotece.core.domain.model.Arrangement
import com.greenfodor.ppremotece.core.domain.model.ConnectionStatus
import com.greenfodor.ppremotece.core.domain.model.LiveSlide
import com.greenfodor.ppremotece.core.domain.model.LiveState
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.feature.playlist.grid.liveCueIndex
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

    @Test
    fun `live cue is marked only for the live item and its presentation`() {
        val item = PlaylistItemKey("pl-1", 1)
        val live =
            LiveState(ConnectionStatus.CONNECTED, item, LiveSlide(presentationUuid = "p-1", index = 3, totalCues = 7))

        assertThat(liveCueIndex(live, item, presentationUuid = "p-1")).isEqualTo(3)
        assertThat(liveCueIndex(live, PlaylistItemKey("pl-1", 0), presentationUuid = "p-1")).isNull()
        assertThat(liveCueIndex(live, item, presentationUuid = "p-2")).isNull()
        assertThat(liveCueIndex(live.copy(slide = null), item, presentationUuid = "p-1")).isNull()
    }
}
