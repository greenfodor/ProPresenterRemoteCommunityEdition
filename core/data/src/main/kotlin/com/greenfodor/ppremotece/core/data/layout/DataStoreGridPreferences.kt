package com.greenfodor.ppremotece.core.data.layout

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.greenfodor.ppremotece.core.domain.layout.GridPreferences
import com.greenfodor.ppremotece.core.domain.layout.GridStep
import com.greenfodor.ppremotece.core.domain.layout.WidthClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.gridPreferencesDataStore by preferencesDataStore(name = "grid_preferences")

/**
 * [GridPreferences] in the `grid_preferences` DataStore, one key per width class
 * (`grid_step_compact`, `grid_step_medium`, `grid_step_expanded`); [GridStep.Default] when unset.
 */
class DataStoreGridPreferences(
    private val context: Context
) : GridPreferences {
    override fun gridStep(widthClass: WidthClass): Flow<GridStep> =
        context.gridPreferencesDataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { preferences ->
                preferences[keyOf(widthClass)]?.let { name -> GridStep.entries.firstOrNull { it.name == name } }
                    ?: GridStep.Default
            }.distinctUntilChanged()

    override suspend fun setGridStep(widthClass: WidthClass, step: GridStep) {
        context.gridPreferencesDataStore.edit { it[keyOf(widthClass)] = step.name }
    }

    private fun keyOf(widthClass: WidthClass): Preferences.Key<String> =
        when (widthClass) {
            WidthClass.COMPACT -> COMPACT
            WidthClass.MEDIUM -> MEDIUM
            WidthClass.EXPANDED -> EXPANDED
        }

    private companion object {
        val COMPACT = stringPreferencesKey("grid_step_compact")
        val MEDIUM = stringPreferencesKey("grid_step_medium")
        val EXPANDED = stringPreferencesKey("grid_step_expanded")
    }
}
