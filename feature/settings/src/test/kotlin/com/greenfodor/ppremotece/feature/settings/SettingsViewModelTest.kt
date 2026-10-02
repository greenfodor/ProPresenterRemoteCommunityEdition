package com.greenfodor.ppremotece.feature.settings

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.live.ConnectionRepository
import com.greenfodor.ppremotece.core.domain.model.ConnectedHost
import com.greenfodor.ppremotece.core.domain.model.ProPresenterHost
import com.greenfodor.ppremotece.core.domain.model.ProPresenterVersion
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.EmptyResult
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.core.domain.settings.AppPreferences
import com.greenfodor.ppremotece.core.domain.settings.KeepAwake
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val host = ProPresenterHost(name = "Host 01", address = "192.0.2.14", port = 60113)
    private val preferences = FakeAppPreferences()
    private val connections = FakeConnections(
        ConnectedHost(
            host,
            ProPresenterVersion(name = "Host 01", hostDescription = "ProPresenter 21.4.2", apiVersion = "v1")
        )
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = SettingsViewModel(preferences, connections)

    @Test
    fun `the connection section shows the connected host`() = runTest(dispatcher) {
        viewModel().state.test {
            val state = expectMostRecentItem()
            assertThat(state.hostName).isEqualTo("Host 01")
            assertThat(state.hostAddress).isEqualTo("192.0.2.14:60113")
            assertThat(state.hostDescription).isEqualTo("ProPresenter 21.4.2")
        }
    }

    @Test
    fun `a keep awake choice is saved and shown`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.state.test {
            assertThat(expectMostRecentItem().keepAwake).isEqualTo(KeepAwake.REMOTE_ONLY)
            viewModel.onAction(SettingsAction.OnKeepAwakeChange(KeepAwake.ALWAYS))

            assertThat(preferences.keepAwake.value).isEqualTo(KeepAwake.ALWAYS)
            assertThat(expectMostRecentItem().keepAwake).isEqualTo(KeepAwake.ALWAYS)
        }
    }

    @Test
    fun `the auto-connect switch is saved and shown`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.state.test {
            assertThat(expectMostRecentItem().autoConnect).isTrue()
            viewModel.onAction(SettingsAction.OnAutoConnectChange(false))

            assertThat(preferences.autoConnect.value).isFalse()
            assertThat(expectMostRecentItem().autoConnect).isFalse()
        }
    }

    @Test
    fun `disconnect asks first and a dismissed dialog keeps the connection`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.state.test {
            viewModel.onAction(SettingsAction.OnDisconnectClick)
            assertThat(expectMostRecentItem().confirmingDisconnect).isTrue()

            viewModel.onAction(SettingsAction.OnDisconnectDismiss)
            assertThat(expectMostRecentItem().confirmingDisconnect).isFalse()
        }
        assertThat(connections.disconnects).isEqualTo(0)
    }

    @Test
    fun `a confirmed disconnect disconnects and reports it`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(SettingsAction.OnDisconnectClick)
            viewModel.onAction(SettingsAction.OnDisconnectConfirm)

            assertThat(awaitItem()).isEqualTo(SettingsEvent.Disconnected)
        }
        assertThat(connections.disconnects).isEqualTo(1)
        assertThat(viewModel.state.value.confirmingDisconnect).isFalse()
    }

    @Test
    fun `a keep awake choice that can't be saved shows the setting message`() = runTest(dispatcher) {
        preferences.failWrites = true
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(SettingsAction.OnKeepAwakeChange(KeepAwake.ALWAYS))

            assertThat(awaitItem().messageId()).isEqualTo(DesignR.string.setting_not_saved)
        }
    }

    @Test
    fun `an auto-connect switch that can't be saved shows the setting message`() = runTest(dispatcher) {
        preferences.failWrites = true
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(SettingsAction.OnAutoConnectChange(false))

            assertThat(awaitItem().messageId()).isEqualTo(DesignR.string.setting_not_saved)
        }
    }

    private fun SettingsEvent.messageId() = ((this as SettingsEvent.ShowError).message as UiText.StringResource).id

    private class FakeAppPreferences : AppPreferences {
        val keepAwake = MutableStateFlow(KeepAwake.REMOTE_ONLY)
        val autoConnect = MutableStateFlow(true)

        override fun keepAwake(): Flow<KeepAwake> = keepAwake

        var failWrites = false

        override suspend fun setKeepAwake(mode: KeepAwake): EmptyResult<DataError.Local> {
            if (failWrites) return Result.Failure(DataError.Local.WRITE_FAILED)
            keepAwake.value = mode
            return Result.Success(Unit)
        }

        override fun autoConnect(): Flow<Boolean> = autoConnect

        override suspend fun setAutoConnect(enabled: Boolean): EmptyResult<DataError.Local> {
            if (failWrites) return Result.Failure(DataError.Local.WRITE_FAILED)
            autoConnect.value = enabled
            return Result.Success(Unit)
        }
    }

    private class FakeConnections(
        connected: ConnectedHost
    ) : ConnectionRepository {
        var disconnects = 0
        override val connectedHost = MutableStateFlow<ConnectedHost?>(connected)

        override suspend fun savedHost(): ProPresenterHost? = connectedHost.value?.host

        override suspend fun stayDisconnected(): Boolean = false

        override suspend fun connect(host: ProPresenterHost): Result<ProPresenterVersion, DataError.Network> =
            Result.Failure(DataError.Network.TIMEOUT)

        override suspend fun disconnect() {
            disconnects++
            connectedHost.value = null
        }
    }
}
