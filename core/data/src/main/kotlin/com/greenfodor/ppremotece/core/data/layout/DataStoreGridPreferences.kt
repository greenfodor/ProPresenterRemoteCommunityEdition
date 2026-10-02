package com.greenfodor.ppremotece.core.data.layout

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.greenfodor.ppremotece.core.domain.layout.GridPreferences
import com.greenfodor.ppremotece.core.domain.layout.GridStep
import com.greenfodor.ppremotece.core.domain.layout.ViewMode
import com.greenfodor.ppremotece.core.domain.layout.WidthClass
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.EmptyResult
import com.greenfodor.ppremotece.core.domain.result.Result
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import java.io.IOException

/** The `grid_preferences` DataStore. */
internal val Context.gridPreferencesDataStore by preferencesDataStore(name = "grid_preferences")

/**
 * [GridPreferences] in [dataStore], one key per width class for the step (`grid_step_compact`,
 * `grid_step_medium`, `grid_step_expanded`) and for the view mode (`view_mode_compact`,
 * `view_mode_medium`, `view_mode_expanded`); [GridStep.Default] and [ViewMode.GRID] when unset.
 * A read error gives the defaults, then the values are read again after [READ_RETRY_DELAY_MS]; a
 * write error leaves the saved value unchanged and is returned.
 */
class DataStoreGridPreferences(
    private val dataStore: DataStore<Preferences>
) : GridPreferences {
    override fun gridStep(widthClass: WidthClass): Flow<GridStep> =
        read(STEP_KEYS.getValue(widthClass), GridStep.Default) { name ->
            GridStep.entries.firstOrNull { it.name == name }
        }

    override suspend fun setGridStep(widthClass: WidthClass, step: GridStep): EmptyResult<DataError.Local> =
        write(STEP_KEYS.getValue(widthClass), step.name)

    override fun viewMode(widthClass: WidthClass): Flow<ViewMode> =
        read(MODE_KEYS.getValue(widthClass), ViewMode.GRID) { name -> ViewMode.entries.firstOrNull { it.name == name } }

    override suspend fun setViewMode(widthClass: WidthClass, mode: ViewMode): EmptyResult<DataError.Local> =
        write(MODE_KEYS.getValue(widthClass), mode.name)

    private fun <T> read(key: Preferences.Key<String>, default: T, parse: (String) -> T?): Flow<T> =
        dataStore.data
            .retryWhen { cause, _ ->
                (cause is IOException).also { recoverable ->
                    if (recoverable) {
                        emit(emptyPreferences())
                        delay(READ_RETRY_DELAY_MS)
                    }
                }
            }.map { preferences -> preferences[key]?.let(parse) ?: default }
            .distinctUntilChanged()

    private suspend fun write(key: Preferences.Key<String>, value: String): EmptyResult<DataError.Local> =
        try {
            dataStore.edit { it[key] = value }
            Result.Success(Unit)
        } catch (_: IOException) {
            Result.Failure(DataError.Local.WRITE_FAILED)
        }

    private companion object {
        const val READ_RETRY_DELAY_MS = 1_000L
        val STEP_KEYS = keys("grid_step")
        val MODE_KEYS = keys("view_mode")

        fun keys(prefix: String): Map<WidthClass, Preferences.Key<String>> =
            WidthClass.entries.associateWith { stringPreferencesKey("${prefix}_${it.name.lowercase()}") }
    }
}
