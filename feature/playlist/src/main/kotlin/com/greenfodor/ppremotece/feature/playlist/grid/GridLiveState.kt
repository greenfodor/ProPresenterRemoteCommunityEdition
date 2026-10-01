package com.greenfodor.ppremotece.feature.playlist.grid

import com.greenfodor.ppremotece.core.domain.arrangement.CueList
import com.greenfodor.ppremotece.core.domain.arrangement.arrangementBanner
import com.greenfodor.ppremotece.core.domain.arrangement.groupSequence
import com.greenfodor.ppremotece.core.domain.arrangement.liveCueList
import com.greenfodor.ppremotece.core.domain.arrangement.resyncCue
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.live.liveCueIndex
import com.greenfodor.ppremotece.core.domain.live.nextCueIndex
import com.greenfodor.ppremotece.core.domain.model.CueSource
import com.greenfodor.ppremotece.core.domain.model.LiveState
import com.greenfodor.ppremotece.core.domain.model.Presentation
import com.greenfodor.ppremotece.core.domain.model.PresentationRef
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.EmptyResult
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailQuality
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailRequests
import com.greenfodor.ppremotece.core.domain.thumbnail.slideAspect
import com.greenfodor.ppremotece.feature.playlist.toArrangementLabel

/** The grid of [cueList] for [source], each cue with its thumbnail request from [requests] unless the count mismatches. */
@Suppress("LongParameterList")
internal fun gridState(
    source: CueSource,
    title: String,
    presentation: Presentation,
    cueList: CueList,
    requests: ThumbnailRequests?,
    thumbnailGeneration: Int
): SlideGridState {
    val thumbnails = requests.takeUnless { cueList.countMismatch }
    return SlideGridState(
        title = title,
        label = cueList.choice.toArrangementLabel(),
        cues = cueList.cues.map { cue ->
            CueUi(
                index = cue.index,
                groupName = cue.groupName,
                groupColor = cue.groupColor,
                text = cue.slideText,
                label = cue.slideLabel,
                enabled = cue.enabled,
                thumbnail = thumbnails?.request(source, presentation.uuid, cue, ThumbnailQuality.Grid)
            )
        },
        aspect = slideAspect(presentation),
        countMismatch = cueList.countMismatch,
        thumbnailGeneration = thumbnailGeneration,
        isLoading = false
    )
}

/** This grid with [live]'s live and next cues, the arrangement banner and the group strip. */
internal fun SlideGridState.withLive(
    source: CueSource,
    presentation: Presentation,
    cueList: CueList,
    live: LiveState,
    liveItemRef: PresentationRef?
): SlideGridState {
    val liveIndex = liveCueIndex(live, source, presentation.uuid, cueList.cues)
    return copy(
        liveCueIndex = liveIndex,
        nextCueIndex = nextCueIndex(live, source, presentation.uuid, cueList.cues),
        banner = arrangementBanner(source, cueList, live, presentation, liveItemRef),
        groupSequence = groupSequence(cueList, liveIndex)
    )
}

/** The cue of [cueList] that Re-sync triggers for [live]'s slide ([resyncCue]); null without a live slide. */
internal fun resyncTargetOf(
    presentation: Presentation,
    cueList: CueList,
    live: LiveState,
    liveItemRef: PresentationRef?
): Int? =
    live.slide?.let { slide ->
        resyncCue(liveCueList(presentation, live, liveItemRef)?.cues, slide.index, cueList.cues)
    }

/** Triggers [cueIndex] of [source]: by playlist item, or by presentation. */
internal suspend fun ProPresenterClient.trigger(source: CueSource, cueIndex: Int): EmptyResult<DataError.Network> =
    when (source) {
        is CueSource.PlaylistItem -> triggerCue(source.key, cueIndex)
        is CueSource.Presentation -> triggerPresentationCue(source.uuid, cueIndex)
    }

/** Whether cue [cueIndex] exists and is enabled. */
internal fun CueList?.isEnabled(cueIndex: Int): Boolean =
    this?.cues?.firstOrNull { it.index == cueIndex }?.enabled == true
