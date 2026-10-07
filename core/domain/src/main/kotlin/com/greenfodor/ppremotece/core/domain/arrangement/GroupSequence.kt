package com.greenfodor.ppremotece.core.domain.arrangement

import com.greenfodor.ppremotece.core.domain.model.GroupColor

/** One occurrence of a group in a cue list: its first cue and which [occurrence] of the group it is, from 0. */
data class GroupPill(
    val groupUuid: String,
    val name: String,
    val color: GroupColor?,
    val firstCueIndex: Int,
    val occurrence: Int
)

/** The group occurrences of a cue list in order, and the index of the one holding the live cue. */
data class GroupSequence(
    val pills: List<GroupPill>,
    val livePill: Int?
)

/**
 * The group occurrences of [cueList] in cue order: a new occurrence starts at each cue that starts
 * a group. [GroupSequence.livePill] holds [liveCueIndex], null without one.
 */
fun groupSequence(cueList: CueList, liveCueIndex: Int?): GroupSequence {
    val pills = mutableListOf<GroupPill>()
    val occurrences = mutableMapOf<String, Int>()
    var livePill: Int? = null
    cueList.cues.forEach { cue ->
        if (cue.startsGroup) {
            val occurrence = occurrences.getOrDefault(cue.groupUuid, 0)
            occurrences[cue.groupUuid] = occurrence + 1
            pills += GroupPill(cue.groupUuid, cue.groupName, cue.groupColor, cue.index, occurrence)
        }
        if (cue.index == liveCueIndex) livePill = pills.lastIndex
    }
    return GroupSequence(pills, livePill)
}
