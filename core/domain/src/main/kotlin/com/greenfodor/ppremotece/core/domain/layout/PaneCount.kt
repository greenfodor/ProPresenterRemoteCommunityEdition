package com.greenfodor.ppremotece.core.domain.layout

private const val TWO_PANE_MIN_WIDTH_DP = 840

/** Number of panes for a window [windowWidthDp] wide: 2 from 840 dp, else 1. */
fun paneCount(windowWidthDp: Int): Int = if (windowWidthDp >= TWO_PANE_MIN_WIDTH_DP) 2 else 1
