package com.greenfodor.ppremotece.core.domain.macros

import com.greenfodor.ppremotece.core.domain.model.MacroCollection
import kotlinx.coroutines.flow.StateFlow

/** The connected host's macro collections from its status stream; empty while disconnected. */
interface MacrosRepository {
    val collections: StateFlow<List<MacroCollection>>
}
