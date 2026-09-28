package com.greenfodor.ppremotece.core.domain.live

import com.greenfodor.ppremotece.core.domain.model.LiveState
import kotlinx.coroutines.flow.Flow

/** The live item and cue of one ProPresenter host, kept current from its status stream. */
interface LiveStateRepository {
    val liveState: Flow<LiveState>
}
