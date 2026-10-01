package com.greenfodor.ppremotece.core.domain.live

import com.greenfodor.ppremotece.core.domain.model.Cue
import com.greenfodor.ppremotece.core.domain.model.CueSource
import com.greenfodor.ppremotece.core.domain.model.LiveState

/**
 * The index of the live cue when [live] is showing [source] and its presentation, else null. A
 * playlist item is live when it is the live item; a presentation is live when no playlist item is
 * live and the live cue count equals the size of its [cues].
 */
fun liveCueIndex(live: LiveState, source: CueSource, presentationUuid: String?, cues: List<Cue>): Int? {
    val slide = live.slide?.takeIf { presentationUuid != null && it.presentationUuid == presentationUuid }
    val isLive = when (source) {
        is CueSource.PlaylistItem -> live.item == source.key
        is CueSource.Presentation -> live.item == null && slide?.totalCues == cues.size
    }
    return slide?.takeIf { isLive }?.index
}

/** The first enabled cue after the live cue of [source], or null when it is not live or none follows. */
fun nextCueIndex(live: LiveState, source: CueSource, presentationUuid: String?, cues: List<Cue>): Int? {
    val liveIndex = liveCueIndex(live, source, presentationUuid, cues) ?: return null
    return nextCueIndex(cues, liveIndex)
}

/** The first enabled cue after [after], or null when none follows. */
fun nextCueIndex(cues: List<Cue>, after: Int): Int? = cues.firstOrNull { it.index > after && it.enabled }?.index

/** The last enabled cue before [before], or null when none precedes it. */
fun previousCueIndex(cues: List<Cue>, before: Int): Int? = cues.lastOrNull { it.index < before && it.enabled }?.index
