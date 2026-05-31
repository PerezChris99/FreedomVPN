package com.freedomvpn.vpn.security

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Port Fallback Manager
 * 
 * Automatically switches to alternative ports when standard VPN ports are blocked.
 * 
 * Port priority:
 * 1. Standard WireGuard (51820)
 * 2. HTTPS (443) - Hardest to block without breaking internet
 * 3. HTTP (80) - Common, rarely blocked
 * 4. DNS (53) - Usually open for DNS resolution
 * 5. IKEv2 (4500) - VPN protocol port
 * 6. OpenVPN (1194) - Fallback
 * 
 * For Uganda and censored regions where:
 * - Standard VPN ports may be blocked
 * - Port scanning may be used to detect VPNs
 * - ISPs may whitelist only common ports
 */
class PortFallbackManager(private val context: Context) {

    companion object {
        private const val TAG = "PortFallback"
        private const val CONNECTION_TIMEOUT_MS = 3000
        
        // Ports to try in order of preference
        val FALLBACK_PORTS = listOf(
            51820,  // WireGuard standard
            443,    // HTTPS - safest, rarely blocked
            80,     // HTTP - common
            53,     // DNS - usually open
            4500,   // IKEv2/IPSec NAT-T
            1194,   // OpenVPN standard
            8443,   // Alternative HTTPS
            8080,   // Alternative HTTP
            993,    // IMAPS - email, usually open
            995     // POP3S - email, usually open
        )
        
        // Ports that look most like regular traffic
        val STEALTH_PORTS = listOf(443, 80, 8443, 8080)
    }

    /**
     * Port test result
     */
    data class PortTestResult(
        val port: Int,
        val isOpen: Boolean,
        val latencyMs: Long?,
        val protocol: String = "UDP"
    )

    /**
     * Find the first open port to a server
     */
    suspend fun findOpenPort(
        serverAddress: String,
        preferStealth: Boolean = false
    ): Int? = withContext(Dispatchers.IO) {
        val portsToTry = if (preferStealth) {
            STEALTH_PORTS + FALLBACK_PORTS.filter { it !in STEALTH_PORTS }
        } else {
            FALLBACK_PORTS
        }
        
        for (port in portsToTry) {
            Log.d(TAG, "Testing port $port on $serverAddress")
            if (testUdpPort(serverAddress, port)) {
                Log.d(TAG, "Port $port is open")
                return@withContext port
            }
        }
        
        Log.w(TAG, "No open ports found for $serverAddress")
        null
    }

    /**
     * Test all ports and return results
     */
    suspend fun testAllPorts(serverAddress: String): List<PortTestResult> = 
        withContext(Dispatchers.IO) {
            FALLBACK_PORTS.map { port ->
                val startTime = System.currentTimeMillis()
                val isOpen = testUdpPort(serverAddress, port)
                val latency = if (isOpen) System.currentTimeMillis() - startTime else null
                
                PortTestResult(
                    port = port,
                    isOpen = isOpen,
                    latencyMs = latency
                )
            }
        }

    /**
     * Test if a UDP port is open (for WireGuard)
     */
    private fun testUdpPort(host: String, port: Int): Boolean {
        return try {
            DatagramSocket().use { socket ->
                socket.soTimeout = CONNECTION_TIMEOUT_MS
                socket.connect(InetSocketAddress(host, port))
                
                // Send a small test packet
                val testData = ByteArray(1) { 0x00 }
                val packet = java.net.DatagramPacket(
                    testData, 
                    testData.size,
                    InetAddress.getByName(host),
                    port
                )
                socket.send(packet)
                
                // If we got here without exception, port seems reachable
                true
            }
        } catch (e: Exception) {
            Log.v(TAG, "UDP port $port test failed: ${e.message}")
            false
        }
    }

    /**
     * Test if a TCP port is open (for fallback protocols)
     */
    private fun testTcpPort(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), CONNECTION_TIMEOUT_MS)
                true
            }
        } catch (e: Exception) {
            Log.v(TAG, "TCP port $port test failed: ${e.message}")
            false
        }
    }

    /**
     * Check if we're on a restricted network (potential censorship)
     */
    fun isRestrictedNetwork(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        
        // Check for VPN capability being blocked might indicate restrictions
        // Also check if network is metered (common in restricted areas)
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
    }

    /**
     * Get recommended port based on network conditions
     */
    suspend fun getRecommendedPort(serverAddress: String): Int {
        // If on restricted network, prefer stealth ports
        val preferStealth = isRestrictedNetwork()
        
        return findOpenPort(serverAddress, preferStealth) ?: FALLBACK_PORTS.first()
    }
}
