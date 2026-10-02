package com.greenfodor.ppremotece.core.domain.remote

import com.greenfodor.ppremotece.core.domain.live.nextCueIndex
import com.greenfodor.ppremotece.core.domain.model.Cue
import com.greenfodor.ppremotece.core.domain.model.CueSource
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailQuality
import com.greenfodor.ppremotece.core.domain.thumbnail.boxQuality

/** The measured image widths of the Remote's current and next boxes in px; 0 until measured. */
data class BoxWidths(
    val current: Int = 0,
    val next: Int = 0
)

/** The thumbnail of [cue] of [source] at [quality]. */
data class ThumbnailTarget(
    val source: CueSource,
    val presentationUuid: String,
    val cue: Cue,
    val quality: ThumbnailQuality
)

/**
 * The thumbnails to load ahead for [display] while its next box shows a slide with thumbnails: the
 * first enabled cue after the next one at the next box's quality, and the next cue at the current
 * box's quality when that is another quality than the next box's. A box of width 0 asks for the
 * grid quality.
 */
fun remotePrefetch(display: RemoteDisplay, widths: BoxWidths): List<ThumbnailTarget> {
    val next = (display.next as? RemoteBox.Slide)?.takeIf { it.thumbnails } ?: return emptyList()
    val nextQuality = qualityOf(widths.next)
    val currentQuality = qualityOf(widths.current)
    val afterNext = display.sidebar
        ?.takeIf { it.source == next.source }
        ?.cues
        ?.let { cues -> nextCueIndex(cues, next.cue.index)?.let { index -> cues.first { it.index == index } } }
    return listOfNotNull(
        afterNext?.let { ThumbnailTarget(next.source, next.presentationUuid, it, nextQuality) },
        next.takeIf { currentQuality.requested() != nextQuality.requested() }
            ?.let { ThumbnailTarget(it.source, it.presentationUuid, it.cue, currentQuality) }
    )
}

private fun qualityOf(px: Int): ThumbnailQuality = if (px > 0) ThumbnailQuality.Box(px) else ThumbnailQuality.Grid

private fun ThumbnailQuality.requested(): Int? = (this as? ThumbnailQuality.Box)?.let { boxQuality(it.px) }
