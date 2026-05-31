package com.freedom.vpn.anticensorship

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.net.InetAddress
import java.net.Socket
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import kotlin.math.roundToInt

/**
 * FreedomVPN Censorship Bypass Engine for Android
 * Implements multiple obfuscation techniques to defeat DPI and VPN blocking
 * Built specifically for Uganda/Africa where ISPs actively block VPN traffic
 */

// Obfuscation protocols
enum class ObfuscationMethod {
    TLS_CAMOUFLAGE,      // Make VPN look like HTTPS traffic
    HTTPS_MIMICRY,       // Full HTTPS behavior emulation
    WEBSOCKET_TUNNEL,    // Tunnel over WebSocket
    DOMAIN_FRONTING,     // Use CDN domains to hide real destination
    MEEK_AZURE,          // MEEK protocol over Azure CDN
    MEEK_CLOUDFRONT,     // MEEK protocol over Amazon CloudFront
    DNS_TUNNEL,          // Tunnel over DNS queries
    ICMP_TUNNEL,         // Tunnel over ICMP (ping)
    TRAFFIC_MORPHING     // Make traffic look like regular browsing
}

// Server with anti-censorship capabilities
data class AntiCensorshipServer(
    val id: String,
    val host: String,
    val port: Int,
    val country: String,
    val city: String,
    val flag: String,
    val obfuscationMethods: List<ObfuscationMethod>,
    val priority: Int = 1,
    val isAfrican: Boolean = false,
    val isCDN: Boolean = false,
    val realHost: String? = null,  // For domain fronting
    val description: String? = null
)

// Connection health status
data class ConnectionHealth(
    var latency: Long = 0,
    var jitter: Long = 0,
    var packetLoss: Float = 0f,
    var quality: ConnectionQuality = ConnectionQuality.UNKNOWN,
    var lastCheck: Long = 0,
    var isBlocked: Boolean = false
)

enum class ConnectionQuality {
    EXCELLENT, GOOD, FAIR, POOR, CRITICAL, UNKNOWN
}

// Server list with anti-censorship capabilities
object AntiCensorshipServers {
    
    val servers = listOf(
        // === AFRICAN SERVERS (Priority for Uganda users) ===
        AntiCensorshipServer(
            id = "ke-nrb", host = "197.232.170.50", port = 443,
            country = "Kenya", city = "Nairobi", flag = "🇰🇪",
            obfuscationMethods = listOf(ObfuscationMethod.TLS_CAMOUFLAGE, ObfuscationMethod.HTTPS_MIMICRY),
            priority = 1, isAfrican = true,
            description = "Best for East Africa - low latency from Uganda"
        ),
        AntiCensorshipServer(
            id = "rw-kgl", host = "41.186.255.100", port = 443,
            country = "Rwanda", city = "Kigali", flag = "🇷🇼",
            obfuscationMethods = listOf(ObfuscationMethod.TLS_CAMOUFLAGE, ObfuscationMethod.WEBSOCKET_TUNNEL),
            priority = 1, isAfrican = true,
            description = "Rwanda - excellent connectivity"
        ),
        AntiCensorshipServer(
            id = "tz-dar", host = "41.59.90.100", port = 443,
            country = "Tanzania", city = "Dar es Salaam", flag = "🇹🇿",
            obfuscationMethods = listOf(ObfuscationMethod.TLS_CAMOUFLAGE),
            priority = 1, isAfrican = true
        ),
        AntiCensorshipServer(
            id = "za-jhb", host = "41.76.108.50", port = 443,
            country = "South Africa", city = "Johannesburg", flag = "🇿🇦",
            obfuscationMethods = listOf(ObfuscationMethod.TLS_CAMOUFLAGE, ObfuscationMethod.HTTPS_MIMICRY, ObfuscationMethod.WEBSOCKET_TUNNEL),
            priority = 1, isAfrican = true,
            description = "Major African hub - very stable"
        ),
        AntiCensorshipServer(
            id = "eg-cai", host = "41.33.120.100", port = 443,
            country = "Egypt", city = "Cairo", flag = "🇪🇬",
            obfuscationMethods = listOf(ObfuscationMethod.TLS_CAMOUFLAGE, ObfuscationMethod.HTTPS_MIMICRY),
            priority = 1, isAfrican = true,
            description = "North Africa gateway"
        ),
        AntiCensorshipServer(
            id = "ng-los", host = "41.190.2.100", port = 443,
            country = "Nigeria", city = "Lagos", flag = "🇳🇬",
            obfuscationMethods = listOf(ObfuscationMethod.TLS_CAMOUFLAGE, ObfuscationMethod.WEBSOCKET_TUNNEL),
            priority = 1, isAfrican = true,
            description = "West Africa hub"
        ),
        AntiCensorshipServer(
            id = "gh-acc", host = "41.215.168.50", port = 443,
            country = "Ghana", city = "Accra", flag = "🇬🇭",
            obfuscationMethods = listOf(ObfuscationMethod.TLS_CAMOUFLAGE),
            priority = 1, isAfrican = true
        ),

        // === EUROPEAN SERVERS ===
        AntiCensorshipServer(
            id = "nl-ams", host = "185.107.56.100", port = 443,
            country = "Netherlands", city = "Amsterdam", flag = "🇳🇱",
            obfuscationMethods = listOf(ObfuscationMethod.TLS_CAMOUFLAGE, ObfuscationMethod.HTTPS_MIMICRY, ObfuscationMethod.WEBSOCKET_TUNNEL),
            priority = 2,
            description = "European hub - strong privacy laws"
        ),
        AntiCensorshipServer(
            id = "de-fra", host = "185.181.8.100", port = 443,
            country = "Germany", city = "Frankfurt", flag = "🇩🇪",
            obfuscationMethods = listOf(ObfuscationMethod.TLS_CAMOUFLAGE, ObfuscationMethod.HTTPS_MIMICRY),
            priority = 2
        ),
        AntiCensorshipServer(
            id = "ch-zur", host = "185.156.46.100", port = 443,
            country = "Switzerland", city = "Zurich", flag = "🇨🇭",
            obfuscationMethods = listOf(ObfuscationMethod.TLS_CAMOUFLAGE, ObfuscationMethod.HTTPS_MIMICRY),
            priority = 1,
            description = "Swiss privacy laws - very secure"
        ),

        // === CDN FALLBACK SERVERS (Nearly Unblockable) ===
        AntiCensorshipServer(
            id = "cdn-cloudflare", host = "cdnjs.cloudflare.com",
            realHost = "freedom-vpn.pages.dev",
            port = 443, country = "Global", city = "Cloudflare CDN", flag = "☁️",
            obfuscationMethods = listOf(ObfuscationMethod.DOMAIN_FRONTING),
            priority = 5, isCDN = true,
            description = "Routes through Cloudflare - very hard to block"
        ),
        AntiCensorshipServer(
            id = "cdn-google", host = "www.google.com",
            realHost = "freedom-vpn.appspot.com",
            port = 443, country = "Global", city = "Google Cloud", flag = "☁️",
            obfuscationMethods = listOf(ObfuscationMethod.DOMAIN_FRONTING),
            priority = 6, isCDN = true,
            description = "Routes through Google - blocking breaks Google"
        ),
        AntiCensorshipServer(
            id = "cdn-azure", host = "ajax.aspnetcdn.com",
            realHost = "freedom-vpn.azureedge.net",
            port = 443, country = "Global", city = "Microsoft Azure", flag = "☁️",
            obfuscationMethods = listOf(ObfuscationMethod.MEEK_AZURE, ObfuscationMethod.DOMAIN_FRONTING),
            priority = 5, isCDN = true,
            description = "Routes through Microsoft Azure CDN"
        ),
        AntiCensorshipServer(
            id = "cdn-amazon", host = "d1234567890.cloudfront.net",
            realHost = "freedom-vpn.cloudfront.net",
            port = 443, country = "Global", city = "Amazon CloudFront", flag = "☁️",
            obfuscationMethods = listOf(ObfuscationMethod.MEEK_CLOUDFRONT, ObfuscationMethod.DOMAIN_FRONTING),
            priority = 5, isCDN = true,
            description = "Routes through Amazon CloudFront"
        )
    )
    
    fun getAfricanServers() = servers.filter { it.isAfrican }
    fun getCDNServers() = servers.filter { it.isCDN }
    fun getByPriority() = servers.sortedBy { it.priority }
}

/**
 * Connection Health Monitor
 * Continuously monitors connection quality and detects blocks
 */
class ConnectionHealthMonitor(private val context: Context) {
    
    companion object {
        private const val TAG = "HealthMonitor"
        private const val CHECK_INTERVAL_MS = 30_000L
        private const val LATENCY_SAMPLES = 5
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null
    private var currentHealth = ConnectionHealth()
    
    var onHealthChanged: ((ConnectionHealth) -> Unit)? = null
    var onBlockDetected: (() -> Unit)? = null
    
    fun startMonitoring(serverHost: String) {
        stopMonitoring()
        monitorJob = scope.launch {
            while (isActive) {
                checkHealth(serverHost)
                delay(CHECK_INTERVAL_MS)
            }
        }
    }
    
    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }
    
    private suspend fun checkHealth(host: String) {
        try {
            // Measure latency with multiple samples
            val latencies = mutableListOf<Long>()
            var failures = 0
            
            repeat(LATENCY_SAMPLES) {
                try {
                    val start = System.currentTimeMillis()
                    val socket = Socket()
                    socket.connect(java.net.InetSocketAddress(host, 443), 5000)
                    socket.close()
                    latencies.add(System.currentTimeMillis() - start)
                } catch (e: Exception) {
                    failures++
                }
                delay(200)
            }
            
            currentHealth = if (latencies.isNotEmpty()) {
                val avgLatency = latencies.average().roundToInt().toLong()
                val jitter = if (latencies.size > 1) {
                    latencies.zipWithNext { a, b -> kotlin.math.abs(a - b) }.average().roundToInt().toLong()
                } else 0L
                val packetLoss = (failures.toFloat() / LATENCY_SAMPLES) * 100
                
                ConnectionHealth(
                    latency = avgLatency,
                    jitter = jitter,
                    packetLoss = packetLoss,
                    quality = calculateQuality(avgLatency, packetLoss),
                    lastCheck = System.currentTimeMillis(),
                    isBlocked = false
                )
            } else {
                // All checks failed - likely blocked
                ConnectionHealth(
                    latency = 9999,
                    jitter = 0,
                    packetLoss = 100f,
                    quality = ConnectionQuality.CRITICAL,
                    lastCheck = System.currentTimeMillis(),
                    isBlocked = true
                )
            }
            
            onHealthChanged?.invoke(currentHealth)
            
            if (currentHealth.isBlocked) {
                onBlockDetected?.invoke()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Health check failed: ${e.message}")
        }
    }
    
    private fun calculateQuality(latency: Long, packetLoss: Float): ConnectionQuality {
        return when {
            packetLoss > 10 -> ConnectionQuality.CRITICAL
            latency < 100 && packetLoss < 1 -> ConnectionQuality.EXCELLENT
            latency < 200 && packetLoss < 5 -> ConnectionQuality.GOOD
            latency < 500 && packetLoss < 10 -> ConnectionQuality.FAIR
            latency < 1000 -> ConnectionQuality.POOR
            else -> ConnectionQuality.CRITICAL
        }
    }
    
    fun getHealth() = currentHealth
}

/**
 * Smart Server Selector
 * Automatically selects the best server based on latency, load, and block status
 */
class SmartServerSelector(private val context: Context) {
    
    companion object {
        private const val TAG = "ServerSelector"
    }
    
    private val blockedServers = mutableSetOf<String>()
    private val serverLatencies = mutableMapOf<String, Long>()
    
    suspend fun findBestServer(preferAfrican: Boolean = true): AntiCensorshipServer? {
        val servers = if (preferAfrican) {
            AntiCensorshipServers.getAfricanServers() + 
            AntiCensorshipServers.servers.filterNot { it.isAfrican || it.isCDN }
        } else {
            AntiCensorshipServers.servers.filterNot { it.isCDN }
        }
        
        // Filter out blocked servers
        val available = servers.filter { it.id !in blockedServers }
        
        if (available.isEmpty()) {
            // All servers blocked, try CDN fallbacks
            Log.w(TAG, "All regular servers blocked, using CDN fallback")
            return AntiCensorshipServers.getCDNServers().firstOrNull()
        }
        
        // Test latency for top 5 candidates
        val candidates = available.take(5)
        val results = candidates.map { server ->
            server to measureLatency(server.host)
        }
        
        // Return server with lowest latency
        return results.minByOrNull { it.second }?.first
    }
    
    private suspend fun measureLatency(host: String): Long {
        return withContext(Dispatchers.IO) {
            try {
                val start = System.currentTimeMillis()
                val socket = Socket()
                socket.connect(java.net.InetSocketAddress(host, 443), 5000)
                socket.close()
                System.currentTimeMillis() - start
            } catch (e: Exception) {
                9999L
            }
        }
    }
    
    fun markBlocked(serverId: String) {
        blockedServers.add(serverId)
        Log.w(TAG, "Server $serverId marked as blocked")
    }
    
    fun clearBlockedServers() {
        blockedServers.clear()
    }
}

/**
 * Main Censorship Bypass Engine
 * Orchestrates all anti-censorship features
 */
class CensorshipBypassEngine(private val context: Context) {
    
    companion object {
        private const val TAG = "BypassEngine"
        const val FAILOVER_THRESHOLD = 3
    }
    
    private val healthMonitor = ConnectionHealthMonitor(context)
    private val serverSelector = SmartServerSelector(context)
    
    private var currentServer: AntiCensorshipServer? = null
    private var failureCount = 0
    private var reconnections = 0
    private var blocksEvaded = 0
    
    var onServerChanged: ((AntiCensorshipServer) -> Unit)? = null
    var onConnectionFailed: (() -> Unit)? = null
    
    init {
        healthMonitor.onBlockDetected = {
            handleBlockDetected()
        }
    }
    
    suspend fun connect(serverId: String? = null): AntiCensorshipServer? {
        return withContext(Dispatchers.IO) {
            val server = if (serverId != null) {
                AntiCensorshipServers.servers.find { it.id == serverId }
            } else {
                serverSelector.findBestServer(preferAfrican = true)
            }
            
            if (server != null) {
                Log.i(TAG, "Connecting to ${server.city}, ${server.country}...")
                
                // Start connection with obfuscation
                val success = establishConnection(server)
                
                if (success) {
                    currentServer = server
                    failureCount = 0
                    healthMonitor.startMonitoring(server.host)
                    return@withContext server
                }
            }
            
            null
        }
    }
    
    private suspend fun establishConnection(server: AntiCensorshipServer): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Choose best obfuscation method
                val method = selectBestObfuscation(server)
                Log.d(TAG, "Using obfuscation: $method")
                
                when (method) {
                    ObfuscationMethod.TLS_CAMOUFLAGE -> connectWithTLS(server)
                    ObfuscationMethod.DOMAIN_FRONTING -> connectWithDomainFronting(server)
                    ObfuscationMethod.WEBSOCKET_TUNNEL -> connectWithWebSocket(server)
                    else -> connectWithTLS(server)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed: ${e.message}")
                false
            }
        }
    }
    
    private fun selectBestObfuscation(server: AntiCensorshipServer): ObfuscationMethod {
        // Prefer domain fronting if available (hardest to block)
        if (server.isCDN && server.obfuscationMethods.contains(ObfuscationMethod.DOMAIN_FRONTING)) {
            return ObfuscationMethod.DOMAIN_FRONTING
        }
        // Then TLS camouflage
        if (server.obfuscationMethods.contains(ObfuscationMethod.TLS_CAMOUFLAGE)) {
            return ObfuscationMethod.TLS_CAMOUFLAGE
        }
        return server.obfuscationMethods.firstOrNull() ?: ObfuscationMethod.TLS_CAMOUFLAGE
    }
    
    private fun connectWithTLS(server: AntiCensorshipServer): Boolean {
        return try {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, null, SecureRandom())
            val socketFactory = sslContext.socketFactory
            
            val socket = socketFactory.createSocket(server.host, server.port) as SSLSocket
            socket.enabledProtocols = arrayOf("TLSv1.3", "TLSv1.2")
            socket.startHandshake()
            socket.close()
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "TLS connection failed: ${e.message}")
            false
        }
    }
    
    private fun connectWithDomainFronting(server: AntiCensorshipServer): Boolean {
        return try {
            // Connect to CDN host but send real host in HTTP Host header
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, null, SecureRandom())
            val socketFactory = sslContext.socketFactory
            
            val socket = socketFactory.createSocket(server.host, server.port) as SSLSocket
            socket.enabledProtocols = arrayOf("TLSv1.3", "TLSv1.2")
            socket.startHandshake()
            
            // Send HTTP request with fronted host
            val request = "GET / HTTP/1.1\r\nHost: ${server.realHost ?: server.host}\r\n\r\n"
            socket.outputStream.write(request.toByteArray())
            socket.close()
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Domain fronting failed: ${e.message}")
            false
        }
    }
    
    private fun connectWithWebSocket(server: AntiCensorshipServer): Boolean {
        // WebSocket tunnel implementation would go here
        return connectWithTLS(server) // Fallback to TLS for now
    }
    
    private fun handleBlockDetected() {
        failureCount++
        Log.w(TAG, "Block detected! Failure count: $failureCount")
        
        if (failureCount >= FAILOVER_THRESHOLD) {
            currentServer?.let { serverSelector.markBlocked(it.id) }
            blocksEvaded++
            
            CoroutineScope(Dispatchers.IO).launch {
                triggerFailover()
            }
        }
    }
    
    suspend fun triggerFailover() {
        Log.i(TAG, "Triggering failover...")
        healthMonitor.stopMonitoring()
        
        val newServer = serverSelector.findBestServer()
        if (newServer != null && connect(newServer.id) != null) {
            reconnections++
            onServerChanged?.invoke(newServer)
            Log.i(TAG, "Failover successful! Now connected to ${newServer.city}")
        } else {
            // Try CDN fallbacks
            val cdnServer = AntiCensorshipServers.getCDNServers().firstOrNull()
            if (cdnServer != null && connect(cdnServer.id) != null) {
                reconnections++
                onServerChanged?.invoke(cdnServer)
                Log.i(TAG, "Connected via CDN: ${cdnServer.city}")
            } else {
                onConnectionFailed?.invoke()
                Log.e(TAG, "All servers failed! Network may be severely restricted.")
            }
        }
    }
    
    fun disconnect() {
        healthMonitor.stopMonitoring()
        currentServer = null
    }
    
    fun getStats(): Map<String, Any> {
        return mapOf(
            "reconnections" to reconnections,
            "blocksEvaded" to blocksEvaded,
            "currentServer" to (currentServer?.id ?: "none"),
            "health" to healthMonitor.getHealth()
        )
    }
    
    fun getCurrentServer() = currentServer
    fun getHealth() = healthMonitor.getHealth()
}
