package com.greenfodor.ppremotece.core.domain.arrangement

import com.greenfodor.ppremotece.core.domain.live.nextCueIndex
import com.greenfodor.ppremotece.core.domain.model.Cue

/**
 * The cue of [itemCues] showing the slide at [liveIndex] of [liveCues]: the same repeat of that
 * slide (group and slide in group), else its first occurrence, else the first enabled cue, which
 * is also the choice when [liveCues] is null. A disabled cue moves to the next enabled cue; null
 * when there is none.
 */
fun resyncCue(liveCues: List<Cue>?, liveIndex: Int, itemCues: List<Cue>): Int? {
    val target = liveCues?.firstOrNull { it.index == liveIndex }?.let { live ->
        val repeat = liveCues.count { it.index < liveIndex && it.sameSlideAs(live) }
        val matches = itemCues.filter { it.sameSlideAs(live) }
        matches.getOrNull(repeat) ?: matches.firstOrNull()
    }
    return when {
        target == null -> itemCues.firstOrNull { it.enabled }?.index
        target.enabled -> target.index
        else -> nextCueIndex(itemCues, target.index)
    }
}

private fun Cue.sameSlideAs(other: Cue) = groupUuid == other.groupUuid && slideIndexInGroup == other.slideIndexInGroup
