package com.freedomvpn.vpn.optimization

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data Compression Manager for Africa
 * 
 * Reduces data usage by compressing VPN traffic.
 * Critical for expensive mobile data markets like Uganda.
 * 
 * Savings: 40-70% for text-based content
 * Target: Reduce effective data cost significantly
 */
@Singleton
class DataCompressionManager @Inject constructor() {

    private val _compressionEnabled = MutableStateFlow(true)
    val compressionEnabled: StateFlow<Boolean> = _compressionEnabled

    private val _compressionLevel = MutableStateFlow(CompressionLevel.BALANCED)
    val compressionLevel: StateFlow<CompressionLevel> = _compressionLevel

    private val _stats = MutableStateFlow(CompressionStats())
    val stats: StateFlow<CompressionStats> = _stats

    // Compression statistics
    data class CompressionStats(
        val totalOriginalBytes: Long = 0,
        val totalCompressedBytes: Long = 0,
        val bytesSaved: Long = 0,
        val compressionRatio: Float = 0f,
        val dataSavedMB: Float = 0f,
        val estimatedMoneySaved: Float = 0f  // In UGX
    )

    enum class CompressionLevel(val deflaterLevel: Int, val description: String) {
        FASTEST(Deflater.BEST_SPEED, "Fastest - minimal CPU, lower compression"),
        BALANCED(Deflater.DEFAULT_COMPRESSION, "Balanced - good compression, moderate CPU"),
        MAXIMUM(Deflater.BEST_COMPRESSION, "Maximum - best compression, higher CPU"),
        ADAPTIVE(Deflater.DEFAULT_COMPRESSION, "Adaptive - adjusts based on battery/network")
    }

    // Content types that compress well
    private val compressibleTypes = setOf(
        "text/html", "text/css", "text/javascript", "application/json",
        "application/javascript", "application/xml", "text/xml", "text/plain"
    )

    // Minimum size worth compressing (overhead not worth it below this)
    private val minCompressionSize = 100

    /**
     * Compress data using DEFLATE algorithm
     * Adds header to indicate compressed data
     */
    fun compress(data: ByteArray): ByteArray {
        if (!_compressionEnabled.value) return data
        if (data.size < minCompressionSize) return data
        
        // Check if already compressed (starts with our magic header)
        if (data.size >= 4 && isCompressedData(data)) {
            return data
        }

        return try {
            val level = when (_compressionLevel.value) {
                CompressionLevel.ADAPTIVE -> getAdaptiveLevel()
                else -> _compressionLevel.value.deflaterLevel
            }

            val deflater = Deflater(level)
            val outputStream = ByteArrayOutputStream()
            
            // Write magic header (4 bytes) + original size (4 bytes)
            outputStream.write(MAGIC_HEADER)
            outputStream.write(intToBytes(data.size))
            
            val deflaterStream = DeflaterOutputStream(outputStream, deflater)
            deflaterStream.write(data)
            deflaterStream.close()
            
            val compressed = outputStream.toByteArray()
            
            // Only use compressed if actually smaller
            if (compressed.size < data.size) {
                updateStats(data.size.toLong(), compressed.size.toLong())
                compressed
            } else {
                data
            }
        } catch (e: Exception) {
            data  // Return original on error
        }
    }

    /**
     * Decompress data
     */
    fun decompress(data: ByteArray): ByteArray {
        if (data.size < 8) return data
        if (!isCompressedData(data)) return data

        return try {
            val originalSize = bytesToInt(data.copyOfRange(4, 8))
            val compressedData = data.copyOfRange(8, data.size)
            
            val inflater = Inflater()
            val inputStream = InflaterInputStream(ByteArrayInputStream(compressedData), inflater)
            val outputStream = ByteArrayOutputStream(originalSize)
            
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            
            inputStream.close()
            outputStream.toByteArray()
        } catch (e: Exception) {
            data  // Return original on error
        }
    }

    /**
     * Smart compression - only compresses compressible content
     */
    fun smartCompress(data: ByteArray, contentType: String? = null): ByteArray {
        if (!_compressionEnabled.value) return data
        
        // Check if content type is compressible
        if (contentType != null && !isCompressibleType(contentType)) {
            return data
        }
        
        // Check if data looks already compressed (images, videos, etc.)
        if (looksCompressed(data)) {
            return data
        }
        
        return compress(data)
    }

    /**
     * Batch compression for multiple packets
     */
    fun compressBatch(packets: List<ByteArray>): ByteArray {
        val combined = ByteArrayOutputStream()
        
        // Write packet count
        combined.write(intToBytes(packets.size))
        
        for (packet in packets) {
            val compressed = compress(packet)
            combined.write(intToBytes(compressed.size))
            combined.write(compressed)
        }
        
        return combined.toByteArray()
    }

    fun decompressBatch(data: ByteArray): List<ByteArray> {
        val result = mutableListOf<ByteArray>()
        val input = ByteArrayInputStream(data)
        
        val countBytes = ByteArray(4)
        input.read(countBytes)
        val packetCount = bytesToInt(countBytes)
        
        for (i in 0 until packetCount) {
            val sizeBytes = ByteArray(4)
            input.read(sizeBytes)
            val size = bytesToInt(sizeBytes)
            
            val packetData = ByteArray(size)
            input.read(packetData)
            
            result.add(decompress(packetData))
        }
        
        return result
    }

    // ==================== SETTINGS ====================

    fun setCompressionEnabled(enabled: Boolean) {
        _compressionEnabled.value = enabled
    }

    fun setCompressionLevel(level: CompressionLevel) {
        _compressionLevel.value = level
    }

    // ==================== STATISTICS ====================

    private fun updateStats(originalSize: Long, compressedSize: Long) {
        val current = _stats.value
        val newOriginal = current.totalOriginalBytes + originalSize
        val newCompressed = current.totalCompressedBytes + compressedSize
        val saved = newOriginal - newCompressed
        val ratio = if (newOriginal > 0) (saved.toFloat() / newOriginal) * 100 else 0f
        val savedMB = saved / (1024f * 1024f)
        
        // Uganda mobile data cost: ~$2 per GB = ~7500 UGX per GB
        val ugxPerMB = 7.5f  // 7500 UGX / 1000 MB
        val moneySaved = savedMB * ugxPerMB
        
        _stats.value = CompressionStats(
            totalOriginalBytes = newOriginal,
            totalCompressedBytes = newCompressed,
            bytesSaved = saved,
            compressionRatio = ratio,
            dataSavedMB = savedMB,
            estimatedMoneySaved = moneySaved
        )
    }

    fun getDataSavingsDisplay(): String {
        val stats = _stats.value
        return buildString {
            append("Data saved: %.2f MB".format(stats.dataSavedMB))
            append(" (${stats.compressionRatio.toInt()}% compression)")
            append("\nEstimated savings: ${stats.estimatedMoneySaved.toInt()} UGX")
        }
    }

    fun resetStats() {
        _stats.value = CompressionStats()
    }

    // ==================== UTILITIES ====================

    private fun isCompressedData(data: ByteArray): Boolean {
        return data.size >= 4 &&
            data[0] == MAGIC_HEADER[0] &&
            data[1] == MAGIC_HEADER[1] &&
            data[2] == MAGIC_HEADER[2] &&
            data[3] == MAGIC_HEADER[3]
    }

    private fun isCompressibleType(contentType: String): Boolean {
        return compressibleTypes.any { contentType.startsWith(it) }
    }

    private fun looksCompressed(data: ByteArray): Boolean {
        if (data.size < 4) return false
        
        // Check for common compressed format signatures
        val signatures = listOf(
            byteArrayOf(0x1F.toByte(), 0x8B.toByte()),  // GZIP
            byteArrayOf(0x50, 0x4B),                      // ZIP/DOCX/etc
            byteArrayOf(0xFF.toByte(), 0xD8.toByte()),  // JPEG
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47), // PNG
            byteArrayOf(0x47, 0x49, 0x46),               // GIF
            byteArrayOf(0x52, 0x49, 0x46, 0x46),         // WebP/AVI
        )
        
        return signatures.any { sig ->
            data.size >= sig.size && data.copyOfRange(0, sig.size).contentEquals(sig)
        }
    }

    private fun getAdaptiveLevel(): Int {
        // In real implementation, would check battery level and network speed
        // For now, use balanced
        return Deflater.DEFAULT_COMPRESSION
    }

    private fun intToBytes(value: Int): ByteArray {
        return byteArrayOf(
            (value shr 24).toByte(),
            (value shr 16).toByte(),
            (value shr 8).toByte(),
            value.toByte()
        )
    }

    private fun bytesToInt(bytes: ByteArray): Int {
        return ((bytes[0].toInt() and 0xFF) shl 24) or
               ((bytes[1].toInt() and 0xFF) shl 16) or
               ((bytes[2].toInt() and 0xFF) shl 8) or
               (bytes[3].toInt() and 0xFF)
    }

    companion object {
        private val MAGIC_HEADER = byteArrayOf(0x46, 0x56, 0x43, 0x4D)  // "FVCM" - FreedomVPN Compressed
    }
}
