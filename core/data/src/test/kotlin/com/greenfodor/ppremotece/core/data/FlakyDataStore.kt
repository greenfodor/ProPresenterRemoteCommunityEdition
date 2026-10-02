package com.greenfodor.ppremotece.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.io.IOException

/** A preferences [DataStore] whose first [readFailures] reads throw [readError], and whose writes throw while [failWrites]. */
class FlakyDataStore(
    initial: Preferences = emptyPreferences(),
    var readFailures: Int = 0,
    private val readError: () -> IOException = { IOException("read failed") }
) : DataStore<Preferences> {
    val stored = MutableStateFlow(initial)
    var failWrites = false

    override val data: Flow<Preferences> = flow {
        if (readFailures > 0) {
            readFailures--
            throw readError()
        }
        emitAll(stored)
    }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        if (failWrites) throw IOException("write failed")
        return transform(stored.value).also { stored.value = it }
    }
}
