package com.greenfodor.ppremotece.core.domain.status

import com.greenfodor.ppremotece.core.domain.model.LiveSlide
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.SlideText

/** A decoded `status/updates` frame. */
sealed interface StatusEvent {
    data class SlideChanged(
        val text: SlideText?
    ) : StatusEvent

    data class SlideIndex(
        val slide: LiveSlide?
    ) : StatusEvent

    data class PresentationActive(
        val presentationUuid: String?
    ) : StatusEvent

    /** The live playlist item and the uuid of the presentation it plays, when the frame names them. */
    data class PlaylistActive(
        val item: PlaylistItemKey?,
        val presentationUuid: String? = null
    ) : StatusEvent

    data class Heartbeat(
        val epochSeconds: Long
    ) : StatusEvent

    data class Unknown(
        val url: String?
    ) : StatusEvent
}
