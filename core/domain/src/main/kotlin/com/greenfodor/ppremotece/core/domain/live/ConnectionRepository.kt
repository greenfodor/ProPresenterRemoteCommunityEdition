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

    /** Whether the last [disconnect] asked to stay disconnected; cleared by the next successful [connect]. */
    suspend fun stayDisconnected(): Boolean

    /**
     * Checks the host's `/version`; on success makes it the current host and saves it. A host whose
     * name is its address is named after the `/version` name when that is not blank, and is renamed
     * on each later connect.
     */
    suspend fun connect(host: ProPresenterHost): Result<ProPresenterVersion, DataError.Network>

    /**
     * Closes the live stream, after any reconnect to the saved host in progress, and keeps the saved
     * host with [stayDisconnected] set; this session does not reconnect to it on its own until the
     * next connect.
     */
    suspend fun disconnect()
}

/** ProPresenter hosts advertised on the local network. */
interface HostDiscovery {
    fun discoveredHosts(): Flow<List<ProPresenterHost>>
}
