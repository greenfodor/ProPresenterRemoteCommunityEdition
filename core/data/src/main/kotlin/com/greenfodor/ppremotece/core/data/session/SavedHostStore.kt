package com.greenfodor.ppremotece.core.data.session

import android.content.Context
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

/** Stores the one saved ProPresenter host in DataStore. */
class SavedHostStore(
    private val context: Context
) {
    suspend fun read(): ProPresenterHost? {
        val preferences = context.savedHostDataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .first()
        val address = preferences[ADDRESS]
        val port = preferences[PORT]
        return if (address != null && port != null) {
            ProPresenterHost(name = preferences[NAME] ?: address, address = address, port = port)
        } else {
            null
        }
    }

    suspend fun save(host: ProPresenterHost) {
        context.savedHostDataStore.edit {
            it[NAME] = host.name
            it[ADDRESS] = host.address
            it[PORT] = host.port
        }
    }

    suspend fun clear() {
        context.savedHostDataStore.edit { it.clear() }
    }

    private companion object {
        val NAME = stringPreferencesKey("name")
        val ADDRESS = stringPreferencesKey("address")
        val PORT = intPreferencesKey("port")
    }
}
