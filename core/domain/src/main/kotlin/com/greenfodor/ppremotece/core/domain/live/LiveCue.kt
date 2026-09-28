package com.greenfodor.ppremotece.core.domain.live

import com.greenfodor.ppremotece.core.domain.model.Cue
import com.greenfodor.ppremotece.core.domain.model.LiveState
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey

/** The index of the live cue when [live] is showing this playlist item and its presentation, else null. */
fun liveCueIndex(live: LiveState, item: PlaylistItemKey, presentationUuid: String?): Int? =
    live.slide?.takeIf {
        live.item == item && presentationUuid != null && it.presentationUuid == presentationUuid
    }?.index

/** The first enabled cue after the live cue of this playlist item, or null when it is not live or none follows. */
fun nextCueIndex(live: LiveState, item: PlaylistItemKey, presentationUuid: String?, cues: List<Cue>): Int? {
    val liveIndex = liveCueIndex(live, item, presentationUuid) ?: return null
    return cues.firstOrNull { it.index > liveIndex && it.enabled }?.index
}
