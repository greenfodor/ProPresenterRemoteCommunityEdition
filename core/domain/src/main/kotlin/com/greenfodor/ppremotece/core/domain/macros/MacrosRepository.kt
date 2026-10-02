package com.greenfodor.ppremotece.core.domain.macros

import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.model.MacroCollection
import kotlinx.coroutines.flow.StateFlow

/** The connected host's macro collections from its status stream; [Loadable.NotLoaded] while disconnected. */
interface MacrosRepository {
    val collections: StateFlow<Loadable<List<MacroCollection>>>
}
