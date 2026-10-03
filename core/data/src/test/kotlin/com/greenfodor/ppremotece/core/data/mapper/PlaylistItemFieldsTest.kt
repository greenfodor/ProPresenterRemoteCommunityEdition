package com.greenfodor.ppremotece.core.data.mapper

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.greenfodor.ppremotece.core.data.Fixtures
import com.greenfodor.ppremotece.core.domain.model.GroupColor
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemType
import org.junit.jupiter.api.Test

class PlaylistItemFieldsTest {
    private val items = Fixtures.playlist(Fixtures.STAGE_10_PLAYLIST).toDomain().items

    @Test
    fun `the stage 10 playlist holds nine items of four types`() {
        assertThat(items.map { it.type }).containsExactly(
            PlaylistItemType.PRESENTATION,
            PlaylistItemType.PRESENTATION,
            PlaylistItemType.PRESENTATION,
            PlaylistItemType.HEADER,
            PlaylistItemType.MEDIA,
            PlaylistItemType.AUDIO,
            PlaylistItemType.HEADER,
            PlaylistItemType.PRESENTATION,
            PlaylistItemType.PRESENTATION
        )
    }

    @Test
    fun `a header colour with no alpha is no colour`() {
        assertThat(items[3].headerColor).isNull()
    }

    @Test
    fun `a header colour with alpha is kept`() {
        val color = items[6].headerColor

        assertThat(color).isNotNull()
        assertThat(color).isEqualTo(GroupColor(0.33333334f, 0.41960785f, 0.18431373f, 1f))
    }

    @Test
    fun `media and audio items carry their target and duration`() {
        assertThat(items[4].targetUuid).isEqualTo("2ca780f1-d07f-40c3-b855-5ade46ba2eb9")
        assertThat(items[4].durationSeconds).isEqualTo(20)
        assertThat(items[5].targetUuid).isEqualTo("28bee335-2a94-4751-b0fd-26592d6dd209")
        assertThat(items[5].durationSeconds).isEqualTo(183)
    }

    @Test
    fun `items without a duration or a header colour have none`() {
        assertThat(items[0].durationSeconds).isNull()
        assertThat(items[0].headerColor).isNull()
        assertThat(items[3].durationSeconds).isNull()
    }
}
