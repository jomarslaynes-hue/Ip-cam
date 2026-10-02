package com.obs.droidcampro.network

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

data class DiscoveredDevice(
    val ip: String,
    val hostname: String,
    val latencyMs: Long,
    val isObsPortOpen: Boolean
)

class NetworkDiagnostics(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    fun getWifiLinkSpeed(): Int {
        return wifiManager?.connectionInfo?.linkSpeed ?: -1
    }

    /**
     * Probes an IP and port (e.g. 4455 for OBS WebSocket, 8080 for web controller)
     */
    suspend fun probeHost(ip: String, port: Int, timeoutMs: Int = 800): Pair<Boolean, Long> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                val duration = System.currentTimeMillis() - startTime
                Pair(true, duration)
            }
        } catch (e: Exception) {
            Pair(false, -1L)
        }
    }

    /**
     * Fast concurrent subnet scanner checking .1 through .254 for reachable OBS workstations
     */
    suspend fun scanSubnet(baseSubnet: String = "192.168.1"): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DiscoveredDevice>()
        val jobs = (1..254).map { i ->
            async {
                val ip = "$baseSubnet.$i"
                try {
                    val address = InetAddress.getByName(ip)
                    val start = System.currentTimeMillis()
                    val reachable = address.isReachable(300)
                    if (reachable) {
                        val latency = System.currentTimeMillis() - start
                        val (obsOpen, _) = probeHost(ip, 4455, 300)
                        synchronized(results) {
                            results.add(
                                DiscoveredDevice(
                                    ip = ip,
                                    hostname = address.canonicalHostName,
                                    latencyMs = latency,
                                    isObsPortOpen = obsOpen
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    // Host not reachable or timeout
                }
            }
        }
        jobs.awaitAll()
        results.sortedBy { it.ip }
    }
}
