package com.greenfodor.ppremotece.core.domain.layout

import kotlinx.coroutines.flow.Flow

enum class WidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED
}

/** A slide-size slider step: the minimum cell width in dp, or null for one full-width column. */
enum class GridStep(
    val minCellWidthDp: Int?
) {
    SIZE_120(120),
    SIZE_160(160),
    SIZE_200(200),
    SIZE_280(280),
    FULL(null);

    companion object {
        val Default = SIZE_200
    }
}

/** The slide-grid size step chosen for each window width class. */
interface GridPreferences {
    fun gridStep(widthClass: WidthClass): Flow<GridStep>

    suspend fun setGridStep(widthClass: WidthClass, step: GridStep)
}
