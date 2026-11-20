package fr.penpenic.babyphone.core.network.lan

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.Executors

/** Téléphone bébé trouvé sur le Wi-Fi. */
data class BabyEndpoint(val name: String, val host: String, val port: Int)

/**
 * Annonce et découverte des téléphones bébé sur le Wi-Fi (mDNS / DNS-SD, service `_babyphone._tcp`).
 * Certains routeurs filtrent le multicast : l'écran parent propose aussi une saisie manuelle de l'adresse.
 */
class LanDiscovery(context: Context) {

    private val nsd = context.getSystemService(NsdManager::class.java)

    /** Annonce le téléphone bébé. Appeler [Registration.unregister] à l'arrêt. */
    fun announce(name: String, port: Int): Registration {
        val info = NsdServiceInfo().apply {
            serviceName = name
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        return Registration { runCatching { nsd.unregisterService(listener) } }
    }

    /** Liste à jour des téléphones bébé visibles, tant que le flux est collecté. */
    fun discover(): Flow<List<BabyEndpoint>> = callbackFlow {
        val found = linkedMapOf<String, BabyEndpoint>()
        val executor = Executors.newSingleThreadExecutor()

        fun publish() {
            trySend(found.values.toList())
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = publish()
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close()
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onServiceFound(info: NsdServiceInfo) {
                resolve(info, executor) { endpoint ->
                    synchronized(found) { found[endpoint.name] = endpoint }
                    publish()
                }
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                synchronized(found) { found.remove(info.serviceName) }
                publish()
            }
        }
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose {
            runCatching { nsd.stopServiceDiscovery(listener) }
            executor.shutdown()
        }
    }

    private fun resolve(info: NsdServiceInfo, executor: java.util.concurrent.Executor, onResolved: (BabyEndpoint) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            nsd.registerServiceInfoCallback(info, executor, object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = Unit
                override fun onServiceUpdated(resolved: NsdServiceInfo) {
                    val host = resolved.hostAddresses.firstOrNull { isPrivateLanAddress(it) } ?: return
                    onResolved(BabyEndpoint(resolved.serviceName, host.hostAddress.orEmpty(), resolved.port))
                    runCatching { nsd.unregisterServiceInfoCallback(this) }
                }
                override fun onServiceLost() = Unit
                override fun onServiceInfoCallbackUnregistered() = Unit
            })
        } else {
            resolveLegacy(info, onResolved)
        }
    }

    @Suppress("DEPRECATION") // remplacé par registerServiceInfoCallback à partir d'Android 14
    private fun resolveLegacy(info: NsdServiceInfo, onResolved: (BabyEndpoint) -> Unit) {
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceResolved(resolved: NsdServiceInfo) {
                val host = resolved.host ?: return
                if (!isPrivateLanAddress(host)) return
                onResolved(BabyEndpoint(resolved.serviceName, host.hostAddress.orEmpty(), resolved.port))
            }
        })
    }

    fun interface Registration {
        fun unregister()
    }

    companion object {
        const val SERVICE_TYPE = "_babyphone._tcp."
    }
}
