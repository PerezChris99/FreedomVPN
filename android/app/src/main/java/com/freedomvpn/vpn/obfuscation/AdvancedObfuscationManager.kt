package com.freedomvpn.vpn.obfuscation

import android.util.Base64
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.experimental.xor

/**
 * Advanced Obfuscation Manager for Uganda/Africa
 * 
 * Implements multiple obfuscation protocols to defeat:
 * - Deep Packet Inspection (DPI)
 * - Protocol fingerprinting
 * - Traffic analysis
 * - Active probing
 */
@Singleton
class AdvancedObfuscationManager @Inject constructor() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val random = SecureRandom()
    
    private val _activeProtocol = MutableStateFlow(ObfuscationProtocol.TLS_CAMOUFLAGE)
    val activeProtocol: StateFlow<ObfuscationProtocol> = _activeProtocol
    
    private val _bypassSuccess = MutableStateFlow(true)
    val bypassSuccess: StateFlow<Boolean> = _bypassSuccess

    // Protocol rotation for anti-fingerprinting
    private var protocolRotationEnabled = true
    private var packetsUntilRotation = 1000
    private var packetCounter = 0

    enum class ObfuscationProtocol {
        NONE,
        XOR_BASIC,                  // Simple XOR (already implemented)
        TLS_CAMOUFLAGE,             // Make traffic look like HTTPS
        HTTP_CAMOUFLAGE,            // Disguise as HTTP traffic
        DNS_TUNNEL,                 // Tunnel through DNS queries
        SHADOWSOCKS_LIKE,           // AEAD encryption like Shadowsocks
        RANDOM_PADDING,             // Add random padding to packets
        TRAFFIC_SHAPING,            // Shape traffic to look normal
        DOMAIN_FRONTING,            // Use CDN domain fronting technique
        WEBSOCKET_WRAP              // Wrap in WebSocket frames
    }

    // ==================== TLS CAMOUFLAGE ====================
    
    /**
     * Makes VPN traffic look like legitimate HTTPS/TLS traffic
     * Adds TLS record headers and mimics TLS handshake patterns
     */
    fun wrapAsTls(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        
        // TLS Record Header (5 bytes)
        output.write(0x17)  // Application Data
        output.write(0x03)  // TLS 1.2
        output.write(0x03)
        
        // Length (2 bytes, big-endian)
        val length = data.size + 16  // Add MAC length
        output.write((length shr 8) and 0xFF)
        output.write(length and 0xFF)
        
        // Add fake IV (16 bytes)
        val iv = ByteArray(16)
        random.nextBytes(iv)
        output.write(iv)
        
        // XOR encrypt the actual data
        val encrypted = xorEncrypt(data, iv)
        output.write(encrypted)
        
        return output.toByteArray()
    }

    fun unwrapFromTls(data: ByteArray): ByteArray? {
        if (data.size < 21) return null  // Minimum: 5 header + 16 IV
        
        // Verify TLS header
        if (data[0] != 0x17.toByte() || data[1] != 0x03.toByte()) {
            return null
        }
        
        // Extract IV
        val iv = data.copyOfRange(5, 21)
        
        // Extract and decrypt payload
        val payload = data.copyOfRange(21, data.size)
        return xorEncrypt(payload, iv)  // XOR is symmetric
    }

    // ==================== HTTP CAMOUFLAGE ====================
    
    /**
     * Disguises VPN traffic as regular HTTP requests/responses
     * Useful when HTTPS inspection is active
     */
    fun wrapAsHttp(data: ByteArray, isRequest: Boolean = true): ByteArray {
        val encoded = Base64.encodeToString(data, Base64.NO_WRAP)
        
        val http = if (isRequest) {
            buildString {
                append("POST /api/sync HTTP/1.1\r\n")
                append("Host: cdn.googleapis.com\r\n")  // Innocent-looking host
                append("User-Agent: Mozilla/5.0 (Linux; Android 12) Chrome/120.0\r\n")
                append("Content-Type: application/octet-stream\r\n")
                append("Content-Length: ${encoded.length}\r\n")
                append("X-Request-ID: ${generateRequestId()}\r\n")
                append("Connection: keep-alive\r\n")
                append("\r\n")
                append(encoded)
            }
        } else {
            buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: application/octet-stream\r\n")
                append("Content-Length: ${encoded.length}\r\n")
                append("Cache-Control: no-cache\r\n")
                append("X-Response-ID: ${generateRequestId()}\r\n")
                append("\r\n")
                append(encoded)
            }
        }
        
        return http.toByteArray(Charsets.UTF_8)
    }

    fun unwrapFromHttp(data: ByteArray): ByteArray? {
        val text = String(data, Charsets.UTF_8)
        
        // Find the body after \r\n\r\n
        val bodyStart = text.indexOf("\r\n\r\n")
        if (bodyStart == -1) return null
        
        val body = text.substring(bodyStart + 4)
        return try {
            Base64.decode(body, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    // ==================== DNS TUNNEL ====================
    
    /**
     * Tunnels VPN data through DNS queries
     * Extremely hard to block without breaking internet
     */
    fun wrapAsDns(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        
        // DNS Header (12 bytes)
        val transactionId = random.nextInt(65536)
        output.write((transactionId shr 8) and 0xFF)
        output.write(transactionId and 0xFF)
        output.write(0x01)  // Standard query
        output.write(0x00)
        output.write(0x00)  // 1 question
        output.write(0x01)
        output.write(0x00)  // 0 answers
        output.write(0x00)
        output.write(0x00)  // 0 authority
        output.write(0x00)
        output.write(0x00)  // 0 additional
        output.write(0x00)
        
        // Encode data as subdomain labels (max 63 chars each)
        val encoded = Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP)
        val chunks = encoded.chunked(60)
        
        for (chunk in chunks) {
            output.write(chunk.length)
            output.write(chunk.toByteArray())
        }
        
        // Domain suffix
        val suffix = "t.freedomvpn.net"
        for (label in suffix.split(".")) {
            output.write(label.length)
            output.write(label.toByteArray())
        }
        output.write(0x00)  // End of domain
        
        // Query type (TXT) and class (IN)
        output.write(0x00)
        output.write(0x10)  // TXT
        output.write(0x00)
        output.write(0x01)  // IN
        
        return output.toByteArray()
    }

    // ==================== SHADOWSOCKS-LIKE AEAD ====================
    
    /**
     * Implements AEAD encryption similar to Shadowsocks
     * Uses AES-GCM with random nonces
     */
    private val aeadKey = ByteArray(32).also { random.nextBytes(it) }
    
    fun wrapAsShadowsocks(data: ByteArray): ByteArray {
        val nonce = ByteArray(12)
        random.nextBytes(nonce)
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(aeadKey, "AES")
        val gcmSpec = GCMParameterSpec(128, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        
        val encrypted = cipher.doFinal(data)
        
        // Format: [salt(16)][nonce(12)][encrypted+tag]
        val salt = ByteArray(16)
        random.nextBytes(salt)
        
        val output = ByteArray(16 + 12 + encrypted.size)
        System.arraycopy(salt, 0, output, 0, 16)
        System.arraycopy(nonce, 0, output, 16, 12)
        System.arraycopy(encrypted, 0, output, 28, encrypted.size)
        
        return output
    }

    fun unwrapFromShadowsocks(data: ByteArray): ByteArray? {
        if (data.size < 28) return null
        
        val nonce = data.copyOfRange(16, 28)
        val encrypted = data.copyOfRange(28, data.size)
        
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(aeadKey, "AES")
            val gcmSpec = GCMParameterSpec(128, nonce)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            cipher.doFinal(encrypted)
        } catch (e: Exception) {
            null
        }
    }

    // ==================== RANDOM PADDING ====================
    
    /**
     * Adds random padding to defeat traffic analysis
     * Makes all packets similar size to hide patterns
     */
    fun addRandomPadding(data: ByteArray, targetSize: Int = 1400): ByteArray {
        if (data.size >= targetSize) return data
        
        val paddingSize = targetSize - data.size - 4  // 4 bytes for length header
        if (paddingSize <= 0) return data
        
        val padding = ByteArray(paddingSize)
        random.nextBytes(padding)
        
        val output = ByteBuffer.allocate(targetSize)
        output.putInt(data.size)  // Original data length
        output.put(data)
        output.put(padding)
        
        return output.array()
    }

    fun removeRandomPadding(data: ByteArray): ByteArray? {
        if (data.size < 4) return null
        
        val buffer = ByteBuffer.wrap(data)
        val originalSize = buffer.int
        
        if (originalSize <= 0 || originalSize > data.size - 4) return null
        
        val original = ByteArray(originalSize)
        buffer.get(original)
        
        return original
    }

    // ==================== TRAFFIC SHAPING ====================
    
    /**
     * Shapes traffic to look like normal browsing patterns
     * Adds delays and varies packet sizes
     */
    suspend fun shapeTraffic(data: ByteArray): ByteArray {
        // Add small random delay (0-50ms) to break timing patterns
        delay((random.nextInt(50)).toLong())
        
        // Sometimes split into smaller packets
        if (data.size > 500 && random.nextBoolean()) {
            // In real implementation, would queue and send separately
            return addRandomPadding(data, 576)  // Common MTU size
        }
        
        return data
    }

    // ==================== WEBSOCKET WRAP ====================
    
    /**
     * Wraps VPN traffic in WebSocket frames
     * Looks like normal web application traffic
     */
    fun wrapAsWebSocket(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        
        // WebSocket frame header
        output.write(0x82)  // Binary frame, FIN bit set
        
        val length = data.size
        when {
            length < 126 -> {
                output.write(0x80 or length)  // Masked
            }
            length < 65536 -> {
                output.write(0x80 or 126)
                output.write((length shr 8) and 0xFF)
                output.write(length and 0xFF)
            }
            else -> {
                output.write(0x80 or 127)
                for (i in 7 downTo 0) {
                    output.write((length shr (i * 8)) and 0xFF)
                }
            }
        }
        
        // Masking key (4 bytes)
        val mask = ByteArray(4)
        random.nextBytes(mask)
        output.write(mask)
        
        // Masked payload
        for (i in data.indices) {
            output.write((data[i].toInt() xor mask[i % 4].toInt()) and 0xFF)
        }
        
        return output.toByteArray()
    }

    // ==================== DOMAIN FRONTING ====================
    
    /**
     * Domain fronting technique - outer SNI differs from inner Host
     * Uses legitimate CDN domains to hide real destination
     */
    data class DomainFrontConfig(
        val frontDomain: String,      // What DPI sees (e.g., "ajax.googleapis.com")
        val realHost: String,          // Actual destination
        val cdnEndpoint: String        // CDN IP to connect to
    )

    val domainFrontConfigs = listOf(
        DomainFrontConfig("ajax.googleapis.com", "vpn.freedomvpn.net", "142.250.80.10"),
        DomainFrontConfig("cdn.cloudflare.com", "vpn.freedomvpn.net", "104.16.132.229"),
        DomainFrontConfig("static.akamai.com", "vpn.freedomvpn.net", "23.192.228.80"),
        DomainFrontConfig("cdn.microsoft.com", "vpn.freedomvpn.net", "13.107.246.10")
    )

    fun getDomainFrontConfig(): DomainFrontConfig {
        return domainFrontConfigs.random()
    }

    // ==================== PROTOCOL ROTATION ====================
    
    /**
     * Automatically rotates between protocols to evade fingerprinting
     */
    fun obfuscate(data: ByteArray): ByteArray {
        packetCounter++
        
        if (protocolRotationEnabled && packetCounter >= packetsUntilRotation) {
            rotateProtocol()
            packetCounter = 0
        }
        
        return when (_activeProtocol.value) {
            ObfuscationProtocol.NONE -> data
            ObfuscationProtocol.XOR_BASIC -> xorEncrypt(data, defaultXorKey)
            ObfuscationProtocol.TLS_CAMOUFLAGE -> wrapAsTls(data)
            ObfuscationProtocol.HTTP_CAMOUFLAGE -> wrapAsHttp(data)
            ObfuscationProtocol.DNS_TUNNEL -> wrapAsDns(data)
            ObfuscationProtocol.SHADOWSOCKS_LIKE -> wrapAsShadowsocks(data)
            ObfuscationProtocol.RANDOM_PADDING -> addRandomPadding(data)
            ObfuscationProtocol.TRAFFIC_SHAPING -> data  // Handled async
            ObfuscationProtocol.DOMAIN_FRONTING -> wrapAsTls(data)  // Combined with TLS
            ObfuscationProtocol.WEBSOCKET_WRAP -> wrapAsWebSocket(data)
        }
    }

    fun deobfuscate(data: ByteArray): ByteArray? {
        return when (_activeProtocol.value) {
            ObfuscationProtocol.NONE -> data
            ObfuscationProtocol.XOR_BASIC -> xorEncrypt(data, defaultXorKey)
            ObfuscationProtocol.TLS_CAMOUFLAGE -> unwrapFromTls(data)
            ObfuscationProtocol.HTTP_CAMOUFLAGE -> unwrapFromHttp(data)
            ObfuscationProtocol.DNS_TUNNEL -> data  // Complex, needs DNS response parsing
            ObfuscationProtocol.SHADOWSOCKS_LIKE -> unwrapFromShadowsocks(data)
            ObfuscationProtocol.RANDOM_PADDING -> removeRandomPadding(data)
            ObfuscationProtocol.TRAFFIC_SHAPING -> data
            ObfuscationProtocol.DOMAIN_FRONTING -> unwrapFromTls(data)
            ObfuscationProtocol.WEBSOCKET_WRAP -> data  // Complex WebSocket parsing
        }
    }

    private fun rotateProtocol() {
        val protocols = ObfuscationProtocol.values().filter { 
            it != ObfuscationProtocol.NONE && it != _activeProtocol.value 
        }
        _activeProtocol.value = protocols.random()
    }

    fun setProtocol(protocol: ObfuscationProtocol) {
        _activeProtocol.value = protocol
    }

    fun enableProtocolRotation(enabled: Boolean, packetsPerRotation: Int = 1000) {
        protocolRotationEnabled = enabled
        packetsUntilRotation = packetsPerRotation
    }

    // ==================== UTILITIES ====================
    
    private val defaultXorKey = byteArrayOf(
        0x5A.toByte(), 0x3C.toByte(), 0x7E.toByte(), 0x1F.toByte(), 
        0x9B.toByte(), 0x2D.toByte(), 0x4A.toByte(), 0x6C.toByte(),
        0x8E.toByte(), 0x0F.toByte(), 0xA1.toByte(), 0xB3.toByte(), 
        0xC5.toByte(), 0xD7.toByte(), 0xE9.toByte(), 0xFB.toByte()
    )

    private fun xorEncrypt(data: ByteArray, key: ByteArray): ByteArray {
        return ByteArray(data.size) { i ->
            data[i] xor key[i % key.size]
        }
    }

    private fun generateRequestId(): String {
        val bytes = ByteArray(8)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ==================== DPI DETECTION & EVASION ====================
    
    /**
     * Detects if DPI is blocking our traffic and switches protocols
     */
    suspend fun handleDpiDetection(connectionFailed: Boolean) {
        if (connectionFailed) {
            _bypassSuccess.value = false
            
            // Try next protocol
            rotateProtocol()
            
            // If all protocols fail, try DNS tunnel as last resort
            if (packetCounter > ObfuscationProtocol.values().size * packetsUntilRotation) {
                _activeProtocol.value = ObfuscationProtocol.DNS_TUNNEL
            }
        } else {
            _bypassSuccess.value = true
        }
    }

    fun destroy() {
        scope.cancel()
    }
}
