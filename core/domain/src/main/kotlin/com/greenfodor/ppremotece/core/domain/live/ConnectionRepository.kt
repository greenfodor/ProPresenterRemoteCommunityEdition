package com.greenfodor.ppremotece.core.domain.live

import com.greenfodor.ppremotece.core.domain.model.ConnectedHost
import com.greenfodor.ppremotece.core.domain.model.ProPresenterHost
import com.greenfodor.ppremotece.core.domain.model.ProPresenterVersion
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The one ProPresenter host the app is connected to, and the saved host it reconnects to on launch. */
interface ConnectionRepository {
    /** The connected host, null while disconnected. */
    val connectedHost: StateFlow<ConnectedHost?>

    suspend fun savedHost(): ProPresenterHost?

    /** Checks the host's `/version`; on success makes it the current host and saves it. */
    suspend fun connect(host: ProPresenterHost): Result<ProPresenterVersion, DataError.Network>

    /** Closes the live stream and keeps the saved host; nothing reconnects to it until the next connect. */
    suspend fun disconnect()
}

/** ProPresenter hosts advertised on the local network. */
interface HostDiscovery {
    fun discoveredHosts(): Flow<List<ProPresenterHost>>
}
