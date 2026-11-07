package com.greenfodor.ppremotece.core.domain.settings

import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.EmptyResult
import kotlinx.coroutines.flow.Flow

/**
 * The app's settings: keep-awake ([KeepAwake.Default] by default), the orientation
 * ([AppOrientation.Default] by default, and [AppOrientation.SYSTEM] for as long as it cannot be
 * read), auto-connect (on by default) and the slide lock (unlocked by default, and locked for as long
 * as it cannot be read); a failed write is reported.
 */
interface AppPreferences {
    fun keepAwake(): Flow<KeepAwake>

    suspend fun setKeepAwake(mode: KeepAwake): EmptyResult<DataError.Local>

    fun orientation(): Flow<AppOrientation>

    suspend fun setOrientation(orientation: AppOrientation): EmptyResult<DataError.Local>

    fun autoConnect(): Flow<Boolean>

    suspend fun setAutoConnect(enabled: Boolean): EmptyResult<DataError.Local>

    /** Whether the five slide calls are blocked. */
    fun slideLock(): Flow<Boolean>

    suspend fun setSlideLock(locked: Boolean): EmptyResult<DataError.Local>
}
