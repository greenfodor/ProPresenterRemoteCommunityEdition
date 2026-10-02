package com.greenfodor.ppremotece.core.domain.arrangement

import com.greenfodor.ppremotece.core.domain.live.nextCueIndex
import com.greenfodor.ppremotece.core.domain.model.Cue

/** What Re-sync triggers in a playlist item's grid: one of its cues, or nothing with a [NoMatchReason]. */
sealed interface ResyncTarget {
    data class Cue(
        val index: Int
    ) : ResyncTarget

    data class NoMatch(
        val reason: NoMatchReason
    ) : ResyncTarget
}

enum class NoMatchReason {
    /** The live slide is not in this item's arrangement. */
    NOT_IN_ARRANGEMENT,

    /** The arrangement the live slide plays in can't be resolved. */
    LIVE_ARRANGEMENT_UNKNOWN
}

/**
 * The cue of [itemCues] showing the slide at [liveIndex] of [liveCues]: the same repeat of that
 * slide (group and slide in group), else its first occurrence. A disabled cue moves to the next
 * enabled cue; null when there is none. No match when the slide is not in [itemCues], or when
 * [liveCues] is null (the live arrangement is unresolved).
 */
fun resyncTarget(liveCues: List<Cue>?, liveIndex: Int, itemCues: List<Cue>): ResyncTarget? {
    if (liveCues == null) return ResyncTarget.NoMatch(NoMatchReason.LIVE_ARRANGEMENT_UNKNOWN)
    val target = liveCues.firstOrNull { it.index == liveIndex }?.let { live ->
        val repeat = liveCues.count { it.index < liveIndex && it.sameSlideAs(live) }
        val matches = itemCues.filter { it.sameSlideAs(live) }
        matches.getOrNull(repeat) ?: matches.firstOrNull()
    }
    return when {
        target == null -> ResyncTarget.NoMatch(NoMatchReason.NOT_IN_ARRANGEMENT)
        target.enabled -> ResyncTarget.Cue(target.index)
        else -> nextCueIndex(itemCues, target.index)?.let { ResyncTarget.Cue(it) }
    }
}

private fun Cue.sameSlideAs(other: Cue) = groupUuid == other.groupUuid && slideIndexInGroup == other.slideIndexInGroup
