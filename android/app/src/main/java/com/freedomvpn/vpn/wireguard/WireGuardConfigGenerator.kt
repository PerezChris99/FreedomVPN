package com.freedomvpn.vpn.wireguard

import android.util.Log
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyPair
import com.freedomvpn.vpngate.VpnGateServer
import java.net.InetAddress

/**
 * WireGuard Configuration Generator
 * 
 * Generates WireGuard configuration from VPN Gate server info.
 * Note: VPN Gate primarily uses OpenVPN, but this provides WireGuard
 * config generation for servers that support it, or for custom servers.
 * 
 * For Uganda and censored regions, WireGuard is preferred because:
 * 1. Faster connection establishment (1-RTT)
 * 2. Lower overhead (less identifiable traffic patterns)
 * 3. Better performance on mobile networks
 * 4. Smaller code footprint (harder to detect)
 */
object WireGuardConfigGenerator {
    
    private const val TAG = "WireGuardConfig"
    
    // DNS servers for privacy and censorship bypass
    private val DNS_SERVERS = listOf(
        "8.8.8.8",          // Google Primary
        "8.8.4.4",          // Google Secondary
        "1.1.1.1",          // Cloudflare Primary
        "1.0.0.1",          // Cloudflare Secondary
        "9.9.9.9",          // Quad9 (malware blocking)
        "208.67.222.222"    // OpenDNS
    )
    
    // Default WireGuard port
    private const val DEFAULT_PORT = 51820
    
    // Alternative ports for bypassing blocks
    private val ALTERNATIVE_PORTS = listOf(443, 80, 53, 1194, 4500)
    
    /**
     * Generate a new WireGuard key pair for the client
     */
    fun generateKeyPair(): KeyPair {
        return KeyPair()
    }
    
    /**
     * Create WireGuard config from server information
     * 
     * @param serverPublicKey The server's public key (base64)
     * @param serverEndpoint The server's IP or hostname
     * @param serverPort The server's WireGuard port
     * @param clientPrivateKey Client's private key (generate with generateKeyPair())
     * @param clientAddress VPN address assigned to client (e.g., "10.0.0.2/32")
     * @param allowedIPs IPs to route through VPN (default: all traffic)
     */
    fun createConfig(
        serverPublicKey: String,
        serverEndpoint: String,
        serverPort: Int = DEFAULT_PORT,
        clientPrivateKey: Key,
        clientAddress: String = "10.0.0.2/32",
        allowedIPs: List<String> = listOf("0.0.0.0/0", "::/0"),
        persistentKeepalive: Int = 25
    ): Config {
        try {
            // Build interface config
            val interfaceBuilder = Interface.Builder()
                .parsePrivateKey(clientPrivateKey.toBase64())
                .parseAddresses(clientAddress)
                .parseDnsServers(DNS_SERVERS.joinToString(", "))
            
            val interfaceConfig = interfaceBuilder.build()
            
            // Build peer config (the server)
            val peerBuilder = Peer.Builder()
                .parsePublicKey(serverPublicKey)
                .parseEndpoint("$serverEndpoint:$serverPort")
                .parseAllowedIPs(allowedIPs.joinToString(", "))
                .parsePersistentKeepalive(persistentKeepalive.toString())
            
            val peerConfig = peerBuilder.build()
            
            // Build complete config
            val config = Config.Builder()
                .setInterface(interfaceConfig)
                .addPeer(peerConfig)
                .build()
            
            Log.d(TAG, "Created WireGuard config for $serverEndpoint:$serverPort")
            return config
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create config", e)
            throw e
        }
    }
    
    /**
     * Create config from VPN Gate server
     * Note: VPN Gate uses OpenVPN, so this is for future WireGuard-enabled servers
     */
    fun createConfigFromVpnGate(
        server: VpnGateServer,
        serverPublicKey: String,
        clientKeyPair: KeyPair
    ): Config {
        return createConfig(
            serverPublicKey = serverPublicKey,
            serverEndpoint = server.ip,
            serverPort = server.port.takeIf { it > 0 } ?: DEFAULT_PORT,
            clientPrivateKey = clientKeyPair.privateKey,
            // Use aggressive keepalive for mobile networks in Uganda
            persistentKeepalive = 15
        )
    }
    
    /**
     * Export config to WireGuard INI format
     * Useful for debugging or sharing
     */
    fun configToString(config: Config): String {
        return config.toWgQuickString()
    }
    
    /**
     * Parse config from WireGuard INI format
     */
    fun parseConfig(configString: String): Config {
        return Config.parse(configString.byteInputStream())
    }
    
    /**
     * Create a config string directly (useful for quick setup)
     */
    fun createConfigString(
        serverPublicKey: String,
        serverEndpoint: String,
        serverPort: Int = DEFAULT_PORT,
        clientPrivateKey: String,
        clientAddress: String = "10.0.0.2/32"
    ): String {
        return """
            [Interface]
            PrivateKey = $clientPrivateKey
            Address = $clientAddress
            DNS = ${DNS_SERVERS.take(3).joinToString(", ")}
            
            [Peer]
            PublicKey = $serverPublicKey
            Endpoint = $serverEndpoint:$serverPort
            AllowedIPs = 0.0.0.0/0, ::/0
            PersistentKeepalive = 25
        """.trimIndent()
    }
    
    /**
     * Get alternative ports for bypassing firewalls
     * Tries common ports that are less likely to be blocked
     */
    fun getAlternativePorts(): List<Int> {
        return ALTERNATIVE_PORTS
    }
}
