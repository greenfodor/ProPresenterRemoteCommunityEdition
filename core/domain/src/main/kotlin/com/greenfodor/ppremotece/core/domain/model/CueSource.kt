package com.greenfodor.ppremotece.core.domain.model

/** Where a cue list comes from: a playlist item, or a presentation played outside a playlist. */
sealed interface CueSource {
    data class PlaylistItem(
        val key: PlaylistItemKey
    ) : CueSource

    data class Presentation(
        val uuid: String
    ) : CueSource
}
