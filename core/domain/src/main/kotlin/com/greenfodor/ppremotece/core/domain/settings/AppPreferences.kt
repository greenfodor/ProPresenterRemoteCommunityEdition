package com.greenfodor.ppremotece.core.domain.settings

import kotlinx.coroutines.flow.Flow

/** The app's settings: keep-awake ([KeepAwake.Default] by default) and auto-connect (on by default). */
interface AppPreferences {
    fun keepAwake(): Flow<KeepAwake>

    suspend fun setKeepAwake(mode: KeepAwake)

    fun autoConnect(): Flow<Boolean>

    suspend fun setAutoConnect(enabled: Boolean)
}
