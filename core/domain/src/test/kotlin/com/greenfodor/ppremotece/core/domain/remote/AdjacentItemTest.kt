package com.greenfodor.ppremotece.core.domain.remote

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.greenfodor.ppremotece.core.domain.remote.RemoteFixtures.key
import com.greenfodor.ppremotece.core.domain.remote.RemoteFixtures.playlist
import org.junit.jupiter.api.Test

class AdjacentItemTest {
    @Test
    fun `next skips the header and the placeholder and stops on media`() {
        assertThat(adjacentItem(playlist, key(1), ItemDirection.NEXT)).isEqualTo(key(4))
    }

    @Test
    fun `previous skips the placeholder and the header`() {
        assertThat(adjacentItem(playlist, key(4), ItemDirection.PREVIOUS)).isEqualTo(key(1))
    }

    @Test
    fun `audio items count`() {
        assertThat(adjacentItem(playlist, key(5), ItemDirection.NEXT)).isEqualTo(key(6))
    }

    @Test
    fun `null at both ends`() {
        assertThat(adjacentItem(playlist, key(0), ItemDirection.PREVIOUS)).isNull()
        assertThat(adjacentItem(playlist, key(7), ItemDirection.NEXT)).isNull()
    }
}
