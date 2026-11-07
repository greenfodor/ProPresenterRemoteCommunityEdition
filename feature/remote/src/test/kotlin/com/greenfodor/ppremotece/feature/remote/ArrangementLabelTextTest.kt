package com.greenfodor.ppremotece.feature.remote

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.arrangement.ArrangementChoice
import com.greenfodor.ppremotece.core.domain.model.Arrangement
import org.junit.jupiter.api.Test

class ArrangementLabelTextTest {
    @Test
    fun `no arrangement is labelled Master`() {
        val text = ArrangementChoice.SongOrder.labelText()

        assertThat(text).isInstanceOf(UiText.StringResource::class)
        assertThat((text as UiText.StringResource).id).isEqualTo(R.string.remote_arrangement_master)
    }

    @Test
    fun `an arrangement with groups and no name is still labelled unnamed`() {
        val text = ArrangementChoice.Resolved(Arrangement("a-1", "", listOf("g-1"), totalCues = 2)).labelText()

        assertThat((text as UiText.StringResource).id).isEqualTo(R.string.remote_unnamed_arrangement)
    }

    @Test
    fun `a named arrangement is labelled with its name`() {
        val text = ArrangementChoice.Resolved(
            Arrangement("a-1", "Chorus Only", listOf("g-1"), totalCues = 7)
        ).labelText()

        assertThat(text).isEqualTo(UiText.DynamicString("Chorus Only"))
    }
}
