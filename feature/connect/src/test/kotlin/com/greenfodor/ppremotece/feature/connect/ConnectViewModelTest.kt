package com.greenfodor.ppremotece.feature.connect

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import com.greenfodor.ppremotece.core.designsystem.R
import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.live.ConnectionRepository
import com.greenfodor.ppremotece.core.domain.live.HostDiscovery
import com.greenfodor.ppremotece.core.domain.model.ConnectedHost
import com.greenfodor.ppremotece.core.domain.model.ProPresenterHost
import com.greenfodor.ppremotece.core.domain.model.ProPresenterVersion
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.EmptyResult
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.core.domain.settings.AppPreferences
import com.greenfodor.ppremotece.core.domain.settings.KeepAwake
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectViewModelTest {
    private val savedHost = ProPresenterHost(name = "Host 01", address = "192.0.2.14", port = 60113)
    private val connections = object : ConnectionRepository {
        val attempts = mutableListOf<ProPresenterHost>()

        override val connectedHost = MutableStateFlow<ConnectedHost?>(null)

        var savedHostRead = CompletableDeferred(Unit)

        override suspend fun savedHost(): ProPresenterHost {
            savedHostRead.await()
            return this@ConnectViewModelTest.savedHost
        }

        var stayDisconnected = false

        override suspend fun stayDisconnected(): Boolean = stayDisconnected

        override suspend fun connect(host: ProPresenterHost): Result<ProPresenterVersion, DataError.Network> {
            attempts += host
            return Result.Failure(DataError.Network.TIMEOUT)
        }

        override suspend fun disconnect() = Unit
    }
    private val preferences = object : AppPreferences {
        val autoConnect = MutableStateFlow(true)

        override fun keepAwake(): Flow<KeepAwake> = flowOf(KeepAwake.REMOTE_ONLY)

        override suspend fun setKeepAwake(mode: KeepAwake): EmptyResult<DataError.Local> = Result.Success(Unit)

        override fun autoConnect(): Flow<Boolean> = autoConnect

        override suspend fun setAutoConnect(enabled: Boolean): EmptyResult<DataError.Local> {
            autoConnect.value = enabled
            return Result.Success(Unit)
        }
    }
    private val discovery = object : HostDiscovery {
        override fun discoveredHosts(): Flow<List<ProPresenterHost>> = emptyFlow()
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `failed auto-connect leaves the saved host filled in with the error`() = runTest {
        val viewModel = ConnectViewModel(connections, discovery, preferences)

        viewModel.events.test {
            viewModel.onAction(ConnectAction.OnStart(permissionGranted = true, autoConnect = true))

            val state = viewModel.state.value
            assertThat(connections.attempts).isEqualTo(listOf(savedHost))
            assertThat(state.address).isEqualTo("192.0.2.14")
            assertThat(state.port).isEqualTo("60113")
            assertThat(state.isConnecting).isFalse()
            assertThat((state.error as? UiText.StringResource)?.id).isEqualTo(R.string.error_timeout)
            expectNoEvents()
        }
    }

    @Test
    fun `a connect screen reached by disconnect shows the saved host without connecting`() = runTest {
        val viewModel = ConnectViewModel(connections, discovery, preferences)

        viewModel.onAction(ConnectAction.OnStart(permissionGranted = true, autoConnect = false))

        assertSavedHostWaiting(viewModel.state.value)
    }

    @Test
    fun `with auto-connect off the launch connect screen shows the saved host without connecting`() = runTest {
        preferences.autoConnect.value = false
        val viewModel = ConnectViewModel(connections, discovery, preferences)

        viewModel.onAction(ConnectAction.OnStart(permissionGranted = true, autoConnect = true))

        assertSavedHostWaiting(viewModel.state.value)
    }

    @Test
    fun `a launch after a disconnect shows the saved host without connecting`() = runTest {
        connections.stayDisconnected = true
        val viewModel = ConnectViewModel(connections, discovery, preferences)

        viewModel.onAction(ConnectAction.OnStart(permissionGranted = true, autoConnect = true))

        assertSavedHostWaiting(viewModel.state.value)
    }

    @Test
    fun `connecting to the prefilled saved host keeps its name`() = runTest {
        val viewModel = ConnectViewModel(connections, discovery, preferences)
        viewModel.onAction(ConnectAction.OnStart(permissionGranted = true, autoConnect = false))

        viewModel.onAction(ConnectAction.OnConnectClick)

        assertThat(connections.attempts).isEqualTo(listOf(savedHost))
    }

    @Test
    fun `an address typed before the saved host is read is kept`() = runTest {
        connections.savedHostRead = CompletableDeferred()
        val viewModel = ConnectViewModel(connections, discovery, preferences)
        viewModel.onAction(ConnectAction.OnStart(permissionGranted = true, autoConnect = false))

        viewModel.onAction(ConnectAction.OnAddressChange("198.51.100.7"))
        connections.savedHostRead.complete(Unit)

        assertThat(viewModel.state.value.address).isEqualTo("198.51.100.7")
        assertThat(viewModel.state.value.port).isEqualTo("")
    }

    private fun assertSavedHostWaiting(state: ConnectState) {
        assertThat(connections.attempts).isEmpty()
        assertThat(state.address).isEqualTo("192.0.2.14")
        assertThat(state.port).isEqualTo("60113")
        assertThat(state.isConnecting).isFalse()
        assertThat(state.error).isNull()
    }
}
