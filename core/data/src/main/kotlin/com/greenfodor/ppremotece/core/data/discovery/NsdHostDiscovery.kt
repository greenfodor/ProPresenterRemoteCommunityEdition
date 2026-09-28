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
import java.io.IOException
import java.net.Inet4Address
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Browses mDNS for `_proapiv1ws._tcp` with [NsdManager] and resolves each service to its SRV port
 * and first IPv4 address. Emits the resolved hosts, sorted by name, whenever the set changes, and
 * fails with [IOException] when discovery cannot start. Pending resolves are cancelled when a
 * service is lost or the flow is closed.
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

            val resolver = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                CallbackResolver()
            } else {
                QueuedResolver()
            }
            val listener = object : NsdManager.DiscoveryListener {
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    resolver.resolve(serviceInfo) { host ->
                        hosts[serviceInfo.serviceName] = host
                        publish()
                    }
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    resolver.cancel(serviceInfo.serviceName)
                    hosts.remove(serviceInfo.serviceName)
                    publish()
                }

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    close(IOException("NSD discovery failed to start: $errorCode"))
                }

                override fun onDiscoveryStarted(serviceType: String) = Unit

                override fun onDiscoveryStopped(serviceType: String) = Unit

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            }
            publish()
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            awaitClose {
                runCatching { nsdManager.stopServiceDiscovery(listener) }
                resolver.cancelAll()
            }
        }

    private interface Resolver {
        fun resolve(serviceInfo: NsdServiceInfo, onResolved: (ProPresenterHost) -> Unit)

        fun cancel(serviceName: String)

        fun cancelAll()
    }

    /** API 34+: one [NsdManager.ServiceInfoCallback] per service, unregistered once an IPv4 address arrives. */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private inner class CallbackResolver : Resolver {
        private val callbacks = ConcurrentHashMap<String, NsdManager.ServiceInfoCallback>()

        override fun resolve(serviceInfo: NsdServiceInfo, onResolved: (ProPresenterHost) -> Unit) {
            val name = serviceInfo.serviceName
            cancel(name)
            val callback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                    val address = serviceInfo.hostAddresses.filterIsInstance<Inet4Address>().firstOrNull()
                    if (address != null && callbacks[name] === this) {
                        cancel(name)
                        onResolved(serviceInfo.toHost(name, address))
                    }
                }

                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                    callbacks.remove(name, this)
                }

                override fun onServiceLost() = Unit

                override fun onServiceInfoCallbackUnregistered() = Unit
            }
            callbacks[name] = callback
            nsdManager.registerServiceInfoCallback(serviceInfo, resolveExecutor, callback)
        }

        override fun cancel(serviceName: String) {
            callbacks.remove(serviceName)?.let { runCatching { nsdManager.unregisterServiceInfoCallback(it) } }
        }

        override fun cancelAll() {
            callbacks.keys.toList().forEach(::cancel)
        }
    }

    /** API 29–33: [NsdManager.resolveService] allows one resolve at a time, so services are resolved in turn. */
    private inner class QueuedResolver : Resolver {
        private val queue = ArrayDeque<Pair<NsdServiceInfo, (ProPresenterHost) -> Unit>>()
        private var active: String? = null
        private var activeCancelled = false

        @Synchronized
        override fun resolve(serviceInfo: NsdServiceInfo, onResolved: (ProPresenterHost) -> Unit) {
            queue.addLast(serviceInfo to onResolved)
            if (active == null) next()
        }

        @Synchronized
        override fun cancel(serviceName: String) {
            queue.removeAll { it.first.serviceName == serviceName }
            if (active == serviceName) activeCancelled = true
        }

        @Synchronized
        override fun cancelAll() {
            queue.clear()
            if (active != null) activeCancelled = true
        }

        @Synchronized
        private fun finish(resolved: ProPresenterHost?, onResolved: (ProPresenterHost) -> Unit) {
            if (resolved != null && !activeCancelled) onResolved(resolved)
            active = null
            next()
        }

        @Suppress("DEPRECATION")
        private fun next() {
            val (serviceInfo, onResolved) = queue.removeFirstOrNull() ?: return
            active = serviceInfo.serviceName
            activeCancelled = false
            nsdManager.resolveService(
                serviceInfo,
                object : NsdManager.ResolveListener {
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val address = serviceInfo.host as? Inet4Address
                        finish(address?.let { serviceInfo.toHost(serviceInfo.serviceName, it) }, onResolved)
                    }

                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        finish(null, onResolved)
                    }
                }
            )
        }
    }

    private fun NsdServiceInfo.toHost(name: String, address: Inet4Address) =
        ProPresenterHost(name = name, address = requireNotNull(address.hostAddress), port = port)

    private companion object {
        const val SERVICE_TYPE = "_proapiv1ws._tcp"
    }
}
