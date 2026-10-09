package com.greenfodor.ppremotece.feature.stage

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The Stage tab's root: the stage screens. */
@Serializable
data object StageRoute : NavKey

/** The layouts to choose from for stage screen [screenUuid]. */
@Serializable
data class StageLayoutsRoute(
    val screenUuid: String
) : NavKey
