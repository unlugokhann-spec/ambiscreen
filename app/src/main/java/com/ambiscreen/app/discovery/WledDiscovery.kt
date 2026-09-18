package com.ambiscreen.app.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log

data class DiscoveredWled(val name: String, val host: String)

private const val SERVICE_TYPE = "_http._tcp."
private const val TAG = "WledDiscovery"

/**
 * WLED cihazları varsayılan olarak mDNS ile kendilerini "_http._tcp" servisi
 * altında "WLED-XXXXXX" adıyla duyurur. Bu sınıf yerel ağı tarayıp bu isimle
 * eşleşen cihazların IP adresini bulur; kullanıcı WLED IP'sini elle girmek
 * zorunda kalmaz.
 */
class WledDiscovery(private val context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun start(onFound: (DiscoveredWled) -> Unit) {
        stop()

        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifiManager.createMulticastLock("ambiscreen-mdns").apply {
            setReferenceCounted(true)
            acquire()
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}

            override fun onServiceFound(service: NsdServiceInfo) {
                if (!service.serviceName.contains("wled", ignoreCase = true)) return
                resolve(service, onFound)
            }

            override fun onServiceLost(service: NsdServiceInfo) {}

            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Discovery başlatılamadı: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        discoveryListener = listener

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.w(TAG, "Discovery başlatılamadı", e)
        }
    }

    private fun resolve(service: NsdServiceInfo, onFound: (DiscoveredWled) -> Unit) {
        nsdManager.resolveService(
            service,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val host = serviceInfo.host?.hostAddress ?: return
                    onFound(DiscoveredWled(serviceInfo.serviceName, host))
                }
            },
        )
    }

    fun stop() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                // Zaten durmuşsa NSD "not registered" hatası fırlatabilir; yok sayılabilir.
            }
        }
        discoveryListener = null

        multicastLock?.let { if (it.isHeld) it.release() }
        multicastLock = null
    }
}
