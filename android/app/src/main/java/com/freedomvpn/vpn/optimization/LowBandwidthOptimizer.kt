package com.freedomvpn.vpn.optimization

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Low Bandwidth Optimizer for Africa
 * 
 * Optimizes VPN for slow and unreliable networks common in:
 * - Rural Africa
 * - Congested urban networks
 * - 2G/EDGE connections
 * - Unstable mobile networks
 * 
 * Key optimizations:
 * - Aggressive packet buffering
 * - Request prioritization
 * - Connection persistence
 * - Automatic quality adjustment
 */
@Singleton
class LowBandwidthOptimizer @Inject constructor(
    private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val _networkQuality = MutableStateFlow(NetworkQuality.UNKNOWN)
    val networkQuality: StateFlow<NetworkQuality> = _networkQuality

    private val _optimizationMode = MutableStateFlow(OptimizationMode.AUTO)
    val optimizationMode: StateFlow<OptimizationMode> = _optimizationMode

    private val _settings = MutableStateFlow(LowBandwidthSettings())
    val settings: StateFlow<LowBandwidthSettings> = _settings

    enum class NetworkQuality(val description: String, val expectedSpeed: String) {
        UNKNOWN("Unknown", "?"),
        EXTREMELY_SLOW("2G/EDGE", "< 50 Kbps"),
        VERY_SLOW("Slow 3G", "50-150 Kbps"),
        SLOW("3G", "150-500 Kbps"),
        MODERATE("Fast 3G/Slow 4G", "0.5-2 Mbps"),
        GOOD("4G/LTE", "2-10 Mbps"),
        EXCELLENT("4G+/5G/WiFi", "> 10 Mbps")
    }

    enum class OptimizationMode {
        AUTO,           // Detect and optimize automatically
        EXTREME_SAVER,  // Maximum data/battery saving, reduced quality
        BALANCED,       // Balance between speed and saving
        PERFORMANCE     // Prioritize speed over data usage
    }

    data class LowBandwidthSettings(
        val maxPacketSize: Int = 512,           // Smaller packets for unreliable networks
        val requestTimeout: Long = 30000,        // Longer timeout for slow networks
        val retryAttempts: Int = 5,              // More retries
        val retryDelayMs: Long = 2000,           // Delay between retries
        val enablePrefetch: Boolean = false,     // Don't prefetch on slow networks
        val enableImageCompression: Boolean = true,
        val imageQuality: Int = 50,              // 0-100, lower = smaller
        val enableTextOnly: Boolean = false,     // Text-only mode
        val bufferSize: Int = 8192,              // Larger buffer for batching
        val connectionPoolSize: Int = 2,         // Fewer connections
        val keepAliveInterval: Long = 60000,     // Keep connection alive
        val enableQuickResume: Boolean = true,   // Quick resume on connection drop
        val prioritizeText: Boolean = true,      // Load text before images
        val dnsCache: Boolean = true,            // Cache DNS to reduce lookups
        val dnsCacheTime: Long = 3600000         // 1 hour DNS cache
    )

    // ==================== NETWORK DETECTION ====================

    fun detectNetworkQuality(): NetworkQuality {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        if (capabilities == null) {
            _networkQuality.value = NetworkQuality.UNKNOWN
            return NetworkQuality.UNKNOWN
        }

        val quality = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                NetworkQuality.EXCELLENT
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                detectCellularQuality()
            }
            else -> NetworkQuality.MODERATE
        }

        _networkQuality.value = quality
        applyOptimizationsForQuality(quality)
        return quality
    }

    private fun detectCellularQuality(): NetworkQuality {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        
        return when (telephonyManager.dataNetworkType) {
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_IDEN -> NetworkQuality.EXTREMELY_SLOW
            
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_EVDO_B -> NetworkQuality.VERY_SLOW
            
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA -> NetworkQuality.SLOW
            
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_EHRPD -> NetworkQuality.MODERATE
            
            TelephonyManager.NETWORK_TYPE_LTE -> NetworkQuality.GOOD
            
            TelephonyManager.NETWORK_TYPE_NR -> NetworkQuality.EXCELLENT  // 5G
            
            else -> NetworkQuality.MODERATE
        }
    }

    // ==================== OPTIMIZATION APPLICATION ====================

    private fun applyOptimizationsForQuality(quality: NetworkQuality) {
        if (_optimizationMode.value != OptimizationMode.AUTO) return

        val settings = when (quality) {
            NetworkQuality.EXTREMELY_SLOW -> LowBandwidthSettings(
                maxPacketSize = 256,
                requestTimeout = 60000,
                retryAttempts = 8,
                retryDelayMs = 5000,
                enablePrefetch = false,
                enableImageCompression = true,
                imageQuality = 20,
                enableTextOnly = true,
                bufferSize = 4096,
                connectionPoolSize = 1,
                keepAliveInterval = 120000,
                prioritizeText = true
            )
            NetworkQuality.VERY_SLOW -> LowBandwidthSettings(
                maxPacketSize = 384,
                requestTimeout = 45000,
                retryAttempts = 6,
                retryDelayMs = 3000,
                enableImageCompression = true,
                imageQuality = 30,
                bufferSize = 6144,
                connectionPoolSize = 1,
                prioritizeText = true
            )
            NetworkQuality.SLOW -> LowBandwidthSettings(
                maxPacketSize = 512,
                requestTimeout = 30000,
                retryAttempts = 5,
                enableImageCompression = true,
                imageQuality = 50,
                bufferSize = 8192,
                connectionPoolSize = 2
            )
            NetworkQuality.MODERATE -> LowBandwidthSettings(
                maxPacketSize = 1024,
                requestTimeout = 20000,
                retryAttempts = 4,
                imageQuality = 70,
                connectionPoolSize = 4
            )
            NetworkQuality.GOOD, NetworkQuality.EXCELLENT -> LowBandwidthSettings(
                maxPacketSize = 1400,
                requestTimeout = 15000,
                retryAttempts = 3,
                enableImageCompression = false,
                bufferSize = 16384,
                connectionPoolSize = 6
            )
            NetworkQuality.UNKNOWN -> LowBandwidthSettings()
        }

        _settings.value = settings
    }

    fun setOptimizationMode(mode: OptimizationMode) {
        _optimizationMode.value = mode
        
        when (mode) {
            OptimizationMode.AUTO -> detectNetworkQuality()
            OptimizationMode.EXTREME_SAVER -> {
                _settings.value = LowBandwidthSettings(
                    maxPacketSize = 256,
                    requestTimeout = 60000,
                    retryAttempts = 8,
                    enableImageCompression = true,
                    imageQuality = 10,
                    enableTextOnly = true,
                    connectionPoolSize = 1
                )
            }
            OptimizationMode.BALANCED -> {
                _settings.value = LowBandwidthSettings()  // Default balanced settings
            }
            OptimizationMode.PERFORMANCE -> {
                _settings.value = LowBandwidthSettings(
                    maxPacketSize = 1500,
                    requestTimeout = 10000,
                    retryAttempts = 2,
                    enableImageCompression = false,
                    bufferSize = 32768,
                    connectionPoolSize = 8,
                    enablePrefetch = true
                )
            }
        }
    }

    // ==================== PACKET OPTIMIZATION ====================

    /**
     * Optimizes packet for current network conditions
     */
    fun optimizePacket(data: ByteArray): List<ByteArray> {
        val settings = _settings.value
        
        // If packet is already small enough, return as-is
        if (data.size <= settings.maxPacketSize) {
            return listOf(data)
        }
        
        // Split into smaller packets for unreliable networks
        return data.toList()
            .chunked(settings.maxPacketSize)
            .mapIndexed { index, chunk ->
                // Add sequence header for reassembly
                val header = byteArrayOf(
                    (index shr 8).toByte(),
                    index.toByte(),
                    ((data.size shr 24) and 0xFF).toByte(),
                    ((data.size shr 16) and 0xFF).toByte(),
                    ((data.size shr 8) and 0xFF).toByte(),
                    (data.size and 0xFF).toByte()
                )
                header + chunk.toByteArray()
            }
    }

    /**
     * Reassembles split packets
     */
    fun reassemblePackets(packets: List<ByteArray>): ByteArray {
        if (packets.isEmpty()) return ByteArray(0)
        if (packets.size == 1 && packets[0].size < 6) return packets[0]
        
        // Sort by sequence number and reassemble
        val sorted = packets.sortedBy { 
            ((it[0].toInt() and 0xFF) shl 8) or (it[1].toInt() and 0xFF)
        }
        
        val result = mutableListOf<Byte>()
        for (packet in sorted) {
            if (packet.size > 6) {
                result.addAll(packet.drop(6))  // Skip header
            }
        }
        
        return result.toByteArray()
    }

    // ==================== CONNECTION RESILIENCE ====================

    /**
     * Handles connection drop with quick resume
     */
    suspend fun <T> withRetry(
        action: suspend () -> T
    ): T {
        val settings = _settings.value
        var lastException: Exception? = null
        
        repeat(settings.retryAttempts) { attempt ->
            try {
                return action()
            } catch (e: Exception) {
                lastException = e
                if (attempt < settings.retryAttempts - 1) {
                    // Exponential backoff
                    val delay = settings.retryDelayMs * (attempt + 1)
                    delay(delay)
                }
            }
        }
        
        throw lastException ?: Exception("All retry attempts failed")
    }

    /**
     * Request queue for slow networks
     * Prioritizes important requests
     */
    data class QueuedRequest(
        val id: String,
        val data: ByteArray,
        val priority: RequestPriority,
        val timestamp: Long = System.currentTimeMillis()
    )

    enum class RequestPriority {
        CRITICAL,   // VPN handshake, auth
        HIGH,       // User-initiated requests
        NORMAL,     // Regular traffic
        LOW         // Prefetch, analytics
    }

    private val requestQueue = mutableListOf<QueuedRequest>()

    fun queueRequest(request: QueuedRequest) {
        synchronized(requestQueue) {
            // Insert by priority
            val index = requestQueue.indexOfFirst { it.priority > request.priority }
            if (index == -1) {
                requestQueue.add(request)
            } else {
                requestQueue.add(index, request)
            }
        }
    }

    fun getNextRequest(): QueuedRequest? {
        synchronized(requestQueue) {
            return requestQueue.removeFirstOrNull()
        }
    }

    // ==================== BANDWIDTH MEASUREMENT ====================

    private var lastSpeedTest = 0L
    private var measuredDownloadSpeed = 0f  // Kbps
    private var measuredUploadSpeed = 0f    // Kbps

    suspend fun measureBandwidth(): Pair<Float, Float> {
        // Only measure every 5 minutes
        if (System.currentTimeMillis() - lastSpeedTest < 300000) {
            return Pair(measuredDownloadSpeed, measuredUploadSpeed)
        }

        try {
            // Download test (small payload)
            val downloadStart = System.currentTimeMillis()
            // In real implementation, would download test file
            delay(100)  // Simulated
            val downloadTime = System.currentTimeMillis() - downloadStart
            measuredDownloadSpeed = 1000f / downloadTime  // Simplified

            // Upload test
            val uploadStart = System.currentTimeMillis()
            delay(100)  // Simulated
            val uploadTime = System.currentTimeMillis() - uploadStart
            measuredUploadSpeed = 500f / uploadTime  // Simplified

            lastSpeedTest = System.currentTimeMillis()
            
            // Update quality based on measured speed
            updateQualityFromSpeed(measuredDownloadSpeed)
            
        } catch (e: Exception) {
            // Network test failed
        }

        return Pair(measuredDownloadSpeed, measuredUploadSpeed)
    }

    private fun updateQualityFromSpeed(speedKbps: Float) {
        val quality = when {
            speedKbps < 50 -> NetworkQuality.EXTREMELY_SLOW
            speedKbps < 150 -> NetworkQuality.VERY_SLOW
            speedKbps < 500 -> NetworkQuality.SLOW
            speedKbps < 2000 -> NetworkQuality.MODERATE
            speedKbps < 10000 -> NetworkQuality.GOOD
            else -> NetworkQuality.EXCELLENT
        }
        
        _networkQuality.value = quality
        if (_optimizationMode.value == OptimizationMode.AUTO) {
            applyOptimizationsForQuality(quality)
        }
    }

    // ==================== STATUS ====================

    fun getOptimizationStatus(): String {
        val quality = _networkQuality.value
        val settings = _settings.value
        
        return buildString {
            append("Network: ${quality.description} (${quality.expectedSpeed})\n")
            append("Mode: ${_optimizationMode.value.name}\n")
            append("Packet size: ${settings.maxPacketSize} bytes\n")
            append("Timeout: ${settings.requestTimeout / 1000}s\n")
            append("Image compression: ${if (settings.enableImageCompression) "${settings.imageQuality}%" else "Off"}\n")
            if (settings.enableTextOnly) append("Text-only mode: ON\n")
        }
    }

    fun destroy() {
        scope.cancel()
    }
}
