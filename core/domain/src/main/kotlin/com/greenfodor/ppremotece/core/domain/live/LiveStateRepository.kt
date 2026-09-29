package com.greenfodor.ppremotece.core.domain.live

import com.greenfodor.ppremotece.core.domain.model.LiveCue
import com.greenfodor.ppremotece.core.domain.model.LiveState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The live item and cue of one ProPresenter host, kept current from its status stream. */
interface LiveStateRepository {
    val liveState: Flow<LiveState>

    /**
     * The last cue reported live with both a playlist item and a slide during this connection.
     * It is kept through clears, stream reconnects and while nothing collects [liveState], and is
     * null after a disconnect or a connect to another host.
     */
    val lastLive: StateFlow<LiveCue?>
}
