package com.greenfodor.ppremotece.core.domain.remote

import com.greenfodor.ppremotece.core.domain.arrangement.ArrangementChoice
import com.greenfodor.ppremotece.core.domain.arrangement.ArrangementExpander
import com.greenfodor.ppremotece.core.domain.arrangement.CueList
import com.greenfodor.ppremotece.core.domain.model.PlaylistItem
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemType
import com.greenfodor.ppremotece.core.domain.model.Presentation
import com.greenfodor.ppremotece.core.domain.model.SlideText

internal fun textDisplay(text: SlideText?) =
    RemoteDisplay(
        status = RemoteStatus.SHOWING,
        current = RemoteBox.Text(text?.current.orEmpty()),
        next = RemoteBox.Text(text?.next.orEmpty()),
        nextButton = RemoteCommand.TriggerNext,
        previousButton = RemoteCommand.TriggerPrevious
    )

/**
 * Runs [block] with the item's presentation and cue list; a loading display while the
 * presentation is not read yet, and null when the item plays no presentation.
 */
internal fun withCues(
    item: PlaylistItem,
    presentations: Map<String, Presentation>,
    block: (Presentation, CueList) -> RemoteDisplay
): RemoteDisplay? {
    val ref = item.presentation?.takeIf { item.type == PlaylistItemType.PRESENTATION }
    val presentation = ref?.let { presentations[it.presentationUuid] }
    return when {
        ref == null -> null
        presentation == null -> RemoteDisplay(status = RemoteStatus.LOADING)
        else -> block(presentation, ArrangementExpander.expand(presentation, ref))
    }
}

internal fun cardDisplay(item: PlaylistItem, mark: BoxMark) =
    RemoteDisplay(
        status = RemoteStatus.SHOWING,
        header = RemoteHeader(item.name, arrangement = null, cueNumber = null, cueCount = null),
        current = RemoteBox.ItemCard(item.name, item.type, mark),
        tapCurrent = RemoteCommand.TriggerItem(item.key)
    )

internal fun headerOf(item: PlaylistItem, cueList: CueList, cueIndex: Int?) =
    RemoteHeader(
        itemName = item.name,
        arrangement = cueList.choice.takeIf { it is ArrangementChoice.Resolved },
        cueNumber = cueIndex?.plus(1),
        cueCount = cueList.cues.size
    )

internal fun slideBox(item: PlaylistItem, presentation: Presentation, cueList: CueList, cueIndex: Int, mark: BoxMark) =
    RemoteBox.Slide(
        item = item.key,
        presentationUuid = presentation.uuid,
        cue = cueList.cues.first { it.index == cueIndex },
        mark = mark,
        thumbnails = !cueList.countMismatch
    )

internal fun nextUpOf(item: PlaylistItem, presentations: Map<String, Presentation>): NextUp {
    val ref = item.presentation
    val arrangement = ref?.let { presentations[it.presentationUuid] }
        ?.let { ArrangementExpander.expand(it, checkNotNull(ref)).choice }
        ?.takeIf { it is ArrangementChoice.Resolved }
    return NextUp(item.key, item.name, arrangement)
}

internal fun sidebarOf(
    item: PlaylistItem,
    presentation: Presentation,
    cueList: CueList,
    current: Pair<Int?, BoxMark>,
    next: Int?
) = RemoteSidebar(
    item = item.key,
    presentationUuid = presentation.uuid,
    cues = cueList.cues,
    marks = buildMap {
        current.first?.let { put(it, current.second) }
        next?.let { put(it, BoxMark.NEXT) }
    },
    thumbnails = !cueList.countMismatch
)
