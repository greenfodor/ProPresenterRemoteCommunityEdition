package com.greenfodor.ppremotece.core.domain.status

import com.greenfodor.ppremotece.core.domain.model.LiveSlide
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey

/** A decoded `status/updates` frame. */
sealed interface StatusEvent {
    data object SlideChanged : StatusEvent

    data class SlideIndex(
        val slide: LiveSlide?
    ) : StatusEvent

    data class PresentationActive(
        val presentationUuid: String?
    ) : StatusEvent

    data class PlaylistActive(
        val item: PlaylistItemKey?
    ) : StatusEvent

    data class Heartbeat(
        val epochSeconds: Long
    ) : StatusEvent

    data class Unknown(
        val url: String?
    ) : StatusEvent
}
