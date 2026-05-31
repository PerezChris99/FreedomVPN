package com.freedomvpn.vpn.optimization

import android.content.Context
import android.util.Log
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Memory and throughput optimization for VPN data processing
 * 
 * Features:
 * - ByteBuffer pooling for packet processing
 * - LRU cache for DNS responses
 * - Efficient packet buffering
 * - Zero-copy operations where possible
 * - Adaptive buffer sizing
 */
@Singleton
class PacketOptimizer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "PacketOptimizer"
        
        // Buffer pool settings
        private const val SMALL_BUFFER_SIZE = 1500    // Standard MTU
        private const val LARGE_BUFFER_SIZE = 65536   // Max UDP size
        private const val POOL_SIZE = 50              // Number of buffers to pool
        
        // DNS cache settings
        private const val DNS_CACHE_SIZE = 200        // Number of entries
        private const val DNS_CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
        
        // Stats history
        private const val STATS_HISTORY_SIZE = 60     // 60 seconds of history
    }
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Buffer pools
    private val smallBufferPool = BufferPool(SMALL_BUFFER_SIZE, POOL_SIZE)
    private val largeBufferPool = BufferPool(LARGE_BUFFER_SIZE, POOL_SIZE / 5)
    
    // DNS cache
    private val dnsCache = object : LruCache<String, DnsCacheEntry>(DNS_CACHE_SIZE) {
        override fun sizeOf(key: String, value: DnsCacheEntry): Int = 1
    }
    
    // Packet statistics
    private val _stats = MutableStateFlow(PacketStats())
    val stats: StateFlow<PacketStats> = _stats.asStateFlow()
    
    private var packetsIn = 0L
    private var packetsOut = 0L
    private var bytesIn = 0L
    private var bytesOut = 0L
    private var packetsDropped = 0L
    private var lastStatsUpdate = System.currentTimeMillis()
    
    // Throughput history for graphs
    private val throughputHistory = ArrayDeque<ThroughputSample>(STATS_HISTORY_SIZE)
    
    /**
     * Get a buffer from the pool
     */
    fun acquireBuffer(large: Boolean = false): ByteBuffer {
        return if (large) {
            largeBufferPool.acquire()
        } else {
            smallBufferPool.acquire()
        }
    }
    
    /**
     * Return a buffer to the pool
     */
    fun releaseBuffer(buffer: ByteBuffer, large: Boolean = false) {
        buffer.clear()
        if (large) {
            largeBufferPool.release(buffer)
        } else {
            smallBufferPool.release(buffer)
        }
    }
    
    /**
     * Process incoming packet with optimization
     */
    fun processIncomingPacket(data: ByteArray, length: Int): ProcessedPacket? {
        packetsIn++
        bytesIn += length
        
        val buffer = acquireBuffer(length > SMALL_BUFFER_SIZE)
        try {
            buffer.put(data, 0, length)
            buffer.flip()
            
            // Parse packet header efficiently
            if (length < 20) {
                packetsDropped++
                return null // Too small for IP header
            }
            
            val version = (buffer.get(0).toInt() and 0xF0) shr 4
            if (version != 4 && version != 6) {
                packetsDropped++
                return null // Invalid IP version
            }
            
            updateStats()
            
            return ProcessedPacket(
                buffer = buffer,
                length = length,
                ipVersion = version,
                isPooled = true
            )
        } catch (e: Exception) {
            releaseBuffer(buffer, length > SMALL_BUFFER_SIZE)
            packetsDropped++
            return null
        }
    }
    
    /**
     * Process outgoing packet with optimization
     */
    fun processOutgoingPacket(data: ByteArray, length: Int): ProcessedPacket? {
        packetsOut++
        bytesOut += length
        
        val buffer = acquireBuffer(length > SMALL_BUFFER_SIZE)
        try {
            buffer.put(data, 0, length)
            buffer.flip()
            
            updateStats()
            
            return ProcessedPacket(
                buffer = buffer,
                length = length,
                ipVersion = (buffer.get(0).toInt() and 0xF0) shr 4,
                isPooled = true
            )
        } catch (e: Exception) {
            releaseBuffer(buffer, length > SMALL_BUFFER_SIZE)
            packetsDropped++
            return null
        }
    }
    
    /**
     * Cache DNS response
     */
    fun cacheDnsResponse(hostname: String, addresses: List<String>) {
        dnsCache.put(hostname, DnsCacheEntry(
            addresses = addresses,
            timestamp = System.currentTimeMillis()
        ))
    }
    
    /**
     * Get cached DNS response
     */
    fun getCachedDns(hostname: String): List<String>? {
        val entry = dnsCache.get(hostname) ?: return null
        
        // Check if expired
        if (System.currentTimeMillis() - entry.timestamp > DNS_CACHE_TTL_MS) {
            dnsCache.remove(hostname)
            return null
        }
        
        return entry.addresses
    }
    
    /**
     * Clear DNS cache
     */
    fun clearDnsCache() {
        dnsCache.evictAll()
        Log.d(TAG, "DNS cache cleared")
    }
    
    /**
     * Update packet statistics
     */
    private fun updateStats() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastStatsUpdate
        
        if (elapsed >= 1000) { // Update every second
            val downloadSpeed = (bytesIn * 1000.0 / elapsed).toFloat()
            val uploadSpeed = (bytesOut * 1000.0 / elapsed).toFloat()
            
            // Add to history
            throughputHistory.addLast(ThroughputSample(
                timestamp = now,
                downloadBps = downloadSpeed,
                uploadBps = uploadSpeed
            ))
            
            // Trim old samples
            while (throughputHistory.size > STATS_HISTORY_SIZE) {
                throughputHistory.removeFirst()
            }
            
            _stats.value = PacketStats(
                packetsIn = packetsIn,
                packetsOut = packetsOut,
                bytesIn = bytesIn,
                bytesOut = bytesOut,
                packetsDropped = packetsDropped,
                downloadSpeedBps = downloadSpeed,
                uploadSpeedBps = uploadSpeed,
                bufferPoolHitRate = smallBufferPool.hitRate,
                dnsCacheHitRate = calculateDnsCacheHitRate()
            )
            
            // Reset counters for next interval
            bytesIn = 0
            bytesOut = 0
            lastStatsUpdate = now
        }
    }
    
    /**
     * Get throughput history for graphs
     */
    fun getThroughputHistory(): List<ThroughputSample> {
        return throughputHistory.toList()
    }
    
    /**
     * Calculate DNS cache hit rate
     */
    private fun calculateDnsCacheHitRate(): Float {
        val hits = dnsCache.hitCount()
        val misses = dnsCache.missCount()
        val total = hits + misses
        return if (total > 0) hits.toFloat() / total else 0f
    }
    
    /**
     * Get memory usage statistics
     */
    fun getMemoryStats(): MemoryStats {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        
        return MemoryStats(
            usedBytes = usedMemory,
            maxBytes = maxMemory,
            usagePercent = (usedMemory.toFloat() / maxMemory * 100),
            smallBufferPoolSize = smallBufferPool.size,
            largeBufferPoolSize = largeBufferPool.size,
            dnsCacheSize = dnsCache.size()
        )
    }
    
    /**
     * Clean up resources when memory is low
     */
    fun onLowMemory() {
        Log.w(TAG, "Low memory, clearing caches")
        clearDnsCache()
        smallBufferPool.clear()
        largeBufferPool.clear()
    }
    
    /**
     * Reset all statistics
     */
    fun resetStats() {
        packetsIn = 0
        packetsOut = 0
        bytesIn = 0
        bytesOut = 0
        packetsDropped = 0
        throughputHistory.clear()
        _stats.value = PacketStats()
    }
    
    fun cleanup() {
        scope.cancel()
        smallBufferPool.clear()
        largeBufferPool.clear()
        clearDnsCache()
    }
}

/**
 * Simple ByteBuffer pool
 */
private class BufferPool(
    private val bufferSize: Int,
    private val maxSize: Int
) {
    private val pool = ArrayDeque<ByteBuffer>(maxSize)
    private var hits = 0L
    private var misses = 0L
    
    val size: Int get() = pool.size
    val hitRate: Float get() {
        val total = hits + misses
        return if (total > 0) hits.toFloat() / total else 0f
    }
    
    @Synchronized
    fun acquire(): ByteBuffer {
        return if (pool.isNotEmpty()) {
            hits++
            pool.removeFirst()
        } else {
            misses++
            ByteBuffer.allocateDirect(bufferSize)
        }
    }
    
    @Synchronized
    fun release(buffer: ByteBuffer) {
        if (pool.size < maxSize) {
            buffer.clear()
            pool.addLast(buffer)
        }
        // If pool is full, let buffer be GC'd
    }
    
    @Synchronized
    fun clear() {
        pool.clear()
        hits = 0
        misses = 0
    }
}

/**
 * Processed packet wrapper
 */
data class ProcessedPacket(
    val buffer: ByteBuffer,
    val length: Int,
    val ipVersion: Int,
    val isPooled: Boolean
)

/**
 * DNS cache entry
 */
data class DnsCacheEntry(
    val addresses: List<String>,
    val timestamp: Long
)

/**
 * Packet processing statistics
 */
data class PacketStats(
    val packetsIn: Long = 0,
    val packetsOut: Long = 0,
    val bytesIn: Long = 0,
    val bytesOut: Long = 0,
    val packetsDropped: Long = 0,
    val downloadSpeedBps: Float = 0f,
    val uploadSpeedBps: Float = 0f,
    val bufferPoolHitRate: Float = 0f,
    val dnsCacheHitRate: Float = 0f
)

/**
 * Memory usage statistics
 */
data class MemoryStats(
    val usedBytes: Long,
    val maxBytes: Long,
    val usagePercent: Float,
    val smallBufferPoolSize: Int,
    val largeBufferPoolSize: Int,
    val dnsCacheSize: Int
)

/**
 * Throughput sample for history
 */
data class ThroughputSample(
    val timestamp: Long,
    val downloadBps: Float,
    val uploadBps: Float
)
