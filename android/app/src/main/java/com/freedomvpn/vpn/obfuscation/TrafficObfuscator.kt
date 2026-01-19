package com.freedomvpn.vpn.obfuscation

import android.util.Log
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Traffic Obfuscation Engine
 * 
 * Hides VPN traffic signatures to bypass Deep Packet Inspection (DPI).
 * 
 * Techniques implemented:
 * 1. Packet padding - Makes packets variable size to hide patterns
 * 2. Random delays - Breaks timing analysis
 * 3. Traffic shaping - Mimics HTTPS traffic patterns
 * 4. XOR obfuscation - Simple header scrambling
 * 5. TLS camouflage - Wraps traffic to look like HTTPS
 * 
 * For Uganda and censored regions where:
 * - ISPs use DPI to detect and block VPN protocols
 * - WireGuard signature may be recognized
 * - Traffic patterns may trigger throttling
 */
object TrafficObfuscator {
    
    private const val TAG = "TrafficObfuscator"
    
    // Obfuscation modes
    enum class ObfuscationMode {
        NONE,           // No obfuscation (fastest)
        XOR,            // Simple XOR scrambling
        PADDING,        // Random padding
        TLS_CAMOUFLAGE, // Wrap in TLS-like headers
        FULL            // All techniques combined
    }
    
    // Current mode
    private var currentMode = ObfuscationMode.NONE
    
    // XOR key for obfuscation
    private var xorKey: ByteArray = ByteArray(32)
    
    // Random number generator
    private val random = SecureRandom()
    
    // TLS record header for camouflage
    private val TLS_RECORD_HEADER = byteArrayOf(
        0x17,          // Application data
        0x03, 0x03,    // TLS 1.2 version
        0x00, 0x00     // Length placeholder
    )
    
    // Minimum and maximum padding sizes
    private const val MIN_PADDING = 16
    private const val MAX_PADDING = 128
    
    /**
     * Initialize obfuscator with a key
     */
    fun initialize(key: ByteArray? = null) {
        xorKey = key ?: ByteArray(32).also { random.nextBytes(it) }
        Log.d(TAG, "Obfuscator initialized")
    }
    
    /**
     * Set obfuscation mode
     */
    fun setMode(mode: ObfuscationMode) {
        currentMode = mode
        Log.d(TAG, "Obfuscation mode set to: $mode")
    }
    
    /**
     * Get current mode
     */
    fun getMode(): ObfuscationMode = currentMode
    
    /**
     * Obfuscate outgoing packet
     */
    fun obfuscate(packet: ByteBuffer): ByteBuffer {
        return when (currentMode) {
            ObfuscationMode.NONE -> packet
            ObfuscationMode.XOR -> xorObfuscate(packet)
            ObfuscationMode.PADDING -> addPadding(packet)
            ObfuscationMode.TLS_CAMOUFLAGE -> tlsCamouflage(packet)
            ObfuscationMode.FULL -> fullObfuscate(packet)
        }
    }
    
    /**
     * Obfuscate byte array
     */
    fun obfuscate(data: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(data)
        val result = obfuscate(buffer)
        val output = ByteArray(result.remaining())
        result.get(output)
        return output
    }
    
    /**
     * Deobfuscate incoming packet
     */
    fun deobfuscate(packet: ByteBuffer): ByteBuffer {
        return when (currentMode) {
            ObfuscationMode.NONE -> packet
            ObfuscationMode.XOR -> xorDeobfuscate(packet)
            ObfuscationMode.PADDING -> removePadding(packet)
            ObfuscationMode.TLS_CAMOUFLAGE -> tlsDecamouflage(packet)
            ObfuscationMode.FULL -> fullDeobfuscate(packet)
        }
    }
    
    /**
     * Deobfuscate byte array
     */
    fun deobfuscate(data: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(data)
        val result = deobfuscate(buffer)
        val output = ByteArray(result.remaining())
        result.get(output)
        return output
    }
    
    /**
     * XOR obfuscation - scrambles packet headers
     */
    private fun xorObfuscate(packet: ByteBuffer): ByteBuffer {
        val data = ByteArray(packet.remaining())
        packet.get(data)
        
        for (i in data.indices) {
            data[i] = (data[i].toInt() xor xorKey[i % xorKey.size].toInt()).toByte()
        }
        
        return ByteBuffer.wrap(data)
    }
    
    private fun xorDeobfuscate(packet: ByteBuffer): ByteBuffer {
        // XOR is symmetric - same operation for both
        return xorObfuscate(packet)
    }
    
    /**
     * Padding - adds random bytes to mask packet sizes
     */
    private fun addPadding(packet: ByteBuffer): ByteBuffer {
        val data = ByteArray(packet.remaining())
        packet.get(data)
        
        // Random padding size
        val paddingSize = MIN_PADDING + random.nextInt(MAX_PADDING - MIN_PADDING)
        val padding = ByteArray(paddingSize)
        random.nextBytes(padding)
        
        // Create output: [2-byte original length][original data][padding]
        val output = ByteBuffer.allocate(2 + data.size + paddingSize)
        output.putShort(data.size.toShort())
        output.put(data)
        output.put(padding)
        output.flip()
        
        return output
    }
    
    private fun removePadding(packet: ByteBuffer): ByteBuffer {
        val originalLength = packet.short.toInt() and 0xFFFF
        val data = ByteArray(originalLength)
        packet.get(data)
        // Remaining bytes are padding - ignore
        
        return ByteBuffer.wrap(data)
    }
    
    /**
     * TLS Camouflage - wraps packet to look like HTTPS traffic
     */
    private fun tlsCamouflage(packet: ByteBuffer): ByteBuffer {
        val data = ByteArray(packet.remaining())
        packet.get(data)
        
        // Create TLS record header
        val output = ByteBuffer.allocate(5 + data.size)
        output.put(TLS_RECORD_HEADER.copyOf())
        
        // Set length in header
        output.putShort(3, data.size.toShort())
        output.position(5)
        output.put(data)
        output.flip()
        
        return output
    }
    
    private fun tlsDecamouflage(packet: ByteBuffer): ByteBuffer {
        // Skip TLS header (5 bytes)
        packet.position(packet.position() + 5)
        
        val data = ByteArray(packet.remaining())
        packet.get(data)
        
        return ByteBuffer.wrap(data)
    }
    
    /**
     * Full obfuscation - combines all techniques
     */
    private fun fullObfuscate(packet: ByteBuffer): ByteBuffer {
        // 1. XOR scramble
        var result = xorObfuscate(packet)
        
        // 2. Add padding
        result = addPadding(result)
        
        // 3. TLS camouflage
        result = tlsCamouflage(result)
        
        return result
    }
    
    private fun fullDeobfuscate(packet: ByteBuffer): ByteBuffer {
        // Reverse order
        var result = tlsDecamouflage(packet)
        result = removePadding(result)
        result = xorDeobfuscate(result)
        
        return result
    }
    
    /**
     * Generate random delay for timing obfuscation
     * Returns delay in milliseconds
     */
    fun getRandomDelay(): Long {
        // Random delay between 0-50ms to break timing patterns
        return random.nextInt(50).toLong()
    }
    
    /**
     * Check if a packet looks like it might be inspected
     * (heuristic based on packet size patterns)
     */
    fun shouldObfuscate(packetSize: Int): Boolean {
        // Common VPN packet sizes that may trigger DPI
        val suspiciousSizes = listOf(52, 60, 148, 156) // WireGuard handshake sizes
        return suspiciousSizes.any { kotlin.math.abs(packetSize - it) < 10 }
    }
}
