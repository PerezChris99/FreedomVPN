package com.freedomvpn.vpn.resilience

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline Mode & Connection Resilience Manager
 * 
 * Handles:
 * - Complete internet shutdowns (cannot solve but can prepare)
 * - DNS blocking (use cached/alternate DNS)
 * - Unstable connections
 * - Connection persistence across network changes
 * - Server config caching for quick reconnect
 */
@Singleton
class OfflineResilienceManager @Inject constructor(
    private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private val _connectionState = MutableStateFlow(ConnectionState.UNKNOWN)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _isInternetAvailable = MutableStateFlow(true)
    val isInternetAvailable: StateFlow<Boolean> = _isInternetAvailable

    private val _offlineMode = MutableStateFlow(false)
    val offlineMode: StateFlow<Boolean> = _offlineMode

    private lateinit var prefs: SharedPreferences
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    enum class ConnectionState {
        UNKNOWN,
        NO_NETWORK,           // No network at all
        NETWORK_NO_INTERNET,  // Network exists but no internet (shutdown?)
        DNS_BLOCKED,          // Internet works but DNS is blocked
        VPN_BLOCKED,          // Internet works but VPN protocols blocked
        PARTIAL,              // Limited connectivity
        CONNECTED             // Full connectivity
    }

    // ==================== INITIALIZATION ====================

    fun initialize() {
        initializeSecurePrefs()
        setupNetworkMonitoring()
        loadCachedConfigs()
    }

    private fun initializeSecurePrefs() {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            prefs = EncryptedSharedPreferences.create(
                context,
                "freedom_vpn_offline_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            prefs = context.getSharedPreferences("freedom_vpn_offline_fallback", Context.MODE_PRIVATE)
        }
    }

    // ==================== NETWORK MONITORING ====================

    private fun setupNetworkMonitoring() {
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch { checkConnectivityState() }
            }

            override fun onLost(network: Network) {
                _connectionState.value = ConnectionState.NO_NETWORK
                _isInternetAvailable.value = false
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                
                if (hasInternet && validated) {
                    scope.launch { checkConnectivityState() }
                } else if (hasInternet && !validated) {
                    _connectionState.value = ConnectionState.NETWORK_NO_INTERNET
                    _isInternetAvailable.value = false
                }
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager?.registerNetworkCallback(request, networkCallback!!)
    }

    /**
     * Comprehensive connectivity check
     */
    suspend fun checkConnectivityState(): ConnectionState = withContext(Dispatchers.IO) {
        // Check basic network
        val cm = connectivityManager ?: return@withContext ConnectionState.UNKNOWN
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)

        if (network == null || caps == null) {
            _connectionState.value = ConnectionState.NO_NETWORK
            _isInternetAvailable.value = false
            return@withContext ConnectionState.NO_NETWORK
        }

        // Check if we can reach the internet
        val canReachInternet = checkInternetReachability()
        if (!canReachInternet) {
            _connectionState.value = ConnectionState.NETWORK_NO_INTERNET
            _isInternetAvailable.value = false
            return@withContext ConnectionState.NETWORK_NO_INTERNET
        }

        // Check DNS
        val dnsWorks = checkDnsResolution()
        if (!dnsWorks) {
            _connectionState.value = ConnectionState.DNS_BLOCKED
            _isInternetAvailable.value = true  // Internet works, just DNS blocked
            return@withContext ConnectionState.DNS_BLOCKED
        }

        _connectionState.value = ConnectionState.CONNECTED
        _isInternetAvailable.value = true
        ConnectionState.CONNECTED
    }

    private suspend fun checkInternetReachability(): Boolean {
        // Try multiple endpoints
        val endpoints = listOf(
            "1.1.1.1",        // Cloudflare
            "8.8.8.8",        // Google
            "208.67.222.222", // OpenDNS
            "9.9.9.9"         // Quad9
        )

        for (endpoint in endpoints) {
            try {
                val address = InetAddress.getByName(endpoint)
                if (address.isReachable(3000)) {
                    return true
                }
            } catch (e: Exception) {
                continue
            }
        }
        return false
    }

    private suspend fun checkDnsResolution(): Boolean {
        val testDomains = listOf(
            "google.com",
            "cloudflare.com",
            "microsoft.com"
        )

        for (domain in testDomains) {
            try {
                val address = InetAddress.getByName(domain)
                if (address.hostAddress?.isNotEmpty() == true) {
                    return true
                }
            } catch (e: Exception) {
                continue
            }
        }
        return false
    }

    // ==================== CACHED CONFIGURATIONS ====================

    @Serializable
    data class CachedServerConfig(
        val hostname: String,
        val ip: String,
        val country: String,
        val port: Int,
        val protocol: String,
        val publicKey: String? = null,
        val cachedAt: Long = System.currentTimeMillis()
    )

    private val serverConfigCache = ConcurrentHashMap<String, CachedServerConfig>()

    fun cacheServerConfig(config: CachedServerConfig) {
        serverConfigCache[config.hostname] = config
        saveConfigsToStorage()
    }

    fun cacheMultipleConfigs(configs: List<CachedServerConfig>) {
        configs.forEach { serverConfigCache[it.hostname] = it }
        saveConfigsToStorage()
    }

    fun getCachedConfig(hostname: String): CachedServerConfig? {
        return serverConfigCache[hostname]
    }

    fun getAllCachedConfigs(): List<CachedServerConfig> {
        return serverConfigCache.values.toList()
    }

    fun getRecentConfigs(maxAgeMs: Long = 24 * 60 * 60 * 1000): List<CachedServerConfig> {
        val now = System.currentTimeMillis()
        return serverConfigCache.values.filter { now - it.cachedAt < maxAgeMs }
    }

    private fun saveConfigsToStorage() {
        scope.launch {
            try {
                val configList = serverConfigCache.values.toList()
                val jsonString = json.encodeToString(configList)
                prefs.edit().putString(KEY_CACHED_CONFIGS, jsonString).apply()
            } catch (e: Exception) {
                // Ignore serialization errors
            }
        }
    }

    private fun loadCachedConfigs() {
        try {
            val jsonString = prefs.getString(KEY_CACHED_CONFIGS, null) ?: return
            val configs = json.decodeFromString<List<CachedServerConfig>>(jsonString)
            configs.forEach { serverConfigCache[it.hostname] = it }
        } catch (e: Exception) {
            // Ignore deserialization errors
        }
    }

    // ==================== ALTERNATE DNS ====================

    /**
     * Cached DNS records for when DNS is blocked
     */
    private val dnsCache = ConcurrentHashMap<String, CachedDnsRecord>()

    @Serializable
    data class CachedDnsRecord(
        val hostname: String,
        val ip: String,
        val cachedAt: Long = System.currentTimeMillis(),
        val ttl: Long = 3600000  // 1 hour default
    )

    // Hardcoded DNS-over-HTTPS endpoints
    val dohEndpoints = listOf(
        "https://1.1.1.1/dns-query",
        "https://8.8.8.8/dns-query",
        "https://dns.google/dns-query",
        "https://cloudflare-dns.com/dns-query"
    )

    // Alternate DNS servers (when system DNS is blocked)
    val alternateDnsServers = listOf(
        "1.1.1.1",        // Cloudflare
        "1.0.0.1",        // Cloudflare secondary
        "8.8.8.8",        // Google
        "8.8.4.4",        // Google secondary
        "9.9.9.9",        // Quad9
        "208.67.222.222", // OpenDNS
        "208.67.220.220"  // OpenDNS secondary
    )

    fun cacheDnsRecord(hostname: String, ip: String, ttl: Long = 3600000) {
        dnsCache[hostname] = CachedDnsRecord(hostname, ip, ttl = ttl)
        saveDnsCacheToStorage()
    }

    fun getCachedDns(hostname: String): String? {
        val record = dnsCache[hostname] ?: return null
        val now = System.currentTimeMillis()
        
        // Check if still valid
        if (now - record.cachedAt > record.ttl) {
            dnsCache.remove(hostname)
            return null
        }
        
        return record.ip
    }

    private fun saveDnsCacheToStorage() {
        scope.launch {
            try {
                val records = dnsCache.values.toList()
                val jsonString = json.encodeToString(records)
                prefs.edit().putString(KEY_DNS_CACHE, jsonString).apply()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    // ==================== CONNECTION PERSISTENCE ====================

    @Serializable
    data class ConnectionState(
        val serverId: String,
        val serverIp: String,
        val connectedAt: Long,
        val lastActivity: Long,
        val bytesUp: Long,
        val bytesDown: Long
    ) {
        companion object {
            val UNKNOWN = com.freedomvpn.vpn.resilience.OfflineResilienceManager.ConnectionState.UNKNOWN
        }
    }

    private var savedConnectionState: ConnectionState? = null

    fun saveConnectionState(state: ConnectionState) {
        savedConnectionState = state
        prefs.edit().putString(KEY_CONNECTION_STATE, json.encodeToString(state)).apply()
    }

    fun loadConnectionState(): ConnectionState? {
        if (savedConnectionState != null) return savedConnectionState
        
        return try {
            val jsonString = prefs.getString(KEY_CONNECTION_STATE, null) ?: return null
            json.decodeFromString<ConnectionState>(jsonString).also { savedConnectionState = it }
        } catch (e: Exception) {
            null
        }
    }

    fun clearConnectionState() {
        savedConnectionState = null
        prefs.edit().remove(KEY_CONNECTION_STATE).apply()
    }

    // ==================== QUICK RECONNECT ====================

    /**
     * Attempt to reconnect using cached data when connection drops
     */
    suspend fun attemptQuickReconnect(): ReconnectResult {
        val lastState = loadConnectionState() ?: return ReconnectResult.NO_SAVED_STATE
        
        // Check if server IP is still reachable
        val serverReachable = try {
            InetAddress.getByName(lastState.serverIp).isReachable(5000)
        } catch (e: Exception) {
            false
        }

        if (!serverReachable) {
            // Try cached servers
            val cachedConfigs = getRecentConfigs()
            for (config in cachedConfigs) {
                val reachable = try {
                    InetAddress.getByName(config.ip).isReachable(5000)
                } catch (e: Exception) {
                    false
                }
                if (reachable) {
                    return ReconnectResult.USE_ALTERNATE(config)
                }
            }
            return ReconnectResult.ALL_SERVERS_UNREACHABLE
        }

        return ReconnectResult.RECONNECT_TO_LAST(lastState.serverId, lastState.serverIp)
    }

    sealed class ReconnectResult {
        object NO_SAVED_STATE : ReconnectResult()
        object ALL_SERVERS_UNREACHABLE : ReconnectResult()
        data class RECONNECT_TO_LAST(val serverId: String, val serverIp: String) : ReconnectResult()
        data class USE_ALTERNATE(val config: CachedServerConfig) : ReconnectResult()
    }

    // ==================== SHUTDOWN DETECTION ====================

    /**
     * Detect if this looks like a government internet shutdown
     */
    suspend fun detectShutdown(): ShutdownAnalysis = withContext(Dispatchers.IO) {
        val networkExists = connectivityManager?.activeNetwork != null
        val canReachIp = checkInternetReachability()
        val dnsWorks = checkDnsResolution()

        when {
            !networkExists -> ShutdownAnalysis(
                type = ShutdownType.FULL_SHUTDOWN,
                message = "No network connection. This may be a complete internet shutdown.",
                suggestions = listOf(
                    "Wait for connectivity to be restored",
                    "Try a different network (mobile/WiFi)",
                    "Check if others in your area have internet"
                )
            )
            !canReachIp -> ShutdownAnalysis(
                type = ShutdownType.IP_LEVEL_BLOCK,
                message = "Network exists but internet is unreachable. Possible IP-level blocking.",
                suggestions = listOf(
                    "This may be a partial or full shutdown",
                    "Try again in a few minutes",
                    "Some services may still work"
                )
            )
            !dnsWorks -> ShutdownAnalysis(
                type = ShutdownType.DNS_BLOCKED,
                message = "DNS is blocked but internet is accessible.",
                suggestions = listOf(
                    "Using cached DNS records",
                    "Switching to encrypted DNS",
                    "VPN should still work with direct IP connections"
                )
            )
            else -> ShutdownAnalysis(
                type = ShutdownType.NONE,
                message = "Internet connection appears normal.",
                suggestions = emptyList()
            )
        }
    }

    enum class ShutdownType {
        NONE,
        DNS_BLOCKED,
        IP_LEVEL_BLOCK,
        PARTIAL_SHUTDOWN,
        FULL_SHUTDOWN
    }

    data class ShutdownAnalysis(
        val type: ShutdownType,
        val message: String,
        val suggestions: List<String>
    )

    // ==================== CLEANUP ====================

    fun destroy() {
        networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        scope.cancel()
    }

    companion object {
        private const val KEY_CACHED_CONFIGS = "cached_server_configs"
        private const val KEY_DNS_CACHE = "dns_cache"
        private const val KEY_CONNECTION_STATE = "connection_state"
    }
}
