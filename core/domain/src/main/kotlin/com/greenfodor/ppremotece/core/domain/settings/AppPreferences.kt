package com.greenfodor.ppremotece.core.domain.settings

import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.EmptyResult
import kotlinx.coroutines.flow.Flow

/** The app's settings: keep-awake ([KeepAwake.Default] by default) and auto-connect (on by default); a failed write is reported. */
interface AppPreferences {
    fun keepAwake(): Flow<KeepAwake>

    suspend fun setKeepAwake(mode: KeepAwake): EmptyResult<DataError.Local>

    fun autoConnect(): Flow<Boolean>

    suspend fun setAutoConnect(enabled: Boolean): EmptyResult<DataError.Local>
}
