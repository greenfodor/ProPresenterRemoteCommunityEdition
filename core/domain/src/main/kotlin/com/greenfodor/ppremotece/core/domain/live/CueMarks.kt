package com.greenfodor.ppremotece.core.domain.live

import com.greenfodor.ppremotece.core.domain.model.Cue
import com.greenfodor.ppremotece.core.domain.model.CueSource
import com.greenfodor.ppremotece.core.domain.model.LiveCue
import com.greenfodor.ppremotece.core.domain.model.LiveState

/**
 * The cue a screen marks: the live cue, or, when [cleared], the last live cue after the slide was
 * cleared. [next] is the first enabled cue after it, null when none follows.
 */
data class MarkedCue(
    val index: Int,
    val cleared: Boolean,
    val next: Int?
)

/**
 * The cue that a screen showing [source] with [presentationUuid] and [cues] marks: the live cue
 * ([liveCueIndex]); else, while no slide is live, the cue of [lastLive] when it names this source
 * and presentation; else null.
 */
fun markedCue(
    live: LiveState,
    lastLive: LiveCue?,
    source: CueSource,
    presentationUuid: String?,
    cues: List<Cue>
): MarkedCue? {
    val liveIndex = liveCueIndex(live, source, presentationUuid, cues)
    val remembered = lastLive?.takeIf {
        live.slide == null && it.source == source && it.presentationUuid == presentationUuid
    }
    val index = liveIndex ?: remembered?.cueIndex ?: return null
    return MarkedCue(index, cleared = liveIndex == null, next = nextCueIndex(cues, index))
}

/** What a Previous or Next button of a cue grid sends. */
sealed interface CueStep {
    /** Nothing: the button is disabled. */
    data object Disabled : CueStep

    /** `trigger/next` or `trigger/previous`. */
    data object Relative : CueStep

    /** The trigger of cue [cueIndex]. */
    data class Explicit(
        val cueIndex: Int
    ) : CueStep
}

data class CueSteps(
    val next: CueStep,
    val previous: CueStep
)

/**
 * The steps of a grid showing [source] with [cues]. A live [marked] cue steps relatively; a cleared
 * one steps to the enabled cues around it, or not at all where none is. With nothing marked a
 * playlist item steps relatively and a presentation not at all.
 */
fun cueSteps(marked: MarkedCue?, source: CueSource, cues: List<Cue>): CueSteps =
    when {
        marked == null && source is CueSource.Presentation -> CueSteps(CueStep.Disabled, CueStep.Disabled)
        marked == null || !marked.cleared -> CueSteps(CueStep.Relative, CueStep.Relative)
        else -> CueSteps(
            next = marked.next.toStep(),
            previous = previousCueIndex(cues, marked.index).toStep()
        )
    }

private fun Int?.toStep(): CueStep = this?.let { CueStep.Explicit(it) } ?: CueStep.Disabled
