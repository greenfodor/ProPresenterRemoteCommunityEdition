package com.greenfodor.ppremotece.core.domain.layout

private const val RAIL_MIN_WIDTH_DP = 600

enum class NavigationLayout {
    BAR,
    RAIL
}

/** The shell's navigation for a window [windowWidthDp] wide: a bottom bar below 600 dp, a rail from 600 dp. */
fun navigationLayout(windowWidthDp: Int): NavigationLayout =
    if (windowWidthDp >= RAIL_MIN_WIDTH_DP) NavigationLayout.RAIL else NavigationLayout.BAR
