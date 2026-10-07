package com.greenfodor.ppremotece.core.domain.remote

import com.greenfodor.ppremotece.core.domain.live.nextCueIndex
import com.greenfodor.ppremotece.core.domain.model.Cue
import com.greenfodor.ppremotece.core.domain.model.CueSource
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailQuality
import com.greenfodor.ppremotece.core.domain.thumbnail.boxThumbnailQuality

/** The thumbnail of [cue] of [source] at [quality]. */
data class ThumbnailTarget(
    val source: CueSource,
    val presentationUuid: String,
    val cue: Cue,
    val quality: ThumbnailQuality
)

/**
 * The thumbnails to load ahead for [display] while its next box shows a slide with thumbnails: the
 * first enabled cue after the next one at the quality of a box whose image is [width] px wide
 * ([boxThumbnailQuality]).
 */
fun remotePrefetch(display: RemoteDisplay, width: Int): List<ThumbnailTarget> {
    val next = (display.next as? RemoteBox.Slide)?.takeIf { it.thumbnails } ?: return emptyList()
    val afterNext = display.sidebar
        ?.takeIf { it.source == next.source }
        ?.cues
        ?.let { cues -> nextCueIndex(cues, next.cue.index)?.let { index -> cues.first { it.index == index } } }
    return listOfNotNull(
        afterNext?.let { ThumbnailTarget(next.source, next.presentationUuid, it, boxThumbnailQuality(width)) }
    )
}
