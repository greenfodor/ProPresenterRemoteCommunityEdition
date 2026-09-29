package com.greenfodor.ppremotece.feature.playlist.grid

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.greenfodor.ppremotece.core.domain.layout.GridStep
import org.junit.jupiter.api.Test

class GridStepCellsTest {
    @Test
    fun `steps map to adaptive minimum widths and full to one column`() {
        assertThat(GridStep.entries.map { it.toGridCells() }).containsExactly(
            GridCells.Adaptive(120.dp),
            GridCells.Adaptive(160.dp),
            GridCells.Adaptive(200.dp),
            GridCells.Adaptive(280.dp),
            GridCells.Fixed(1)
        )
    }

    @Test
    fun `the default step is 200 dp`() {
        assertThat(GridStep.Default.toGridCells()).isEqualTo(GridCells.Adaptive(200.dp))
    }
}
