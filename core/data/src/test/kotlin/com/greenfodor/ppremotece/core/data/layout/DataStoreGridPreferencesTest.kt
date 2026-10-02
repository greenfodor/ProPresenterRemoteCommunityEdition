package com.greenfodor.ppremotece.core.data.layout

import androidx.datastore.core.CorruptionException
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.greenfodor.ppremotece.core.data.FlakyDataStore
import com.greenfodor.ppremotece.core.domain.layout.GridStep
import com.greenfodor.ppremotece.core.domain.layout.ViewMode
import com.greenfodor.ppremotece.core.domain.layout.WidthClass
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
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

    @Test
    fun `a read error gives the default, then the saved value is read again`() = runTest {
        val saved = preferencesOf(stringPreferencesKey("view_mode_compact") to "LIST")
        val dataStore = FlakyDataStore(saved, readFailures = 1)

        DataStoreGridPreferences(dataStore).viewMode(WidthClass.COMPACT).test {
            assertThat(awaitItem()).isEqualTo(ViewMode.GRID)
            assertThat(awaitItem()).isEqualTo(ViewMode.LIST)
        }
    }

    @Test
    fun `a failed write is reported`() = runTest {
        val preferences = DataStoreGridPreferences(FlakyDataStore().apply { failWrites = true })

        assertThat(preferences.setViewMode(WidthClass.COMPACT, ViewMode.LIST))
            .isEqualTo(Result.Failure(DataError.Local.WRITE_FAILED))
        assertThat(preferences.setGridStep(WidthClass.COMPACT, GridStep.SIZE_120))
            .isEqualTo(Result.Failure(DataError.Local.WRITE_FAILED))
    }

    @Test
    fun `a corrupt file gives the default without reading again`() = runTest {
        val dataStore = FlakyDataStore(readFailures = 2, readError = { CorruptionException("corrupt") })

        DataStoreGridPreferences(dataStore).viewMode(WidthClass.COMPACT).test {
            assertThat(awaitItem()).isEqualTo(ViewMode.GRID)
            awaitComplete()
        }
        assertThat(dataStore.readFailures).isEqualTo(1)
    }
}
