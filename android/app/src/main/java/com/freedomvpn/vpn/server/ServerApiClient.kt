package com.freedomvpn.vpn.server

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CertificatePinner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for the FreedomVPN peer management API (server/api/).
 *
 * Registers the client's WireGuard public key with the server and
 * receives tunnel credentials (assigned IP, server public key, endpoint).
 *
 * The client NEVER sends the private key — only the public key.
 * The server never has enough information to decrypt the client's traffic.
 *
 * Usage:
 *   val result = serverApiClient.registerPeer(myPublicKeyBase64)
 *   if (result.isSuccess) {
 *       val creds = result.getOrThrow()
 *       wireGuardManager.connect(creds.serverPublicKey, creds.serverEndpoint)
 *   }
 */
@Singleton
class ServerApiClient @Inject constructor() {

    companion object {
        private const val TAG = "ServerApiClient"

        // Override by injecting a ServerConfig or setting this before first use.
        // In production this should come from a BuildConfig constant or remote config.
        var BASE_URL: String = BuildConfigHelper.SERVER_BASE_URL

        /**
         * Production SPKI pins are supplied at build time.
         * Never ship placeholder pins: a release without real pins must fail
         * during the Gradle configuration step.
         */
        val CERT_PINS: Array<String>
            get() = BuildConfigHelper.SERVER_CERT_PINS
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toTypedArray()

    }

    data class PeerCredentials(
        val id: String,
        val assignedIP: String,           // e.g. "10.8.0.5/32"
        val serverPublicKey: String,      // Base64 WireGuard public key
        val serverEndpoint: String,       // "host:port"
        val dns: List<String>,            // DNS servers to use in tunnel config
        val reregistered: Boolean = false,
    )

    data class ServerInfo(
        val id: String,
        val name: String,
        val country: String,
        val countryCode: String,
        val city: String,
        val endpoint: String,
        val publicKey: String,
    )

    private val httpClient: OkHttpClient by lazy {
        val hostname = runCatching {
            java.net.URI(BASE_URL).host ?: ""
        }.getOrDefault("")

        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)

        require(BASE_URL.startsWith("https://")) {
            "FreedomVPN server API requires HTTPS"
        }
        require(hostname.isNotEmpty()) {
            "FreedomVPN server API URL must contain a valid hostname"
        }
        require(CERT_PINS.isNotEmpty() && CERT_PINS.all { it.startsWith("sha256/") }) {
            "FreedomVPN release requires valid SHA-256 SPKI certificate pins"
        }

        val pinner = CertificatePinner.Builder().apply {
            CERT_PINS.forEach { pin -> add(hostname, pin) }
        }.build()
        builder.certificatePinner(pinner)

        builder.build()
    }

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Register (or re-register) a WireGuard peer with the server.
     *
     * @param publicKeyBase64 44-char base64 WireGuard public key
     * @param platform        "android" (or "windows", "web", "extension")
     * @param deviceId        opaque device identifier for deduplication
     * @return PeerCredentials on success, error on failure
     */
    suspend fun registerPeer(
        publicKeyBase64: String,
        platform: String = "android",
        deviceId: String = "",
    ): Result<PeerCredentials> = withContext(Dispatchers.IO) {
        try {
            // Validate key format before sending
            if (!isValidWireGuardKey(publicKeyBase64)) {
                return@withContext Result.failure(
                    IllegalArgumentException("Invalid WireGuard public key format")
                )
            }

            val body = JSONObject().apply {
                put("publicKey", publicKeyBase64)
                put("platform", platform)
                put("deviceId", deviceId)
            }.toString().toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url("$BASE_URL/api/peers/register")
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                val msg = "Server returned ${response.code}: ${responseBody?.take(200)}"
                Log.e(TAG, "registerPeer failed: $msg")
                return@withContext Result.failure(Exception(msg))
            }

            val json  = JSONObject(responseBody)
            val dnsArr = json.optJSONArray("dns")
            val dns   = mutableListOf<String>()
            if (dnsArr != null) {
                for (i in 0 until dnsArr.length()) dns.add(dnsArr.getString(i))
            }

            val creds = PeerCredentials(
                id              = json.getString("id"),
                assignedIP      = json.getString("assignedIP"),
                serverPublicKey = json.getString("serverPublicKey"),
                serverEndpoint  = json.getString("serverEndpoint"),
                dns             = dns,
                reregistered    = json.optBoolean("reregistered", false),
            )

            Log.d(TAG, "Peer registered: id=${creds.id} ip=${creds.assignedIP} reregistered=${creds.reregistered}")
            Result.success(creds)

        } catch (e: Exception) {
            Log.e(TAG, "registerPeer exception", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch the server list (no auth required).
     */
    suspend fun fetchServers(): Result<List<ServerInfo>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/api/servers")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                return@withContext Result.failure(Exception("Server returned ${response.code}"))
            }

            val json    = JSONObject(responseBody)
            val arr     = json.getJSONArray("servers")
            val servers = mutableListOf<ServerInfo>()

            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                servers.add(ServerInfo(
                    id          = s.optString("id", ""),
                    name        = s.optString("name", "Unknown"),
                    country     = s.optString("country", ""),
                    countryCode = s.optString("countryCode", ""),
                    city        = s.optString("city", ""),
                    endpoint    = s.optString("endpoint", ""),
                    publicKey   = s.optString("publicKey", ""),
                ))
            }

            Result.success(servers)
        } catch (e: Exception) {
            Log.e(TAG, "fetchServers exception", e)
            Result.failure(e)
        }
    }

    /**
     * Check server health. Returns true if the server is reachable and WireGuard is up.
     */
    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/api/health")
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext false
            val json = JSONObject(response.body?.string() ?: return@withContext false)
            json.optString("status") == "ok" && json.optString("wireguard") == "up"
        } catch (e: Exception) {
            Log.w(TAG, "Health check failed: ${e.message}")
            false
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Validate WireGuard public key format client-side before transmitting.
     * 44 base64 chars, decoding to exactly 32 bytes.
     */
    private fun isValidWireGuardKey(key: String): Boolean {
        if (key.length != 44) return false
        if (!key.matches(Regex("^[A-Za-z0-9+/]{43}=$"))) return false
        return try {
            android.util.Base64.decode(key, android.util.Base64.DEFAULT).size == 32
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Provides build-time server configuration.
 * In a release build, SERVER_BASE_URL is set via BuildConfig.
 * In development/test builds, defaults to localhost.
 */
private object BuildConfigHelper {
    val SERVER_BASE_URL: String
        get() = BuildConfig.SERVER_BASE_URL

    val SERVER_CERT_PINS: Array<String>
        get() = BuildConfig.SERVER_CERT_PINS
}
