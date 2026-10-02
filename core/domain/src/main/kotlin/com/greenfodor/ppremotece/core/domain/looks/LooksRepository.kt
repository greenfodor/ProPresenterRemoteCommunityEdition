package com.greenfodor.ppremotece.core.domain.looks

import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.model.Look
import kotlinx.coroutines.flow.StateFlow

/**
 * The connected host's looks and its live look from the status stream: [looks] is
 * [Loadable.NotLoaded] while disconnected; [currentLook] carries the live look's own uuid with the
 * name and index of the last triggered look, null until known.
 */
interface LooksRepository {
    val looks: StateFlow<Loadable<List<Look>>>
    val currentLook: StateFlow<Look?>
}

/** The look of [looks] whose index and name match [current]; its uuid is not compared. */
fun liveLook(looks: List<Look>, current: Look?): Look? =
    current?.let { live -> looks.firstOrNull { it.index == live.index && it.name == live.name } }
