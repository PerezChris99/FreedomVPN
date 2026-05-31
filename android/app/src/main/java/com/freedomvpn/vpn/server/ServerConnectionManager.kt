package com.freedomvpn.vpn.server

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.freedomvpn.vpn.wireguard.WireGuardConfigGenerator
import com.freedomvpn.vpn.wireguard.WireGuardManager
import com.wireguard.crypto.KeyPair
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the end-to-end connection flow for the FreedomVPN server:
 *
 *   1. Generate (or restore) the client WireGuard key pair
 *   2. Register the public key with the FreedomVPN server API
 *   3. Build a WireGuard config using the returned credentials
 *   4. Start the WireGuard tunnel via WireGuardManager
 *
 * The private key NEVER leaves the device — only the public key is sent.
 * Credentials (assigned IP, server public key) are stored in EncryptedSharedPreferences
 * so reconnection is near-instant without hitting the server each time.
 */
@Singleton
class ServerConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serverApiClient: ServerApiClient,
    private val wireGuardManager: WireGuardManager,
) {
    companion object {
        private const val TAG = "ServerConnectionManager"

        // EncryptedSharedPreferences keys
        private const val PREFS_FILE        = "freedomvpn_server_creds"
        private const val KEY_PEER_ID       = "peer_id"
        private const val KEY_ASSIGNED_IP   = "assigned_ip"
        private const val KEY_SERVER_PUBKEY = "server_public_key"
        private const val KEY_SERVER_EP     = "server_endpoint"
        private const val KEY_DNS           = "dns_json"
        private const val KEY_DEVICE_ID     = "device_id"
    }

    // Lazily created encrypted prefs — EncryptedSharedPreferences is backed by Android Keystore
    private val encryptedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // Stable (but anonymous) device identifier.
    private fun getDeviceId(): String {
        val stored = encryptedPrefs.getString(KEY_DEVICE_ID, null)
        if (stored != null) return stored
        val generated = java.util.UUID.randomUUID().toString().replace("-", "")
        encryptedPrefs.edit().putString(KEY_DEVICE_ID, generated).apply()
        return generated
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Connect to the FreedomVPN server via the production peer API flow.
     *
     * Steps:
     *  1. Get/generate client WireGuard key pair (stored in WireGuardManager)
     *  2. Register public key with server → receive tunnel credentials
     *  3. Cache credentials in EncryptedSharedPreferences
     *  4. Build WireGuard config and start tunnel
     *
     * Returns true if the WireGuard tunnel started successfully.
     */
    suspend fun connectToServer(
        serverUrl: String? = null,                  // Override default server URL
        preferCachedCredentials: Boolean = true,    // Skip API call if valid cache exists
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (serverUrl != null) {
                ServerApiClient.BASE_URL = serverUrl
            }

            // 1. Get or generate the client WireGuard key pair
            val keyPair: KeyPair = wireGuardManager.getOrCreateKeyPair()
            val publicKeyBase64 = keyPair.publicKey.toBase64()
            Log.d(TAG, "Client public key: $publicKeyBase64")

            // 2. Get credentials (from cache or fresh registration)
            val credentials = if (preferCachedCredentials) {
                loadCachedCredentials() ?: registerAndCache(publicKeyBase64)
            } else {
                registerAndCache(publicKeyBase64)
            }

            credentials ?: return@withContext Result.failure(
                Exception("Failed to obtain server credentials")
            )

            // 3. Build WireGuard config using server credentials
            // Parse endpoint host and port from "host:port"
            val (endpointHost, endpointPort) = parseEndpoint(credentials.serverEndpoint)

            // Use the assigned IP returned by the server, not a local default
            val config = WireGuardConfigGenerator.createConfig(
                serverPublicKey  = credentials.serverPublicKey,
                serverEndpoint   = endpointHost,
                serverPort       = endpointPort,
                clientPrivateKey = keyPair.privateKey,
                clientAddress    = credentials.assignedIP,   // e.g. "10.8.0.5/32"
                allowedIPs       = listOf("0.0.0.0/0"),      // route ALL traffic through VPN
                persistentKeepalive = 25,
            )

            // 4. Start the WireGuard tunnel
            Log.d(TAG, "Starting WireGuard tunnel to ${credentials.serverEndpoint}")
            val started = wireGuardManager.connectWithConfig(config.toWgQuickString())
            Result.success(started)

        } catch (e: Exception) {
            Log.e(TAG, "connectToServer failed", e)
            Result.failure(e)
        }
    }

    /**
     * Clear cached credentials and disconnect.
     * Call this when the user explicitly logs out or resets the app.
     */
    suspend fun disconnectAndClear() = withContext(Dispatchers.IO) {
        wireGuardManager.disconnect()
        encryptedPrefs.edit().clear().apply()
        Log.d(TAG, "Cleared cached server credentials")
    }

    /**
     * Quick reconnect using cached credentials (no API call).
     * Useful for reconnect-on-network-change scenarios.
     */
    suspend fun reconnect(): Result<Boolean> {
        return connectToServer(preferCachedCredentials = true)
    }

    /**
     * Returns true if we have a valid cached server credential that can be used
     * for a reconnect without going to the API.
     */
    fun hasCachedCredentials(): Boolean = loadCachedCredentials() != null

    // ── Private helpers ────────────────────────────────────────────────────────

    private suspend fun registerAndCache(publicKeyBase64: String): ServerApiClient.PeerCredentials? {
        val result = serverApiClient.registerPeer(
            publicKeyBase64 = publicKeyBase64,
            platform        = "android",
            deviceId        = getDeviceId(),
        )
        return if (result.isSuccess) {
            val creds = result.getOrThrow()
            cacheCredentials(creds)
            creds
        } else {
            Log.e(TAG, "Peer registration failed: ${result.exceptionOrNull()?.message}")
            null
        }
    }

    private fun cacheCredentials(creds: ServerApiClient.PeerCredentials) {
        encryptedPrefs.edit()
            .putString(KEY_PEER_ID, creds.id)
            .putString(KEY_ASSIGNED_IP, creds.assignedIP)
            .putString(KEY_SERVER_PUBKEY, creds.serverPublicKey)
            .putString(KEY_SERVER_EP, creds.serverEndpoint)
            .putString(KEY_DNS, JSONArray(creds.dns).toString())
            .apply()
        Log.d(TAG, "Credentials cached for peer ${creds.id}")
    }

    private fun loadCachedCredentials(): ServerApiClient.PeerCredentials? {
        val id        = encryptedPrefs.getString(KEY_PEER_ID, null) ?: return null
        val ip        = encryptedPrefs.getString(KEY_ASSIGNED_IP, null) ?: return null
        val pubKey    = encryptedPrefs.getString(KEY_SERVER_PUBKEY, null) ?: return null
        val endpoint  = encryptedPrefs.getString(KEY_SERVER_EP, null) ?: return null
        val dnsStr    = encryptedPrefs.getString(KEY_DNS, "[]") ?: "[]"

        val dnsArr    = JSONArray(dnsStr)
        val dns       = (0 until dnsArr.length()).map { dnsArr.getString(it) }

        return ServerApiClient.PeerCredentials(
            id              = id,
            assignedIP      = ip,
            serverPublicKey = pubKey,
            serverEndpoint  = endpoint,
            dns             = dns,
        )
    }

    /**
     * Parse "host:port" into Pair<host, port>. Handles IPv6 [::1]:port notation.
     */
    private fun parseEndpoint(endpoint: String): Pair<String, Int> {
        // IPv6 bracket notation: [::1]:51820
        if (endpoint.startsWith("[")) {
            val closeBracket = endpoint.lastIndexOf(']')
            val host = endpoint.substring(1, closeBracket)
            val port = endpoint.substringAfterLast(':').toIntOrNull() ?: 51820
            return Pair(host, port)
        }
        // Standard host:port
        val lastColon = endpoint.lastIndexOf(':')
        return if (lastColon >= 0) {
            val host = endpoint.substring(0, lastColon)
            val port = endpoint.substring(lastColon + 1).toIntOrNull() ?: 51820
            Pair(host, port)
        } else {
            Pair(endpoint, 51820)
        }
    }
}
