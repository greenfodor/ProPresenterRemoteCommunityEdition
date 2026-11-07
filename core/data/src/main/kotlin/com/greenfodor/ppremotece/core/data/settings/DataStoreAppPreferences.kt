package com.greenfodor.ppremotece.core.data.settings

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.EmptyResult
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.core.domain.settings.AppOrientation
import com.greenfodor.ppremotece.core.domain.settings.AppPreferences
import com.greenfodor.ppremotece.core.domain.settings.KeepAwake
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import java.io.IOException

private const val SLIDE_LOCK_KEY = "slide_lock"

/** What replaces a corrupt `app_settings` file: the defaults, with the slide lock on. */
internal fun appSettingsAfterCorruption(): Preferences = preferencesOf(booleanPreferencesKey(SLIDE_LOCK_KEY) to true)

/** The `app_settings` DataStore. */
internal val Context.appSettingsDataStore by preferencesDataStore(
    name = "app_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { appSettingsAfterCorruption() }
)

/**
 * [AppPreferences] in [dataStore] under `keep_awake`, `orientation`, `auto_connect` and `slide_lock`;
 * [KeepAwake.Default], [AppOrientation.Default], auto-connect on and the slide lock off when unset.
 * A corrupt file is replaced with the defaults and the slide lock on. A read error gives the defaults, and the slide lock
 * on; then, unless the file is corrupt, the values are read again after [READ_RETRY_DELAY_MS]. A write
 * error leaves the saved value unchanged and is returned.
 */
class DataStoreAppPreferences(
    private val dataStore: DataStore<Preferences>
) : AppPreferences {
    override fun keepAwake(): Flow<KeepAwake> =
        read { preferences ->
            KeepAwake.entries.firstOrNull { it.name == preferences[KEEP_AWAKE] } ?: KeepAwake.Default
        }

    override suspend fun setKeepAwake(mode: KeepAwake): EmptyResult<DataError.Local> = write {
        it[KEEP_AWAKE] =
            mode.name
    }

    override fun orientation(): Flow<AppOrientation> =
        read { preferences ->
            AppOrientation.entries.firstOrNull { it.name == preferences[ORIENTATION] } ?: AppOrientation.Default
        }

    override suspend fun setOrientation(orientation: AppOrientation): EmptyResult<DataError.Local> =
        write { it[ORIENTATION] = orientation.name }

    override fun autoConnect(): Flow<Boolean> = read { it[AUTO_CONNECT] ?: true }

    override suspend fun setAutoConnect(enabled: Boolean): EmptyResult<DataError.Local> =
        write { it[AUTO_CONNECT] = enabled }

    override fun slideLock(): Flow<Boolean> = read(unreadable = true) { it[SLIDE_LOCK] ?: false }

    override suspend fun setSlideLock(locked: Boolean): EmptyResult<DataError.Local> =
        write { it[SLIDE_LOCK] = locked }

    /** [value] of the preferences; on a read error [unreadable] when given, else [value] of empty preferences. */
    private fun <T> read(unreadable: T? = null, value: (Preferences) -> T): Flow<T> =
        dataStore.data
            .map<Preferences, T>(value)
            .retryWhen { cause, _ ->
                if (cause is IOException) emit(unreadable ?: value(emptyPreferences()))
                (cause is IOException && cause !is CorruptionException).also { retry ->
                    if (retry) delay(READ_RETRY_DELAY_MS)
                }
            }.catch { if (it !is CorruptionException) throw it }
            .distinctUntilChanged()

    private suspend fun write(change: (MutablePreferences) -> Unit): EmptyResult<DataError.Local> =
        try {
            dataStore.edit(change)
            Result.Success(Unit)
        } catch (_: IOException) {
            Result.Failure(DataError.Local.WRITE_FAILED)
        }

    private companion object {
        const val READ_RETRY_DELAY_MS = 1_000L
        val KEEP_AWAKE = stringPreferencesKey("keep_awake")
        val ORIENTATION = stringPreferencesKey("orientation")
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        val SLIDE_LOCK = booleanPreferencesKey(SLIDE_LOCK_KEY)
    }
}
