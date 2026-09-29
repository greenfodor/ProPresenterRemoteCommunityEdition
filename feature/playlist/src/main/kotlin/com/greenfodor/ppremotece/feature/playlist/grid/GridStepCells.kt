package com.greenfodor.ppremotece.feature.playlist.grid

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.unit.dp
import com.greenfodor.ppremotece.core.domain.layout.GridStep

/** Adaptive columns of the step's minimum width, or one column for [GridStep.FULL]. */
fun GridStep.toGridCells(): GridCells = minCellWidthDp?.let { GridCells.Adaptive(it.dp) } ?: GridCells.Fixed(1)
