package com.greenfodor.ppremotece.core.domain.thumbnail

private const val QUALITY_STEP = 200
private const val GRID_QUALITY = 400
private const val MAX_QUALITY = 1080
private const val WIDTH_RATIO = 16
private const val HEIGHT_RATIO = 9

/** The size a thumbnail is asked for: the grid request, or a box [px] wide. */
sealed interface ThumbnailQuality {
    data object Grid : ThumbnailQuality

    data class Box(
        val px: Int
    ) : ThumbnailQuality
}

/**
 * A thumbnail route. Box qualities are on the playlist route's scale, where the image is
 * ⌊quality × 16/9⌋ px wide; the presentation route takes the width in px.
 */
enum class ThumbnailRoute {
    /** `GET /v1/playlist/{pl}/{item}/thumbnail/{cue}`. */
    PLAYLIST,

    /** `GET /v1/presentation/{uuid}/thumbnail/{cue}`. */
    PRESENTATION;

    /** The `quality` value sent for [boxQuality], or for the grid request when it is null: 400 / 711 for the grid. */
    fun query(boxQuality: Int?): Int {
        val quality = boxQuality ?: GRID_QUALITY
        return when (this) {
            PLAYLIST -> quality
            PRESENTATION -> quality * WIDTH_RATIO / HEIGHT_RATIO
        }
    }
}

/**
 * The box quality for a box [px] wide: ⌈px × 9/16⌉ rounded up to a multiple of 200 and capped at
 * 1080; null when that is not above the grid quality 400, so the grid request applies.
 */
fun boxQuality(px: Int): Int? {
    val wanted = (px * HEIGHT_RATIO + WIDTH_RATIO - 1) / WIDTH_RATIO
    val quality = ((wanted + QUALITY_STEP - 1) / QUALITY_STEP * QUALITY_STEP).coerceAtMost(MAX_QUALITY)
    return quality.takeIf { it > GRID_QUALITY }
}

/** Every value [boxQuality] gives, in ascending order. */
fun boxQualities(): List<Int> = (GRID_QUALITY + QUALITY_STEP until MAX_QUALITY step QUALITY_STEP).toList() + MAX_QUALITY
