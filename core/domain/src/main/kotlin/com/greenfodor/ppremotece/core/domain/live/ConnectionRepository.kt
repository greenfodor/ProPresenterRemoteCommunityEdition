package com.greenfodor.ppremotece.core.domain.live

import com.greenfodor.ppremotece.core.domain.model.ProPresenterHost
import com.greenfodor.ppremotece.core.domain.model.ProPresenterVersion
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.Result
import kotlinx.coroutines.flow.Flow

/** The one ProPresenter host the app is connected to, and the saved host it reconnects to on launch. */
interface ConnectionRepository {
    suspend fun savedHost(): ProPresenterHost?

    /** Checks the host's `/version`; on success makes it the current host and saves it. */
    suspend fun connect(host: ProPresenterHost): Result<ProPresenterVersion, DataError.Network>

    /** Closes the live stream and clears the saved host. */
    suspend fun disconnect()
}

/** ProPresenter hosts advertised on the local network. */
interface HostDiscovery {
    fun discoveredHosts(): Flow<List<ProPresenterHost>>
}
