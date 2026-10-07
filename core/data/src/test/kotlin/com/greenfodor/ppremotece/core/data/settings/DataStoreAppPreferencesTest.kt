package com.greenfodor.ppremotece.core.data.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.greenfodor.ppremotece.core.data.FlakyDataStore
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.core.domain.settings.AppOrientation
import com.greenfodor.ppremotece.core.domain.settings.KeepAwake
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DataStoreAppPreferencesTest {
    @TempDir
    lateinit var dir: File

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferences by lazy {
        DataStoreAppPreferences(
            PreferenceDataStoreFactory.create(scope = scope) { dir.resolve("app_settings.preferences_pb") }
        )
    }

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `keep awake is remote only and auto-connect is on until saved`() = runBlocking {
        assertThat(preferences.keepAwake().first()).isEqualTo(KeepAwake.REMOTE_ONLY)
        assertThat(preferences.autoConnect().first()).isTrue()
    }

    @Test
    fun `the orientation follows the system until saved`() = runBlocking<Unit> {
        assertThat(preferences.orientation().first()).isEqualTo(AppOrientation.SYSTEM)
    }

    @Test
    fun `each orientation is saved and read back`() = runBlocking<Unit> {
        listOf(AppOrientation.LANDSCAPE, AppOrientation.PORTRAIT, AppOrientation.SYSTEM).forEach { orientation ->
            assertThat(preferences.setOrientation(orientation)).isEqualTo(Result.Success(Unit))

            assertThat(preferences.orientation().first()).isEqualTo(orientation)
        }
    }

    @Test
    fun `each keep awake mode is saved`() = runBlocking {
        KeepAwake.entries.forEach { mode ->
            preferences.setKeepAwake(mode)

            assertThat(preferences.keepAwake().first()).isEqualTo(mode)
        }
    }

    @Test
    fun `auto-connect is saved`() = runBlocking {
        preferences.setAutoConnect(false)
        assertThat(preferences.autoConnect().first()).isFalse()

        preferences.setAutoConnect(true)
        assertThat(preferences.autoConnect().first()).isTrue()
    }

    @Test
    fun `a read error gives the default, then the saved value is read again`() = runTest {
        val dataStore = FlakyDataStore(preferencesOf(stringPreferencesKey("keep_awake") to "ALWAYS"), readFailures = 1)

        DataStoreAppPreferences(dataStore).keepAwake().test {
            assertThat(awaitItem()).isEqualTo(KeepAwake.REMOTE_ONLY)
            assertThat(awaitItem()).isEqualTo(KeepAwake.ALWAYS)
        }
    }

    @Test
    fun `a failed write is reported`() = runTest {
        val preferences = DataStoreAppPreferences(FlakyDataStore().apply { failWrites = true })

        assertThat(preferences.setKeepAwake(KeepAwake.ALWAYS)).isEqualTo(Result.Failure(DataError.Local.WRITE_FAILED))
        assertThat(preferences.setAutoConnect(false)).isEqualTo(Result.Failure(DataError.Local.WRITE_FAILED))
    }

    @Test
    fun `a saved value is reported as saved`() = runBlocking<Unit> {
        assertThat(preferences.setAutoConnect(false)).isInstanceOf<Result.Success<Unit>>()
    }

    @Test
    fun `a corrupt file gives the default without reading again`() = runTest {
        val dataStore = FlakyDataStore(readFailures = 2, readError = { CorruptionException("corrupt") })

        DataStoreAppPreferences(dataStore).keepAwake().test {
            assertThat(awaitItem()).isEqualTo(KeepAwake.REMOTE_ONLY)
            awaitComplete()
        }
        assertThat(dataStore.readFailures).isEqualTo(1)
    }
}
