package com.greenfodor.ppremotece.core.data.layout

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.greenfodor.ppremotece.core.domain.layout.GridStep
import com.greenfodor.ppremotece.core.domain.layout.ViewMode
import com.greenfodor.ppremotece.core.domain.layout.WidthClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DataStoreGridPreferencesTest {
    @TempDir
    lateinit var dir: File

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferences by lazy {
        DataStoreGridPreferences(
            PreferenceDataStoreFactory.create(scope = scope) { dir.resolve("grid_preferences.preferences_pb") }
        )
    }

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `the view mode is grid until one is saved`() = runBlocking {
        WidthClass.entries.forEach { assertThat(preferences.viewMode(it).first()).isEqualTo(ViewMode.GRID) }
    }

    @Test
    fun `the view mode is saved per width class`() = runBlocking {
        preferences.setViewMode(WidthClass.COMPACT, ViewMode.LIST)

        assertThat(preferences.viewMode(WidthClass.COMPACT).first()).isEqualTo(ViewMode.LIST)
        assertThat(preferences.viewMode(WidthClass.MEDIUM).first()).isEqualTo(ViewMode.GRID)
        assertThat(preferences.viewMode(WidthClass.EXPANDED).first()).isEqualTo(ViewMode.GRID)
    }

    @Test
    fun `the view mode and the slide size are kept apart`() = runBlocking {
        preferences.setGridStep(WidthClass.COMPACT, GridStep.SIZE_120)
        preferences.setViewMode(WidthClass.COMPACT, ViewMode.LIST)

        assertThat(preferences.gridStep(WidthClass.COMPACT).first()).isEqualTo(GridStep.SIZE_120)
        assertThat(preferences.viewMode(WidthClass.COMPACT).first()).isEqualTo(ViewMode.LIST)
    }
}
