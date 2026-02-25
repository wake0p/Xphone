package com.safe.discipline.data.service.localadb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class AdbEndpoint(val host: String, val port: Int)

object AdbMdnsLocator {

    private const val TLS_CONNECT = "_adb-tls-connect._tcp"

    suspend fun findTlsConnectEndpoint(context: Context, timeoutMs: Long = 2500): AdbEndpoint? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return findWithNsd(context, timeoutMs)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun findWithNsd(context: Context, timeoutMs: Long): AdbEndpoint? {
        val nsdManager = context.getSystemService(NsdManager::class.java) ?: return null

        return suspendCancellableCoroutine { cont ->
            var finished = false
            var discoveryListener: NsdManager.DiscoveryListener? = null

            fun finish(value: AdbEndpoint?) {
                if (finished) return
                finished = true
                discoveryListener?.let { listener ->
                    runCatching { nsdManager.stopServiceDiscovery(listener) }
                }
                cont.resume(value)
            }

            val resolveListener =
                    object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val hostAddress = serviceInfo.host?.hostAddress ?: return
                            val port = serviceInfo.port
                            if (port > 0) finish(AdbEndpoint(hostAddress, port))
                        }
                    }

            val listener =
                    object : NsdManager.DiscoveryListener {
                        override fun onDiscoveryStarted(serviceType: String) = Unit
                        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = finish(null)
                        override fun onDiscoveryStopped(serviceType: String) = Unit
                        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                        override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
                        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                            nsdManager.resolveService(serviceInfo, resolveListener)
                        }
                    }
            discoveryListener = listener

            nsdManager.discoverServices(TLS_CONNECT, NsdManager.PROTOCOL_DNS_SD, listener)

            val timeoutThread = Thread {
                try {
                    Thread.sleep(timeoutMs)
                    finish(null)
                } catch (_: InterruptedException) {
                }
            }
            timeoutThread.start()

            cont.invokeOnCancellation {
                timeoutThread.interrupt()
                discoveryListener?.let { listener ->
                    runCatching { nsdManager.stopServiceDiscovery(listener) }
                }
            }
        }
    }
}
