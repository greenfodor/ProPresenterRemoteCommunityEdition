package com.greenfodor.ppremotece.feature.connect

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import com.greenfodor.ppremotece.core.designsystem.R
import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.live.ConnectionRepository
import com.greenfodor.ppremotece.core.domain.live.HostDiscovery
import com.greenfodor.ppremotece.core.domain.model.ProPresenterHost
import com.greenfodor.ppremotece.core.domain.model.ProPresenterVersion
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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

        override suspend fun savedHost(): ProPresenterHost = this@ConnectViewModelTest.savedHost

        override suspend fun connect(host: ProPresenterHost): Result<ProPresenterVersion, DataError.Network> {
            attempts += host
            return Result.Failure(DataError.Network.TIMEOUT)
        }

        override suspend fun disconnect() = Unit
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
        val viewModel = ConnectViewModel(connections, discovery)

        viewModel.events.test {
            viewModel.onAction(ConnectAction.OnStart(permissionGranted = true))

            val state = viewModel.state.value
            assertThat(connections.attempts).isEqualTo(listOf(savedHost))
            assertThat(state.address).isEqualTo("192.0.2.14")
            assertThat(state.port).isEqualTo("60113")
            assertThat(state.isConnecting).isFalse()
            assertThat((state.error as? UiText.StringResource)?.id).isEqualTo(R.string.error_timeout)
            expectNoEvents()
        }
    }
}
