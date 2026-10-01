package com.greenfodor.ppremotece.core.domain.thumbnail

private const val QUALITY_STEP = 200

/** The size a thumbnail is asked for: the grid request, or a box [px] wide. */
sealed interface ThumbnailQuality {
    data object Grid : ThumbnailQuality

    data class Box(
        val px: Int
    ) : ThumbnailQuality
}

/** A thumbnail route with its grid `quality` value and the largest box `quality` value it is asked for. */
enum class ThumbnailRoute(
    val gridQuality: Int,
    val maxQuality: Int
) {
    /** `GET /v1/playlist/{pl}/{item}/thumbnail/{cue}`: the image is ⌊quality × 16/9⌋ px wide. */
    PLAYLIST(gridQuality = 400, maxQuality = 1080),

    /** `GET /v1/presentation/{uuid}/thumbnail/{cue}`: the image is quality px wide. */
    PRESENTATION(gridQuality = 711, maxQuality = 1920)
}

/**
 * The `quality` value for a box [px] wide on [route]: ⌈px × 9/16⌉ on the playlist route and px on
 * the presentation route, rounded up to a multiple of 200 and capped at [ThumbnailRoute.maxQuality];
 * null when that is not above [ThumbnailRoute.gridQuality], so the grid request applies.
 */
fun boxQuality(route: ThumbnailRoute, px: Int): Int? {
    val wanted = when (route) {
        ThumbnailRoute.PLAYLIST -> (px * 9 + 15) / 16
        ThumbnailRoute.PRESENTATION -> px
    }
    val quality = ((wanted + QUALITY_STEP - 1) / QUALITY_STEP * QUALITY_STEP).coerceAtMost(route.maxQuality)
    return quality.takeIf { it > route.gridQuality }
}
