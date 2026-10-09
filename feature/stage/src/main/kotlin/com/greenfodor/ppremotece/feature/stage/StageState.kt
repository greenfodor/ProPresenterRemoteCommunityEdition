package com.greenfodor.ppremotece.feature.stage

import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.stage.StageLayoutThumbnailRequests

/**
 * The stage screens as cards, not loaded until the stream has sent the screens, the layouts and
 * the layout map, and unavailable when the server rejected one of the three. [thumbnails] builds
 * the connected host's stage layout thumbnail requests.
 */
data class StageState(
    val screens: Loadable<List<StageScreenUi>> = Loadable.NotLoaded,
    val thumbnails: StageLayoutThumbnailRequests? = null
)

/** A stage screen's card: its name and the layout it shows, both null while the map names none it knows. */
data class StageScreenUi(
    val uuid: String,
    val name: String,
    val layoutUuid: String?,
    val layoutName: String?
)

/**
 * The layouts to choose from for one stage screen: [screenName] is the screen's name, [screenGone]
 * is true once the loaded screens no longer hold it, and the tile of the layout the screen shows
 * is [StageLayoutUi.live].
 */
data class StageLayoutsState(
    val screenName: String = "",
    val screenGone: Boolean = false,
    val layouts: Loadable<List<StageLayoutUi>> = Loadable.NotLoaded,
    val thumbnails: StageLayoutThumbnailRequests? = null
)

data class StageLayoutUi(
    val uuid: String,
    val name: String,
    val live: Boolean
)

sealed interface StageLayoutsAction {
    data class OnLayoutClick(
        val uuid: String
    ) : StageLayoutsAction
}
