package com.greenfodor.ppremotece.core.domain.arrangement

import com.greenfodor.ppremotece.core.domain.model.Cue
import com.greenfodor.ppremotece.core.domain.model.CueSource
import com.greenfodor.ppremotece.core.domain.model.LiveState
import com.greenfodor.ppremotece.core.domain.model.Presentation
import com.greenfodor.ppremotece.core.domain.model.PresentationRef

/**
 * The banner of a playlist item's grid while its presentation is live in another arrangement:
 * [arrangementName] (null when unnamed or unresolved), whether it is the presentation's own group
 * order ([isSongOrder]) and the live cue count.
 */
data class ArrangementBanner(
    val arrangementName: String?,
    val isSongOrder: Boolean,
    val totalCues: Int
)

/**
 * The cue list ProPresenter plays for [presentation] while [live] shows it ([liveItemResolution]):
 * the arrangement of the live item ([liveItemRef]), or the presentation's current arrangement when
 * no item is live; null when that arrangement can't be resolved or its cue count is not the live
 * slide's.
 */
fun liveCueList(presentation: Presentation, live: LiveState, liveItemRef: PresentationRef?): CueList? {
    val resolution = if (live.item == null) {
        liveItemResolution(presentation, itemRef = null)
    } else {
        liveItemRef?.let { liveItemResolution(presentation, it) }
    }
    return resolution
        ?.takeIf {
            it.arrangementResolved &&
                live.slide?.totalCues?.let { total -> total == it.cueList.cues.size } != false
        }
        ?.cueList
}

/**
 * The banner for [source]'s grid, showing [itemCueList] of [presentation]: shown when the
 * presentation is live but not through this item, and the live arrangement ([liveCues], from
 * [liveCueList]) orders its slides (group and slide in group) differently; an unresolved live
 * arrangement is compared by cue count. Null for a presentation source.
 */
fun arrangementBanner(
    source: CueSource,
    itemCueList: CueList,
    live: LiveState,
    presentation: Presentation,
    liveCues: CueList?
): ArrangementBanner? {
    val slide = live.slide
        ?.takeIf {
            it.presentationUuid == presentation.uuid &&
                source is CueSource.PlaylistItem &&
                live.item != source.key
        }
        ?: return null
    return when {
        liveCues == null ->
            ArrangementBanner(null, isSongOrder = false, slide.totalCues).takeIf {
                slide.totalCues != itemCueList.cues.size
            }
        liveCues.cues.slideOrder() == itemCueList.cues.slideOrder() -> null
        else -> when (val choice = liveCues.choice) {
            is ArrangementChoice.Resolved ->
                ArrangementBanner(choice.arrangement.name.ifEmpty { null }, isSongOrder = false, slide.totalCues)
            ArrangementChoice.SongOrder -> ArrangementBanner(null, isSongOrder = true, slide.totalCues)
        }
    }
}

private fun List<Cue>.slideOrder() = map { it.groupUuid to it.slideIndexInGroup }
