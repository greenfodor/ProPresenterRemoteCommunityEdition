package com.greenfodor.ppremotece.core.domain.remote

import com.greenfodor.ppremotece.core.domain.arrangement.ArrangementChoice
import com.greenfodor.ppremotece.core.domain.arrangement.ArrangementExpander
import com.greenfodor.ppremotece.core.domain.arrangement.CueList
import com.greenfodor.ppremotece.core.domain.arrangement.liveItemResolution
import com.greenfodor.ppremotece.core.domain.model.CueSource
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
 * Runs [block] with the item's presentation and cue list ([liveItemResolution]); a loading display
 * while the presentation is not read yet, and null when the item plays no presentation.
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
        else -> liveItemResolution(presentation, ref)?.let { block(presentation, it.cueList) }
    }
}

internal fun cardDisplay(item: PlaylistItem, mark: BoxMark) =
    RemoteDisplay(
        status = RemoteStatus.SHOWING,
        header = RemoteHeader(item.name, arrangement = null, cueNumber = null, cueCount = null),
        current = RemoteBox.ItemCard(item.name, item.type, mark),
        tapCurrent = RemoteCommand.TriggerItem(item.key)
    )

internal fun headerOf(name: String, cueList: CueList, cueIndex: Int?) =
    RemoteHeader(
        itemName = name,
        arrangement = cueList.choice.takeIf { it is ArrangementChoice.Resolved },
        cueNumber = cueIndex?.plus(1),
        cueCount = cueList.cues.size
    )

internal fun slideBox(source: CueSource, presentation: Presentation, cueList: CueList, cueIndex: Int, mark: BoxMark) =
    RemoteBox.Slide(
        source = source,
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
    source: CueSource,
    presentation: Presentation,
    cueList: CueList,
    current: Int?,
    currentMark: BoxMark,
    next: Int?
) = RemoteSidebar(
    source = source,
    presentationUuid = presentation.uuid,
    cues = cueList.cues,
    marks = buildMap {
        current?.let { put(it, currentMark) }
        next?.let { put(it, BoxMark.NEXT) }
    },
    focus = current,
    thumbnails = !cueList.countMismatch
)
