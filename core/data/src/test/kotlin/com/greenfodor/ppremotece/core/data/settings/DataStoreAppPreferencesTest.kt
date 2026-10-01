package com.greenfodor.ppremotece.core.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.greenfodor.ppremotece.core.domain.settings.KeepAwake
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
}
