package com.greenfodor.ppremotece.feature.props

import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.props.PropThumbnailRequests

/**
 * The prop collections that hold props as sections, not loaded until the stream's first
 * `prop_collections` frame; [showHeaders] is false when there is a single section. [thumbnails]
 * builds the connected host's prop thumbnail requests.
 */
data class PropsState(
    val sections: Loadable<List<PropSectionUi>> = Loadable.NotLoaded,
    val showHeaders: Boolean = false,
    val thumbnails: PropThumbnailRequests? = null
)

data class PropSectionUi(
    val uuid: String,
    val name: String,
    val props: List<PropUi>
)

/** A prop tile; its thumbnail is read again when [thumbnailVersion] (the name and transition) changes. */
data class PropUi(
    val uuid: String,
    val name: String,
    val active: Boolean,
    val thumbnailVersion: String
)

sealed interface PropsAction {
    data class OnPropClick(
        val uuid: String
    ) : PropsAction
}
