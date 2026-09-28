package com.greenfodor.ppremotece.core.domain.arrangement

import com.greenfodor.ppremotece.core.domain.model.Arrangement
import com.greenfodor.ppremotece.core.domain.model.Cue

/** Which arrangement a playlist item's cues were expanded from. */
sealed interface ArrangementChoice {
    data class Resolved(
        val arrangement: Arrangement
    ) : ArrangementChoice

    /** No usable arrangement: the presentation's groups in their stored order. */
    data object SongOrder : ArrangementChoice
}

/** [countMismatch] is true when a resolved arrangement expands to a cue count other than its `totalCues`. */
data class CueList(
    val choice: ArrangementChoice,
    val cues: List<Cue>,
    val countMismatch: Boolean = false
)
