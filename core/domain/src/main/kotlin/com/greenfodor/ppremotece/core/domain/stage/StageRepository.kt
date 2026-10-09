package com.greenfodor.ppremotece.core.domain.stage

import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.model.StageLayout
import com.greenfodor.ppremotece.core.domain.model.StageScreen
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.EmptyResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The connected host's stage screens and stage layouts, in ProPresenter's order, and
 * [layoutMap], each stage screen's uuid with the uuid of the layout it shows, from the status
 * stream; all three are [Loadable.NotLoaded] while disconnected.
 */
interface StageRepository {
    val screens: StateFlow<Loadable<List<StageScreen>>>
    val layouts: StateFlow<Loadable<List<StageLayout>>>
    val layoutMap: StateFlow<Loadable<Map<String, String>>>

    /** Makes layout [layoutUuid] the layout of stage screen [screenUuid]. */
    suspend fun setLayout(screenUuid: String, layoutUuid: String): EmptyResult<DataError.Network>
}

private const val WIDTH_STEP = 200
private const val MAX_WIDTH = 800

/** The thumbnail width asked for a box [px] wide: rounded up to 200, 400, 600 or 800. */
fun stageLayoutThumbnailWidth(px: Int): Int =
    ((px + WIDTH_STEP - 1) / WIDTH_STEP * WIDTH_STEP).coerceIn(WIDTH_STEP, MAX_WIDTH)

/** The memory cache key of stage layout [uuid]'s thumbnail [width] px wide on host [hostName]. */
fun stageLayoutThumbnailKey(hostName: String, uuid: String, width: Int): String =
    "stage-layout:$hostName:$uuid:w$width"

/** Where a stage layout thumbnail is read from and the key it is kept under in memory. */
data class StageLayoutThumbnailRequest(
    val url: String,
    val cacheKey: String
)

/** Builds stage layout thumbnail requests for one connected host. */
fun interface StageLayoutThumbnailRequests {
    /** Stage layout [uuid]'s thumbnail for a box [px] wide ([stageLayoutThumbnailWidth]). */
    fun request(uuid: String, px: Int): StageLayoutThumbnailRequest
}

/** Stage layout thumbnail requests of the connected host. */
interface StageLayoutThumbnailSource {
    /** Emits the request builder of each newly connected host, and null while none is connected. */
    val stageLayoutThumbnailRequests: Flow<StageLayoutThumbnailRequests?>
}
