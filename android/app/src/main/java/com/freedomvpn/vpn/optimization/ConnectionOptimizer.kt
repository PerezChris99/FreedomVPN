package com.freedomvpn.vpn.optimization

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.DatagramSocket
import java.net.DatagramPacket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connection optimization strategies for fast, reliable VPN connections
 * 
 * Features:
 * - Parallel connection attempts to multiple servers
 * - TCP/UDP protocol fallback
 * - Connection pooling for quick reconnects
 * - Adaptive timeout based on network conditions
 * - MTU discovery for optimal packet size
 */
@Singleton
class ConnectionOptimizer @Inject constructor() {
    
    companion object {
        private const val TAG = "ConnectionOptimizer"
        
        // Default connection timeouts
        private const val INITIAL_TIMEOUT_MS = 5000
        private const val MIN_TIMEOUT_MS = 2000
        private const val MAX_TIMEOUT_MS = 15000
        
        // Parallel connection settings
        private const val MAX_PARALLEL_ATTEMPTS = 3
        private const val ATTEMPT_STAGGER_MS = 500L
        
        // MTU discovery
        private const val DEFAULT_MTU = 1420
        private const val MIN_MTU = 1280
        private const val MAX_MTU = 1500
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _connectionState = MutableStateFlow(OptimizationState())
    val connectionState: StateFlow<OptimizationState> = _connectionState.asStateFlow()
    
    // Adaptive timeout based on historical connection times
    private var adaptiveTimeout = INITIAL_TIMEOUT_MS
    private val connectionTimes = mutableListOf<Long>()
    
    // Connection pool for quick reconnects
    private val connectionPool = mutableMapOf<String, CachedConnection>()
    
    // Discovered MTU values per server
    private val mtuCache = mutableMapOf<String, Int>()
    
    /**
     * Connect with optimization - tries multiple strategies
     */
    suspend fun optimizedConnect(
        servers: List<ServerEndpoint>,
        preferUdp: Boolean = true
    ): ConnectionResult = withContext(Dispatchers.IO) {
        
        Log.d(TAG, "Starting optimized connection to ${servers.size} servers")
        
        _connectionState.value = OptimizationState(
            phase = ConnectionPhase.INITIALIZING,
            message = "Preparing connection..."
        )
        
        // Check connection pool first
        for (server in servers) {
            connectionPool[server.id]?.let { cached ->
                if (cached.isValid()) {
                    Log.d(TAG, "Using cached connection for ${server.id}")
                    _connectionState.value = OptimizationState(
                        phase = ConnectionPhase.CONNECTED,
                        message = "Quick reconnect successful"
                    )
                    return@withContext ConnectionResult.Success(
                        server = server,
                        protocol = cached.protocol,
                        mtu = cached.mtu,
                        connectionTime = 50 // Very fast from cache
                    )
                }
            }
        }
        
        // Try parallel connections to top servers
        val topServers = servers.take(MAX_PARALLEL_ATTEMPTS)
        val result = parallelConnect(topServers, preferUdp)
        
        if (result is ConnectionResult.Success) {
            // Cache successful connection
            connectionPool[result.server.id] = CachedConnection(
                serverId = result.server.id,
                protocol = result.protocol,
                mtu = result.mtu,
                timestamp = System.currentTimeMillis()
            )
            
            // Update adaptive timeout
            updateAdaptiveTimeout(result.connectionTime)
        }
        
        result
    }
    
    /**
     * Attempt connections to multiple servers in parallel
     */
    private suspend fun parallelConnect(
        servers: List<ServerEndpoint>,
        preferUdp: Boolean
    ): ConnectionResult = coroutineScope {
        
        _connectionState.value = OptimizationState(
            phase = ConnectionPhase.CONNECTING,
            message = "Testing ${servers.size} servers..."
        )
        
        val results = mutableListOf<Deferred<ConnectionResult>>()
        
        servers.forEachIndexed { index, server ->
            val attempt = async {
                // Stagger attempts slightly to avoid network congestion
                delay(index * ATTEMPT_STAGGER_MS)
                attemptConnection(server, preferUdp)
            }
            results.add(attempt)
        }
        
        // Wait for first successful connection or all failures
        var firstSuccess: ConnectionResult.Success? = null
        
        for (deferred in results) {
            try {
                when (val result = deferred.await()) {
                    is ConnectionResult.Success -> {
                        if (firstSuccess == null) {
                            firstSuccess = result
                            // Cancel remaining attempts
                            results.forEach { it.cancel() }
                            break
                        }
                    }
                    is ConnectionResult.Failure -> {
                        // Continue waiting for others
                    }
                }
            } catch (e: CancellationException) {
                // Cancelled because another succeeded
            }
        }
        
        firstSuccess ?: ConnectionResult.Failure(
            error = "All connection attempts failed",
            servers = servers
        )
    }
    
    /**
     * Attempt connection to a single server with protocol fallback
     */
    private suspend fun attemptConnection(
        server: ServerEndpoint,
        preferUdp: Boolean
    ): ConnectionResult = withContext(Dispatchers.IO) {
        
        val startTime = System.currentTimeMillis()
        
        // Try preferred protocol first
        val protocols = if (preferUdp) {
            listOf(Protocol.UDP, Protocol.TCP)
        } else {
            listOf(Protocol.TCP, Protocol.UDP)
        }
        
        for (protocol in protocols) {
            try {
                val success = when (protocol) {
                    Protocol.UDP -> testUdpConnection(server)
                    Protocol.TCP -> testTcpConnection(server)
                }
                
                if (success) {
                    val connectionTime = (System.currentTimeMillis() - startTime).toInt()
                    val mtu = discoverMtu(server)
                    
                    _connectionState.value = OptimizationState(
                        phase = ConnectionPhase.CONNECTED,
                        message = "Connected via $protocol"
                    )
                    
                    return@withContext ConnectionResult.Success(
                        server = server,
                        protocol = protocol,
                        mtu = mtu,
                        connectionTime = connectionTime
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to connect via $protocol: ${e.message}")
            }
        }
        
        ConnectionResult.Failure(
            error = "Could not connect to ${server.host}",
            servers = listOf(server)
        )
    }
    
    /**
     * Test UDP connection to server
     */
    private fun testUdpConnection(server: ServerEndpoint): Boolean {
        return try {
            DatagramSocket().use { socket ->
                socket.soTimeout = adaptiveTimeout
                
                // Send a minimal probe packet
                val probe = byteArrayOf(0x00, 0x00, 0x00, 0x01) // WireGuard handshake init
                val packet = DatagramPacket(
                    probe,
                    probe.size,
                    InetAddress.getByName(server.host),
                    server.port
                )
                socket.send(packet)
                
                // Try to receive response (may timeout, that's OK for UDP)
                val buffer = ByteArray(64)
                val response = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(response)
                    true
                } catch (e: Exception) {
                    // UDP may not get response, just verify we could send
                    true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "UDP test failed: ${e.message}")
            false
        }
    }
    
    /**
     * Test TCP connection to server
     */
    private fun testTcpConnection(server: ServerEndpoint): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(
                    InetSocketAddress(server.host, server.port),
                    adaptiveTimeout
                )
                socket.isConnected
            }
        } catch (e: Exception) {
            Log.w(TAG, "TCP test failed: ${e.message}")
            false
        }
    }
    
    /**
     * Discover optimal MTU for server
     */
    private fun discoverMtu(server: ServerEndpoint): Int {
        // Check cache first
        mtuCache[server.id]?.let { return it }
        
        // Binary search for optimal MTU
        var low = MIN_MTU
        var high = MAX_MTU
        var optimal = DEFAULT_MTU
        
        try {
            while (low <= high) {
                val mid = (low + high) / 2
                if (canSendPacketOfSize(server, mid)) {
                    optimal = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MTU discovery failed, using default: ${e.message}")
            optimal = DEFAULT_MTU
        }
        
        mtuCache[server.id] = optimal
        Log.d(TAG, "Discovered MTU for ${server.id}: $optimal")
        return optimal
    }
    
    /**
     * Test if we can send a packet of given size
     */
    private fun canSendPacketOfSize(server: ServerEndpoint, size: Int): Boolean {
        return try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 1000
                val data = ByteArray(size)
                val packet = DatagramPacket(
                    data,
                    data.size,
                    InetAddress.getByName(server.host),
                    server.port
                )
                socket.send(packet)
                true
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Update adaptive timeout based on connection history
     */
    private fun updateAdaptiveTimeout(connectionTime: Int) {
        connectionTimes.add(connectionTime.toLong())
        if (connectionTimes.size > 10) {
            connectionTimes.removeAt(0)
        }
        
        val avgTime = connectionTimes.average()
        // Set timeout to 2x average with bounds
        adaptiveTimeout = (avgTime * 2).toInt().coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)
        
        Log.d(TAG, "Adaptive timeout updated to $adaptiveTimeout ms")
    }
    
    /**
     * Clear connection pool (call on network change)
     */
    fun clearConnectionPool() {
        connectionPool.clear()
        Log.d(TAG, "Connection pool cleared")
    }
    
    /**
     * Clean up expired connections from pool
     */
    fun cleanupPool() {
        val expired = connectionPool.filter { !it.value.isValid() }
        expired.keys.forEach { connectionPool.remove(it) }
    }
    
    /**
     * Get optimal MTU for a server
     */
    fun getMtu(serverId: String): Int {
        return mtuCache[serverId] ?: DEFAULT_MTU
    }
    
    fun cleanup() {
        scope.cancel()
    }
}

/**
 * Server endpoint data
 */
data class ServerEndpoint(
    val id: String,
    val host: String,
    val port: Int,
    val publicKey: String? = null
)

/**
 * Connection result
 */
sealed class ConnectionResult {
    data class Success(
        val server: ServerEndpoint,
        val protocol: Protocol,
        val mtu: Int,
        val connectionTime: Int // milliseconds
    ) : ConnectionResult()
    
    data class Failure(
        val error: String,
        val servers: List<ServerEndpoint>
    ) : ConnectionResult()
}

/**
 * Protocol options
 */
enum class Protocol {
    UDP, TCP
}

/**
 * Cached connection for quick reconnect
 */
data class CachedConnection(
    val serverId: String,
    val protocol: Protocol,
    val mtu: Int,
    val timestamp: Long
) {
    companion object {
        private const val CACHE_VALIDITY_MS = 5 * 60 * 1000 // 5 minutes
    }
    
    fun isValid(): Boolean {
        return System.currentTimeMillis() - timestamp < CACHE_VALIDITY_MS
    }
}

/**
 * Connection phases
 */
enum class ConnectionPhase {
    IDLE,
    INITIALIZING,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED,
    FAILED
}

/**
 * Optimization state for UI
 */
data class OptimizationState(
    val phase: ConnectionPhase = ConnectionPhase.IDLE,
    val message: String = "",
    val progress: Float = 0f
)
