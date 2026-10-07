package com.greenfodor.ppremotece.core.domain.live

import assertk.assertThat
import assertk.assertions.containsExactly
import com.greenfodor.ppremotece.core.domain.model.ConnectionStatus
import com.greenfodor.ppremotece.core.domain.model.LiveCue
import com.greenfodor.ppremotece.core.domain.model.LiveSlide
import com.greenfodor.ppremotece.core.domain.model.LiveState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class ReconnectingTest {
    @Test
    fun `reconnecting is true only while the stream reconnects and repeats no value`() = runBlocking<Unit> {
        val connected = LiveState(ConnectionStatus.CONNECTED, item = null, slide = null)
        val dropped = connected.copy(connection = ConnectionStatus.RECONNECTING)
        val repository = object : LiveStateRepository {
            override val liveState = flowOf(
                LiveState.Initial,
                connected,
                dropped,
                dropped.copy(slide = LiveSlide("p-1", index = 2, totalCues = 8)),
                connected
            )
            override val lastLive = MutableStateFlow<LiveCue?>(null)
        }

        assertThat(repository.reconnecting.toList()).containsExactly(false, true, false)
    }
}
