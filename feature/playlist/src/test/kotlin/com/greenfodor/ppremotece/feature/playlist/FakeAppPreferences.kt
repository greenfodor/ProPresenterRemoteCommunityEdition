package com.greenfodor.ppremotece.feature.playlist

import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.EmptyResult
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.core.domain.settings.AppOrientation
import com.greenfodor.ppremotece.core.domain.settings.AppPreferences
import com.greenfodor.ppremotece.core.domain.settings.KeepAwake
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import java.io.IOException

/**
 * The slide lock in [lock] (null: not read yet); [failRead] makes the read throw, [failWrites] makes a write fail.
 * Writes are recorded in [writes]. The other settings are not served.
 */
class FakeAppPreferences(
    locked: Boolean? = false
) : AppPreferences {
    val lock = MutableStateFlow(locked)
    val writes = mutableListOf<Boolean>()
    var failRead = false
    var failWrites = false

    override fun slideLock(): Flow<Boolean> =
        if (failRead) flow { throw IOException("read failed") } else lock.filterNotNull()

    override suspend fun setSlideLock(locked: Boolean): EmptyResult<DataError.Local> {
        writes += locked
        if (failWrites) return Result.Failure(DataError.Local.WRITE_FAILED)
        lock.value = locked
        return Result.Success(Unit)
    }

    override fun keepAwake(): Flow<KeepAwake> = flowOf(KeepAwake.Default)

    override suspend fun setKeepAwake(mode: KeepAwake): EmptyResult<DataError.Local> = Result.Success(Unit)

    override fun orientation(): Flow<AppOrientation> = flowOf(AppOrientation.Default)

    override suspend fun setOrientation(orientation: AppOrientation): EmptyResult<DataError.Local> =
        Result.Success(Unit)

    override fun autoConnect(): Flow<Boolean> = flowOf(true)

    override suspend fun setAutoConnect(enabled: Boolean): EmptyResult<DataError.Local> = Result.Success(Unit)
}
