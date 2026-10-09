package com.greenfodor.ppremotece.feature.connect

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
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
import com.greenfodor.ppremotece.core.domain.settings.AppOrientation
import com.greenfodor.ppremotece.core.domain.settings.AppPreferences
import com.greenfodor.ppremotece.core.domain.settings.KeepAwake
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
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

        var saved: ProPresenterHost? = this@ConnectViewModelTest.savedHost

        override suspend fun savedHost(): ProPresenterHost? {
            savedHostRead.await()
            return saved
        }

        var stayDisconnected = false

        override suspend fun stayDisconnected(): Boolean = stayDisconnected

        var connectResult: Result<ProPresenterVersion, DataError.Network> = Result.Failure(DataError.Network.TIMEOUT)

        var connectAnswered = CompletableDeferred(Unit)

        override suspend fun connect(host: ProPresenterHost): Result<ProPresenterVersion, DataError.Network> {
            attempts += host
            connectAnswered.await()
            return connectResult
        }

        override suspend fun disconnect() = Unit
    }
    private val preferences = object : AppPreferences {
        val autoConnect = MutableStateFlow(true)

        override fun keepAwake(): Flow<KeepAwake> = flowOf(KeepAwake.REMOTE_ONLY)

        override suspend fun setKeepAwake(mode: KeepAwake): EmptyResult<DataError.Local> = Result.Success(Unit)

        override fun orientation(): Flow<AppOrientation> = flowOf(AppOrientation.SYSTEM)

        override suspend fun setOrientation(orientation: AppOrientation): EmptyResult<DataError.Local> =
            Result.Success(Unit)

        override fun autoConnect(): Flow<Boolean> = autoConnect

        override suspend fun setAutoConnect(enabled: Boolean): EmptyResult<DataError.Local> {
            autoConnect.value = enabled
            return Result.Success(Unit)
        }
    }
    private val otherHost = ProPresenterHost(name = "Host 02", address = "192.0.2.15", port = 60113)
    private val discovered = MutableStateFlow<List<ProPresenterHost>?>(null)
    private val discovery = object : HostDiscovery {
        override fun discoveredHosts(): Flow<List<ProPresenterHost>> = discovered.filterNotNull()
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
            assertThat(awaitItem()).isEqualTo(ConnectEvent.StartupResolved)
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

    @Test
    fun `start-up is resolved at once without a saved host`() = runTest {
        connections.saved = null
        val viewModel = ConnectViewModel(connections, discovery, preferences)

        viewModel.events.test {
            viewModel.onAction(ConnectAction.OnStart(permissionGranted = true, autoConnect = true))

            assertThat(awaitItem()).isEqualTo(ConnectEvent.StartupResolved)
            expectNoEvents()
        }
        assertThat(connections.attempts).isEmpty()
    }

    @Test
    fun `start-up is resolved at once when the route does not auto-connect`() = runTest {
        val viewModel = ConnectViewModel(connections, discovery, preferences)

        viewModel.events.test {
            viewModel.onAction(ConnectAction.OnStart(permissionGranted = true, autoConnect = false))

            assertThat(awaitItem()).isEqualTo(ConnectEvent.StartupResolved)
            expectNoEvents()
        }
    }

    @Test
    fun `start-up is resolved at once with auto-connect off`() = runTest {
        preferences.autoConnect.value = false
        val viewModel = ConnectViewModel(connections, discovery, preferences)

        viewModel.events.test {
            viewModel.onAction(ConnectAction.OnStart(permissionGranted = true, autoConnect = true))

            assertThat(awaitItem()).isEqualTo(ConnectEvent.StartupResolved)
            expectNoEvents()
        }
    }

    @Test
    fun `start-up is resolved at once after a disconnect`() = runTest {
        connections.stayDisconnected = true
        val viewModel = ConnectViewModel(connections, discovery, preferences)

        viewModel.events.test {
            viewModel.onAction(ConnectAction.OnStart(permissionGranted = true, autoConnect = true))

            assertThat(awaitItem()).isEqualTo(ConnectEvent.StartupResolved)
            expectNoEvents()
        }
    }

    @Test
    fun `start-up is resolved at once when the permission has to be requested and not again by the connect`() =
        runTest {
            val viewModel = ConnectViewModel(connections, discovery, preferences)

            viewModel.events.test {
                viewModel.onAction(ConnectAction.OnStart(permissionGranted = false, autoConnect = true))

                assertThat(awaitItem()).isEqualTo(ConnectEvent.StartupResolved)
                assertThat(awaitItem()).isEqualTo(ConnectEvent.RequestLocalNetworkPermission)
                assertThat(connections.attempts).isEmpty()

                viewModel.onAction(ConnectAction.OnPermissionResult(granted = true))

                assertThat(connections.attempts).isEqualTo(listOf(savedHost))
                expectNoEvents()
            }
        }

    @Test
    fun `start-up is resolved once the auto-connect succeeds, after the connected event`() = runTest {
        connections.connectResult = Result.Success(ProPresenterVersion("Host 01", "ProPresenter 21.4.2", "v1"))
        connections.connectAnswered = CompletableDeferred()
        val viewModel = ConnectViewModel(connections, discovery, preferences)

        viewModel.events.test {
            viewModel.onAction(ConnectAction.OnStart(permissionGranted = true, autoConnect = true))
            expectNoEvents()

            connections.connectAnswered.complete(Unit)

            assertThat(awaitItem()).isEqualTo(ConnectEvent.Connected)
            assertThat(awaitItem()).isEqualTo(ConnectEvent.StartupResolved)
            expectNoEvents()
        }
    }

    @Test
    fun `start-up is resolved once the auto-connect fails and not again by a later attempt`() = runTest {
        connections.connectAnswered = CompletableDeferred()
        val viewModel = ConnectViewModel(connections, discovery, preferences)

        viewModel.events.test {
            viewModel.onAction(ConnectAction.OnStart(permissionGranted = true, autoConnect = true))
            expectNoEvents()

            connections.connectAnswered.complete(Unit)

            assertThat(awaitItem()).isEqualTo(ConnectEvent.StartupResolved)

            viewModel.onAction(ConnectAction.OnSavedHostClick)

            assertThat(connections.attempts).isEqualTo(listOf(savedHost, savedHost))
            expectNoEvents()
        }
    }

    @Test
    fun `the saved host is the last-used card and the auto-connect is shown on it`() = runTest {
        connections.connectAnswered = CompletableDeferred()
        val viewModel = ConnectViewModel(connections, discovery, preferences)

        viewModel.onAction(ConnectAction.OnStart(permissionGranted = true, autoConnect = true))

        assertThat(viewModel.state.value.savedHost).isEqualTo(savedHost)
        assertThat(viewModel.state.value.target).isEqualTo(ConnectTarget.LastUsed)
        assertThat(viewModel.state.value.isConnecting).isTrue()
    }

    @Test
    fun `a tap on the last-used card connects to the saved host with its name`() = runTest {
        val viewModel = ConnectViewModel(connections, discovery, preferences)
        viewModel.onAction(ConnectAction.OnStart(permissionGranted = true, autoConnect = false))

        viewModel.onAction(ConnectAction.OnSavedHostClick)

        assertThat(connections.attempts).isEqualTo(listOf(savedHost))
        assertThat(viewModel.state.value.target).isEqualTo(ConnectTarget.LastUsed)
    }

    @Test
    fun `a discovered host with the saved host's address and port is not listed again`() = runTest {
        val viewModel = ConnectViewModel(connections, discovery, preferences)
        viewModel.onAction(ConnectAction.OnStart(permissionGranted = true, autoConnect = false))

        discovered.value = listOf(savedHost.copy(name = "Host 01 on the network"), otherHost)

        assertThat(viewModel.state.value.otherHosts).isEqualTo(listOf(otherHost))
    }

    @Test
    fun `an error is attached to the host that was tried and cleared by the next attempt`() = runTest {
        val viewModel = ConnectViewModel(connections, discovery, preferences)
        viewModel.onAction(ConnectAction.OnStart(permissionGranted = true, autoConnect = false))

        viewModel.onAction(ConnectAction.OnDiscoveredHostClick(otherHost))

        assertThat(viewModel.state.value.target).isEqualTo(ConnectTarget.Discovered(otherHost))
        assertThat((viewModel.state.value.error as? UiText.StringResource)?.id).isEqualTo(R.string.error_timeout)

        connections.connectAnswered = CompletableDeferred()
        viewModel.onAction(ConnectAction.OnSavedHostClick)

        assertThat(viewModel.state.value.target).isEqualTo(ConnectTarget.LastUsed)
        assertThat(viewModel.state.value.error).isNull()
        assertThat(viewModel.state.value.isConnecting).isTrue()
    }

    @Test
    fun `a validation error is attached to the manual form`() = runTest {
        connections.saved = null
        val viewModel = ConnectViewModel(connections, discovery, preferences)
        viewModel.onAction(ConnectAction.OnStart(permissionGranted = true, autoConnect = false))

        viewModel.onAction(ConnectAction.OnConnectClick)

        assertThat(viewModel.state.value.target).isEqualTo(ConnectTarget.Manual)
        assertThat((viewModel.state.value.error as? UiText.StringResource)?.id)
            .isEqualTo(com.greenfodor.ppremotece.feature.connect.R.string.connect_error_address)
    }

    @Test
    fun `manual entry starts open only with no saved and no discovered host`() = runTest {
        connections.saved = null
        val nothingKnown = ConnectViewModel(connections, discovery, preferences)
        nothingKnown.onAction(ConnectAction.OnStart(permissionGranted = true, autoConnect = true))
        assertThat(nothingKnown.state.value.manualOpen).isTrue()

        discovered.value = listOf(otherHost)
        val discoveredOnly = ConnectViewModel(connections, discovery, preferences)
        discoveredOnly.onAction(ConnectAction.OnStart(permissionGranted = true, autoConnect = true))
        assertThat(discoveredOnly.state.value.manualOpen).isFalse()

        connections.saved = savedHost
        discovered.value = null
        val savedOnly = ConnectViewModel(connections, discovery, preferences)
        savedOnly.onAction(ConnectAction.OnStart(permissionGranted = true, autoConnect = false))
        assertThat(savedOnly.state.value.manualOpen).isFalse()
    }

    @Test
    fun `the enter an address row opens and closes manual entry`() = runTest {
        val viewModel = ConnectViewModel(connections, discovery, preferences)
        viewModel.onAction(ConnectAction.OnStart(permissionGranted = true, autoConnect = false))

        viewModel.onAction(ConnectAction.OnManualToggle)
        assertThat(viewModel.state.value.manualOpen).isTrue()

        viewModel.onAction(ConnectAction.OnManualToggle)
        assertThat(viewModel.state.value.manualOpen).isFalse()
    }

    private fun assertSavedHostWaiting(state: ConnectState) {
        assertThat(connections.attempts).isEmpty()
        assertThat(state.address).isEqualTo("192.0.2.14")
        assertThat(state.port).isEqualTo("60113")
        assertThat(state.isConnecting).isFalse()
        assertThat(state.error).isNull()
    }
}
