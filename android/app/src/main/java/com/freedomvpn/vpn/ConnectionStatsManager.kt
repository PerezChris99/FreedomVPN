package com.freedomvpn.vpn

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Real-time VPN Connection Statistics Manager
 * 
 * Tracks and reports:
 * 1. Data usage (bytes sent/received)
 * 2. Current speed (upload/download)
 * 3. Connection duration
 * 4. Packet counts
 * 5. Average speeds over time
 * 
 * Useful for Uganda and censored regions to:
 * - Monitor data usage on limited plans
 * - Identify throttling by ISPs
 * - Measure actual connection quality
 */
class ConnectionStatsManager {

    /**
     * Connection statistics data
     */
    data class Stats(
        val bytesIn: Long = 0,
        val bytesOut: Long = 0,
        val packetsIn: Long = 0,
        val packetsOut: Long = 0,
        val speedIn: Long = 0,      // bytes/sec
        val speedOut: Long = 0,     // bytes/sec
        val avgSpeedIn: Long = 0,   // bytes/sec average
        val avgSpeedOut: Long = 0,  // bytes/sec average
        val peakSpeedIn: Long = 0,  // bytes/sec peak
        val peakSpeedOut: Long = 0, // bytes/sec peak
        val connectedAt: Long = 0,
        val duration: Long = 0,     // milliseconds
        val serverName: String = "",
        val serverCountry: String = "",
        val protocol: String = "WireGuard"
    ) {
        // Formatted values for UI
        val formattedBytesIn: String get() = formatBytes(bytesIn)
        val formattedBytesOut: String get() = formatBytes(bytesOut)
        val formattedTotal: String get() = formatBytes(bytesIn + bytesOut)
        val formattedSpeedIn: String get() = "${formatBytes(speedIn)}/s"
        val formattedSpeedOut: String get() = "${formatBytes(speedOut)}/s"
        val formattedAvgSpeedIn: String get() = "${formatBytes(avgSpeedIn)}/s"
        val formattedAvgSpeedOut: String get() = "${formatBytes(avgSpeedOut)}/s"
        val formattedPeakSpeedIn: String get() = "${formatBytes(peakSpeedIn)}/s"
        val formattedPeakSpeedOut: String get() = "${formatBytes(peakSpeedOut)}/s"
        val formattedDuration: String get() = formatDuration(duration)

        companion object {
            fun formatBytes(bytes: Long): String = when {
                bytes >= 1_000_000_000 -> String.format("%.2f GB", bytes / 1_000_000_000.0)
                bytes >= 1_000_000 -> String.format("%.2f MB", bytes / 1_000_000.0)
                bytes >= 1_000 -> String.format("%.2f KB", bytes / 1_000.0)
                else -> "$bytes B"
            }

            fun formatDuration(millis: Long): String {
                val seconds = millis / 1000
                val hours = seconds / 3600
                val minutes = (seconds % 3600) / 60
                val secs = seconds % 60
                return when {
                    hours > 0 -> String.format("%02d:%02d:%02d", hours, minutes, secs)
                    else -> String.format("%02d:%02d", minutes, secs)
                }
            }
        }
    }

    // State flow for reactive UI updates
    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats.asStateFlow()

    // Internal counters
    private val bytesIn = AtomicLong(0)
    private val bytesOut = AtomicLong(0)
    private val packetsIn = AtomicLong(0)
    private val packetsOut = AtomicLong(0)
    
    private var lastBytesIn = 0L
    private var lastBytesOut = 0L
    private var lastUpdateTime = 0L
    
    private var peakSpeedIn = 0L
    private var peakSpeedOut = 0L
    private var connectedAt = 0L
    private var serverName = ""
    private var serverCountry = ""
    private var protocol = "WireGuard"

    // Speed history for averaging
    private val speedHistory = mutableListOf<Pair<Long, Long>>()
    private val maxHistorySize = 60 // 60 seconds of history

    // Coroutine scope
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var updateJob: Job? = null

    /**
     * Start tracking statistics
     */
    fun startTracking(
        serverName: String = "",
        serverCountry: String = "",
        protocol: String = "WireGuard"
    ) {
        this.serverName = serverName
        this.serverCountry = serverCountry
        this.protocol = protocol
        
        reset()
        connectedAt = System.currentTimeMillis()
        lastUpdateTime = connectedAt

        // Start periodic updates
        updateJob = scope.launch {
            while (isActive) {
                delay(1000) // Update every second
                updateStats()
            }
        }
    }

    /**
     * Stop tracking statistics
     */
    fun stopTracking() {
        updateJob?.cancel()
        updateJob = null
    }

    /**
     * Record incoming bytes
     */
    fun recordBytesIn(bytes: Long) {
        bytesIn.addAndGet(bytes)
        packetsIn.incrementAndGet()
    }

    /**
     * Record outgoing bytes
     */
    fun recordBytesOut(bytes: Long) {
        bytesOut.addAndGet(bytes)
        packetsOut.incrementAndGet()
    }

    /**
     * Update stats from external source (e.g., WireGuard)
     */
    fun updateFromExternal(rxBytes: Long, txBytes: Long) {
        val newRx = rxBytes - bytesIn.get()
        val newTx = txBytes - bytesOut.get()
        
        if (newRx > 0) bytesIn.set(rxBytes)
        if (newTx > 0) bytesOut.set(txBytes)
    }

    /**
     * Reset all statistics
     */
    fun reset() {
        bytesIn.set(0)
        bytesOut.set(0)
        packetsIn.set(0)
        packetsOut.set(0)
        lastBytesIn = 0
        lastBytesOut = 0
        peakSpeedIn = 0
        peakSpeedOut = 0
        speedHistory.clear()
        _stats.value = Stats()
    }

    /**
     * Get current stats snapshot
     */
    fun getSnapshot(): Stats = _stats.value

    /**
     * Update statistics (called periodically)
     */
    private fun updateStats() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastUpdateTime
        
        if (elapsed <= 0) return
        
        val currentBytesIn = bytesIn.get()
        val currentBytesOut = bytesOut.get()
        
        // Calculate current speed
        val deltaIn = currentBytesIn - lastBytesIn
        val deltaOut = currentBytesOut - lastBytesOut
        
        val speedIn = (deltaIn * 1000 / elapsed)
        val speedOut = (deltaOut * 1000 / elapsed)
        
        // Update peaks
        if (speedIn > peakSpeedIn) peakSpeedIn = speedIn
        if (speedOut > peakSpeedOut) peakSpeedOut = speedOut
        
        // Add to history for averaging
        speedHistory.add(Pair(speedIn, speedOut))
        while (speedHistory.size > maxHistorySize) {
            speedHistory.removeAt(0)
        }
        
        // Calculate averages
        val avgSpeedIn = if (speedHistory.isNotEmpty()) {
            speedHistory.sumOf { it.first } / speedHistory.size
        } else 0
        
        val avgSpeedOut = if (speedHistory.isNotEmpty()) {
            speedHistory.sumOf { it.second } / speedHistory.size
        } else 0
        
        // Update state
        _stats.value = Stats(
            bytesIn = currentBytesIn,
            bytesOut = currentBytesOut,
            packetsIn = packetsIn.get(),
            packetsOut = packetsOut.get(),
            speedIn = speedIn,
            speedOut = speedOut,
            avgSpeedIn = avgSpeedIn,
            avgSpeedOut = avgSpeedOut,
            peakSpeedIn = peakSpeedIn,
            peakSpeedOut = peakSpeedOut,
            connectedAt = connectedAt,
            duration = now - connectedAt,
            serverName = serverName,
            serverCountry = serverCountry,
            protocol = protocol
        )
        
        // Remember for next calculation
        lastBytesIn = currentBytesIn
        lastBytesOut = currentBytesOut
        lastUpdateTime = now
    }

    /**
     * Export stats for logging/analytics
     */
    fun exportStats(): Map<String, Any> {
        val s = _stats.value
        return mapOf(
            "bytes_in" to s.bytesIn,
            "bytes_out" to s.bytesOut,
            "packets_in" to s.packetsIn,
            "packets_out" to s.packetsOut,
            "speed_in" to s.speedIn,
            "speed_out" to s.speedOut,
            "avg_speed_in" to s.avgSpeedIn,
            "avg_speed_out" to s.avgSpeedOut,
            "peak_speed_in" to s.peakSpeedIn,
            "peak_speed_out" to s.peakSpeedOut,
            "duration_ms" to s.duration,
            "server" to s.serverName,
            "country" to s.serverCountry,
            "protocol" to s.protocol
        )
    }
}
