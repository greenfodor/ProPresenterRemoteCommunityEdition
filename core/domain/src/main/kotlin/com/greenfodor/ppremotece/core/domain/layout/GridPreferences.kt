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

/** How a slide grid shows its cues: thumbnail cells or text rows. */
enum class ViewMode {
    GRID,
    LIST
}

/** The slide-grid size step and view mode chosen for each window width class. */
interface GridPreferences {
    fun gridStep(widthClass: WidthClass): Flow<GridStep>

    suspend fun setGridStep(widthClass: WidthClass, step: GridStep)

    fun viewMode(widthClass: WidthClass): Flow<ViewMode>

    suspend fun setViewMode(widthClass: WidthClass, mode: ViewMode)
}
