package com.greenfodor.ppremotece.core.domain.remote

import com.greenfodor.ppremotece.core.domain.arrangement.CueList
import com.greenfodor.ppremotece.core.domain.arrangement.currentCueList
import com.greenfodor.ppremotece.core.domain.live.nextCueIndex
import com.greenfodor.ppremotece.core.domain.live.previousCueIndex
import com.greenfodor.ppremotece.core.domain.model.CueSource
import com.greenfodor.ppremotece.core.domain.model.LiveCue
import com.greenfodor.ppremotece.core.domain.model.Playlist
import com.greenfodor.ppremotece.core.domain.model.PlaylistItem
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.Presentation
import com.greenfodor.ppremotece.core.domain.model.SlideText
import com.greenfodor.ppremotece.core.domain.thumbnail.slideAspect

internal sealed interface Base {
    val item: PlaylistItemKey?

    data class Media(
        override val item: PlaylistItemKey
    ) : Base

    data class Cue(
        override val item: PlaylistItemKey,
        val presentationUuid: String,
        val cueIndex: Int
    ) : Base

    /** A cue of a presentation played outside a playlist; [liveCueCount] is null for a remembered cue. */
    data class Presentation(
        val presentationUuid: String,
        val cueIndex: Int,
        val liveCueCount: Int?
    ) : Base {
        override val item: PlaylistItemKey? = null
    }

    data object None : Base {
        override val item: PlaylistItemKey? = null
    }
}

internal fun baseOf(inputs: RemoteInputs): Base {
    val live = inputs.live
    val slide = live.slide
    val item = live.item
    return when {
        inputs.mediaLive != null -> Base.Media(inputs.mediaLive)
        slide != null && item != null -> Base.Cue(item, slide.presentationUuid, slide.index)
        slide != null -> Base.Presentation(slide.presentationUuid, slide.index, slide.totalCues)
        inputs.lastLive != null -> inputs.lastLive.toBase()
        else -> Base.None
    }
}

private fun LiveCue.toBase(): Base =
    when (source) {
        is CueSource.PlaylistItem -> Base.Cue(source.key, presentationUuid, cueIndex)
        is CueSource.Presentation -> Base.Presentation(presentationUuid, cueIndex, liveCueCount = null)
    }

internal fun Base.matches(playlist: Playlist): Boolean =
    when (this) {
        is Base.Cue -> playlist.item(item)?.presentation?.presentationUuid == presentationUuid
        is Base.Media -> playlist.item(item) != null
        is Base.Presentation, Base.None -> false
    }

internal fun focusOf(inputs: RemoteInputs, playlist: Playlist): PlaylistItemKey? {
    val base = baseOf(inputs).takeIf { it.matches(playlist) }
    return base?.item?.let { baseItem ->
        inputs.cued?.takeIf { it != baseItem && playlist.item(it) != null } ?: baseItem
    }
}

internal fun Playlist.item(key: PlaylistItemKey): PlaylistItem? = items.firstOrNull { it.key == key }

internal fun itemDisplay(
    inputs: RemoteInputs,
    base: Base,
    playlist: Playlist,
    presentations: Map<String, Presentation>
): RemoteDisplay {
    val focus = checkNotNull(focusOf(inputs, playlist))
    val item = checkNotNull(playlist.item(focus))
    val cued = focus != base.item
    val shown = when {
        cued -> cuedDisplay(item, presentations)
        base is Base.Cue -> liveDisplay(item, base.cueIndex, presentations, inputs.live.slideText)
        else -> cardDisplay(item, BoxMark.LIVE)
    }
    val nextItem = adjacentItem(playlist, focus, ItemDirection.NEXT)
    return if (shown.status == RemoteStatus.LOADING) {
        shown
    } else {
        shown.copy(
            cued = cued,
            baseItem = base.item,
            previousItem = adjacentItem(playlist, focus, ItemDirection.PREVIOUS),
            nextItem = nextItem,
            nextUp = nextItem?.let { nextUpOf(checkNotNull(playlist.item(it)), presentations) },
            endOfPlaylist = nextItem == null
        )
    }
}

private fun cuedDisplay(item: PlaylistItem, presentations: Map<String, Presentation>): RemoteDisplay =
    withCues(item, presentations) { presentation, cueList ->
        val source = CueSource.PlaylistItem(item.key)
        val first = cueList.cues.firstOrNull { it.enabled }
        val next = first?.let { nextCueIndex(cueList.cues, it.index) }
        val trigger = first?.let { RemoteCommand.TriggerCue(item.key, it.index) }
        RemoteDisplay(
            status = RemoteStatus.SHOWING,
            header = headerOf(item.name, cueList, first?.index),
            current = first?.let { slideBox(source, presentation, cueList, it.index, BoxMark.CUED) } ?: RemoteBox.Empty,
            next = next?.let { slideBox(source, presentation, cueList, it, BoxMark.NEXT) } ?: RemoteBox.Empty,
            aspect = slideAspect(presentation),
            tapCurrent = trigger,
            tapNext = next?.let { RemoteCommand.TriggerCue(item.key, it) },
            nextButton = trigger,
            sidebar = sidebarOf(source, presentation, cueList, first?.index, BoxMark.CUED, next)
        )
    } ?: cardDisplay(item, BoxMark.CUED)

private fun liveDisplay(
    item: PlaylistItem,
    cueIndex: Int,
    presentations: Map<String, Presentation>,
    text: SlideText?
): RemoteDisplay =
    withCues(item, presentations) { presentation, cueList ->
        if (cueList.cues.none { it.index == cueIndex }) {
            textDisplay(text)
        } else {
            val source = CueSource.PlaylistItem(item.key)
            val next = nextCueIndex(cueList.cues, cueIndex)
            val previous = previousCueIndex(cueList.cues, cueIndex)
            RemoteDisplay(
                status = RemoteStatus.SHOWING,
                header = headerOf(item.name, cueList, cueIndex),
                current = slideBox(source, presentation, cueList, cueIndex, BoxMark.LIVE),
                next = next?.let { slideBox(source, presentation, cueList, it, BoxMark.NEXT) } ?: RemoteBox.Empty,
                aspect = slideAspect(presentation),
                tapCurrent = RemoteCommand.TriggerCue(item.key, cueIndex),
                tapNext = next?.let { RemoteCommand.TriggerCue(item.key, it) },
                nextButton = if (cueList.countMismatch) {
                    RemoteCommand.TriggerNext
                } else {
                    next?.let { RemoteCommand.TriggerCue(item.key, it) }
                },
                previousButton = if (cueList.countMismatch) {
                    RemoteCommand.TriggerPrevious
                } else {
                    previous?.let { RemoteCommand.TriggerCue(item.key, it) }
                },
                sidebar = sidebarOf(source, presentation, cueList, cueIndex, BoxMark.LIVE, next)
            )
        }
    } ?: textDisplay(text)

/**
 * A presentation played outside a playlist, with the cues of its current arrangement; the text when
 * their count is not the live cue count or they do not hold the cue, and loading until it is read.
 */
internal fun presentationDisplay(
    base: Base.Presentation,
    presentations: Map<String, Presentation>,
    text: SlideText?
): RemoteDisplay {
    val presentation = presentations[base.presentationUuid] ?: return RemoteDisplay(status = RemoteStatus.LOADING)
    val cueList = currentCueList(presentation)
    val countMatches = base.liveCueCount == null || base.liveCueCount == cueList.cues.size
    return if (countMatches && cueList.cues.any { it.index == base.cueIndex }) {
        matchDisplay(presentation, cueList, base.cueIndex)
    } else {
        textDisplay(text)
    }
}

private fun matchDisplay(presentation: Presentation, cueList: CueList, cueIndex: Int): RemoteDisplay {
    val source = CueSource.Presentation(presentation.uuid)
    val next = nextCueIndex(cueList.cues, cueIndex)
    val previous = previousCueIndex(cueList.cues, cueIndex)
    return RemoteDisplay(
        status = RemoteStatus.SHOWING,
        header = headerOf(presentation.name, cueList, cueIndex),
        current = slideBox(source, presentation, cueList, cueIndex, BoxMark.LIVE),
        next = next?.let { slideBox(source, presentation, cueList, it, BoxMark.NEXT) } ?: RemoteBox.Empty,
        aspect = slideAspect(presentation),
        tapCurrent = RemoteCommand.TriggerPresentationCue(presentation.uuid, cueIndex),
        tapNext = next?.let { RemoteCommand.TriggerPresentationCue(presentation.uuid, it) },
        nextButton = next?.let { RemoteCommand.TriggerPresentationCue(presentation.uuid, it) },
        previousButton = previous?.let { RemoteCommand.TriggerPresentationCue(presentation.uuid, it) },
        sidebar = sidebarOf(source, presentation, cueList, cueIndex, BoxMark.LIVE, next),
        showsNextUp = false
    )
}
