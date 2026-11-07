package com.greenfodor.ppremotece.core.domain.settings

import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The slide lock as a slide screen holds it, read from [preferences] in [scope]: on until it is read, and
 * on for good if the read fails (the store itself already answers its read errors with "on").
 * [toggle] saves the other state of the read lock; before a value is read, or after a failed read, it
 * saves nothing.
 */
class SlideLock(
    private val preferences: AppPreferences,
    private val scope: CoroutineScope
) {
    private val read: StateFlow<Boolean?> = preferences.slideLock()
        .map<Boolean, Boolean?> { it }
        .catch { emit(null) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Whether slide calls are blocked now. */
    val isOn: Boolean get() = read.value != false

    /** [isOn] as it changes. */
    val on: Flow<Boolean> = read.map { it != false }.distinctUntilChanged()

    /** Saves the other state of the read lock; [onFailure] gets a failed save. */
    fun toggle(onFailure: suspend (DataError.Local) -> Unit) {
        val current = read.value ?: return
        scope.launch {
            val saved = preferences.setSlideLock(!current)
            if (saved is Result.Failure) onFailure(saved.error)
        }
    }
}
