package com.greenfodor.ppremotece.core.domain.settings

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.EmptyResult
import com.greenfodor.ppremotece.core.domain.result.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.IOException

class SlideLockTest {
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val preferences = Preferences()

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `the lock is on until it is read, and a toggle before then saves nothing`() = runBlocking<Unit> {
        preferences.lock.value = null
        val lock = SlideLock(preferences, scope)

        assertThat(lock.isOn).isTrue()
        assertThat(lock.on.first()).isTrue()
        lock.toggle {}

        assertThat(preferences.writes).isEmpty()
    }

    @Test
    fun `the lock follows the saved value`() = runBlocking<Unit> {
        val lock = SlideLock(preferences, scope)

        assertThat(lock.isOn).isFalse()
        assertThat(lock.on.first()).isFalse()
        preferences.lock.value = true

        assertThat(lock.isOn).isTrue()
        assertThat(lock.on.first()).isTrue()
    }

    @Test
    fun `a toggle saves the other state of the read lock`() = runBlocking<Unit> {
        val lock = SlideLock(preferences, scope)

        lock.toggle {}
        assertThat(lock.isOn).isTrue()
        lock.toggle {}

        assertThat(preferences.writes).containsExactly(true, false)
        assertThat(lock.isOn).isFalse()
    }

    @Test
    fun `the lock is on when it cannot be read, and a toggle saves nothing`() = runBlocking<Unit> {
        preferences.failRead = true
        val lock = SlideLock(preferences, scope)

        assertThat(lock.isOn).isTrue()
        assertThat(lock.on.first()).isTrue()
        lock.toggle {}

        assertThat(preferences.writes).isEmpty()
    }

    @Test
    fun `a failed save is reported and the lock is unchanged`() = runBlocking<Unit> {
        preferences.failWrites = true
        val lock = SlideLock(preferences, scope)
        val failures = mutableListOf<DataError.Local>()

        lock.toggle { failures += it }

        assertThat(failures).containsExactly(DataError.Local.WRITE_FAILED)
        assertThat(lock.isOn).isFalse()
    }

    private class Preferences : AppPreferences {
        val lock = MutableStateFlow<Boolean?>(false)
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
}
