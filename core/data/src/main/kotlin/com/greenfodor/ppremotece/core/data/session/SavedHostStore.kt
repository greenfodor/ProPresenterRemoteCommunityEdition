package com.greenfodor.ppremotece.core.data.session

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.greenfodor.ppremotece.core.domain.model.ProPresenterHost
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

private val Context.savedHostDataStore by preferencesDataStore(name = "saved_host")

/**
 * The saved [host]; [namedByVersion] when its name is the `/version` name of a host entered by its
 * address, and [stayDisconnected] after a disconnect, until the next connect.
 */
data class SavedHost(
    val host: ProPresenterHost,
    val namedByVersion: Boolean,
    val stayDisconnected: Boolean
)

/** The one saved ProPresenter host. */
interface SavedHosts {
    suspend fun read(): SavedHost?

    /** Saves [host] and clears [SavedHost.stayDisconnected]. */
    suspend fun save(host: ProPresenterHost, namedByVersion: Boolean)

    suspend fun setStayDisconnected()

    suspend fun clear()
}

/** Stores the one saved ProPresenter host in DataStore. */
class SavedHostStore(
    private val context: Context
) : SavedHosts {
    override suspend fun read(): SavedHost? {
        val preferences = context.savedHostDataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .first()
        val address = preferences[ADDRESS]
        val port = preferences[PORT]
        return if (address != null && port != null) {
            SavedHost(
                host = ProPresenterHost(name = preferences[NAME] ?: address, address = address, port = port),
                namedByVersion = preferences[NAMED_BY_VERSION] ?: false,
                stayDisconnected = preferences[STAY_DISCONNECTED] ?: false
            )
        } else {
            null
        }
    }

    override suspend fun save(host: ProPresenterHost, namedByVersion: Boolean) {
        context.savedHostDataStore.edit {
            it[NAME] = host.name
            it[ADDRESS] = host.address
            it[PORT] = host.port
            it[NAMED_BY_VERSION] = namedByVersion
            it.remove(STAY_DISCONNECTED)
        }
    }

    override suspend fun setStayDisconnected() {
        context.savedHostDataStore.edit { it[STAY_DISCONNECTED] = true }
    }

    override suspend fun clear() {
        context.savedHostDataStore.edit { it.clear() }
    }

    private companion object {
        val NAME = stringPreferencesKey("name")
        val ADDRESS = stringPreferencesKey("address")
        val PORT = intPreferencesKey("port")
        val NAMED_BY_VERSION = booleanPreferencesKey("named_by_version")
        val STAY_DISCONNECTED = booleanPreferencesKey("stay_disconnected")
    }
}
