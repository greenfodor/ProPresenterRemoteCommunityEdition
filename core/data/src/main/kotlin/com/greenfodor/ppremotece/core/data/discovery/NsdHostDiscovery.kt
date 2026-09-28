package com.greenfodor.ppremotece.core.data.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.annotation.RequiresApi
import com.greenfodor.ppremotece.core.domain.live.HostDiscovery
import com.greenfodor.ppremotece.core.domain.model.ProPresenterHost
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.Inet4Address
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Browses mDNS for `_proapiv1ws._tcp` with [NsdManager] and resolves each service to its SRV port
 * and first IPv4 address. Emits the resolved hosts, sorted by name, whenever the set changes.
 */
class NsdHostDiscovery(
    context: Context
) : HostDiscovery {
    private val nsdManager = context.getSystemService(NsdManager::class.java)
    private val resolveExecutor = Executors.newSingleThreadExecutor()

    override fun discoveredHosts(): Flow<List<ProPresenterHost>> =
        callbackFlow {
            val hosts = ConcurrentHashMap<String, ProPresenterHost>()

            fun publish() {
                trySend(hosts.values.sortedBy { it.name })
            }

            val listener = object : NsdManager.DiscoveryListener {
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    resolve(serviceInfo) { host ->
                        hosts[serviceInfo.serviceName] = host
                        publish()
                    }
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    hosts.remove(serviceInfo.serviceName)
                    publish()
                }

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    close()
                }

                override fun onDiscoveryStarted(serviceType: String) = Unit

                override fun onDiscoveryStopped(serviceType: String) = Unit

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            }
            publish()
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            awaitClose { runCatching { nsdManager.stopServiceDiscovery(listener) } }
        }

    private fun resolve(serviceInfo: NsdServiceInfo, onResolved: (ProPresenterHost) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            resolveWithCallback(serviceInfo, onResolved)
        } else {
            resolveLegacy(serviceInfo, onResolved)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun resolveWithCallback(serviceInfo: NsdServiceInfo, onResolved: (ProPresenterHost) -> Unit) {
        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                val address = serviceInfo.hostAddresses.filterIsInstance<Inet4Address>().firstOrNull() ?: return
                onResolved(serviceInfo.toHost(address))
                runCatching { nsdManager.unregisterServiceInfoCallback(this) }
            }

            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = Unit

            override fun onServiceLost() = Unit

            override fun onServiceInfoCallbackUnregistered() = Unit
        }
        nsdManager.registerServiceInfoCallback(serviceInfo, resolveExecutor, callback)
    }

    @Suppress("DEPRECATION")
    private fun resolveLegacy(serviceInfo: NsdServiceInfo, onResolved: (ProPresenterHost) -> Unit) {
        nsdManager.resolveService(
            serviceInfo,
            object : NsdManager.ResolveListener {
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val address = serviceInfo.host as? Inet4Address ?: return
                    onResolved(serviceInfo.toHost(address))
                }

                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            }
        )
    }

    private fun NsdServiceInfo.toHost(address: Inet4Address) =
        ProPresenterHost(name = serviceName, address = requireNotNull(address.hostAddress), port = port)

    private companion object {
        const val SERVICE_TYPE = "_proapiv1ws._tcp"
    }
}
