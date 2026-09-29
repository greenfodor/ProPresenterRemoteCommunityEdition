package com.greenfodor.ppremotece.core.domain.layout

private const val MEDIUM_MIN_WIDTH_DP = 600
private const val TWO_PANE_MIN_WIDTH_DP = 840

/** Number of panes for a window [windowWidthDp] wide: 2 from 840 dp, else 1. */
fun paneCount(windowWidthDp: Int): Int = if (windowWidthDp >= TWO_PANE_MIN_WIDTH_DP) 2 else 1

/** Width class of a window [windowWidthDp] wide: compact below 600 dp, medium below 840 dp, else expanded. */
fun widthClassOf(windowWidthDp: Int): WidthClass =
    when {
        windowWidthDp < MEDIUM_MIN_WIDTH_DP -> WidthClass.COMPACT
        windowWidthDp < TWO_PANE_MIN_WIDTH_DP -> WidthClass.MEDIUM
        else -> WidthClass.EXPANDED
    }
