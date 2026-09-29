package com.greenfodor.ppremotece.core.domain.remote

import com.greenfodor.ppremotece.core.domain.live.nextCueIndex
import com.greenfodor.ppremotece.core.domain.live.previousCueIndex
import com.greenfodor.ppremotece.core.domain.model.Cue
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
        val cue: LiveCue
    ) : Base {
        override val item: PlaylistItemKey get() = cue.item
    }

    data object Text : Base {
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
        slide == null && inputs.mediaLive != null -> Base.Media(inputs.mediaLive)
        slide != null && item != null -> Base.Cue(LiveCue(item, slide.presentationUuid, slide.index))
        slide != null -> Base.Text
        inputs.lastLive != null -> Base.Cue(inputs.lastLive)
        else -> Base.None
    }
}

internal fun Base.matches(playlist: Playlist): Boolean =
    when (this) {
        is Base.Cue -> playlist.item(cue.item)?.presentation?.presentationUuid == cue.presentationUuid
        is Base.Media -> playlist.item(item) != null
        Base.Text, Base.None -> false
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
        base is Base.Cue -> liveDisplay(item, base.cue.cueIndex, presentations, inputs.live.slideText)
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
        val first = cueList.cues.firstOrNull()
        val next = first?.let { nextCueIndex(cueList.cues, it.index) }
        val trigger = first?.let { RemoteCommand.TriggerCue(item.key, it.index) }
        RemoteDisplay(
            status = RemoteStatus.SHOWING,
            header = headerOf(item, cueList, first?.index),
            current = first?.let { slideBox(item, presentation, cueList, it.index, BoxMark.CUED) } ?: RemoteBox.Empty,
            next = next?.let { slideBox(item, presentation, cueList, it, BoxMark.NEXT) } ?: RemoteBox.Empty,
            aspect = slideAspect(presentation),
            tapCurrent = trigger,
            tapNext = next?.let { RemoteCommand.TriggerCue(item.key, it) },
            nextButton = trigger
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
            val next = nextCueIndex(cueList.cues, cueIndex)
            val previous = previousCueIndex(cueList.cues, cueIndex)
            RemoteDisplay(
                status = RemoteStatus.SHOWING,
                header = headerOf(item, cueList, cueIndex),
                current = slideBox(item, presentation, cueList, cueIndex, BoxMark.LIVE),
                next = next?.let { slideBox(item, presentation, cueList, it, BoxMark.NEXT) } ?: RemoteBox.Empty,
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
                }
            )
        }
    } ?: textDisplay(text)
