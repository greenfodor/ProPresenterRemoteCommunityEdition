package com.greenfodor.ppremotece.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.greenfodor.ppremotece.core.domain.settings.AppPreferences
import com.greenfodor.ppremotece.core.domain.settings.KeepAwake
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/** The `app_settings` DataStore. */
internal val Context.appSettingsDataStore by preferencesDataStore(name = "app_settings")

/**
 * [AppPreferences] in [dataStore] under `keep_awake` and `auto_connect`; [KeepAwake.Default]
 * and auto-connect on when unset. Read and write errors leave the saved values unchanged.
 */
class DataStoreAppPreferences(
    private val dataStore: DataStore<Preferences>
) : AppPreferences {
    override fun keepAwake(): Flow<KeepAwake> =
        read { preferences ->
            KeepAwake.entries.firstOrNull { it.name == preferences[KEEP_AWAKE] } ?: KeepAwake.Default
        }

    override suspend fun setKeepAwake(mode: KeepAwake) {
        write { it[KEEP_AWAKE] = mode.name }
    }

    override fun autoConnect(): Flow<Boolean> = read { it[AUTO_CONNECT] ?: true }

    override suspend fun setAutoConnect(enabled: Boolean) {
        write { it[AUTO_CONNECT] = enabled }
    }

    private fun <T> read(value: (Preferences) -> T): Flow<T> =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map(value)
            .distinctUntilChanged()

    private suspend fun write(change: (MutablePreferences) -> Unit) {
        try {
            dataStore.edit(change)
        } catch (_: IOException) {
            // The previously saved value stays in place.
        }
    }

    private companion object {
        val KEEP_AWAKE = stringPreferencesKey("keep_awake")
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
    }
}
