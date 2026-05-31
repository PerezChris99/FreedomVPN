package com.freedomvpn.vpngate

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Smart Connect Service
 * 
 * Provides intelligent VPN server selection and connection:
 * - One-tap Quick Connect to optimal server
 * - Country/region filtering
 * - Real-time ping testing
 * - Automatic failover
 * 
 * Optimized for Uganda and censored regions:
 * - Prefers servers with good African connectivity
 * - Falls back to cached servers if API blocked
 * - Measures actual latency, not just reported
 */
@Singleton
class SmartConnectService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: VpnGateRepository
) {
    companion object {
        private const val TAG = "SmartConnectService"
        private const val PING_TIMEOUT_MS = 3000
        private const val MAX_PARALLEL_PINGS = 10
    }

    /**
     * Connection recommendation
     */
    data class ServerRecommendation(
        val server: VpnGateServer,
        val reason: String,
        val measuredPing: Int? = null,
        val confidence: Float = 1.0f
    )

    /**
     * Quick connect state
     */
    sealed class QuickConnectState {
        object Idle : QuickConnectState()
        object Searching : QuickConnectState()
        data class Found(val recommendations: List<ServerRecommendation>) : QuickConnectState()
        data class Error(val message: String) : QuickConnectState()
    }

    private val _state = MutableStateFlow<QuickConnectState>(QuickConnectState.Idle)
    val state: StateFlow<QuickConnectState> = _state.asStateFlow()

    private val serverCache = ServerCache(context)
    private val serverSelector = ServerSelector()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Quick Connect - Find and return the best server
     */
    suspend fun quickConnect(): ServerRecommendation? {
        _state.value = QuickConnectState.Searching
        
        return try {
            // Try to get servers from API
            var servers = repository.fetchServers().getOrNull()
            
            // Fall back to cache if API fails
            if (servers.isNullOrEmpty()) {
                Log.d(TAG, "API failed, using cached servers")
                servers = serverCache.getServers(ignoreExpiry = true)
            }
            
            if (servers.isNullOrEmpty()) {
                _state.value = QuickConnectState.Error("No servers available")
                return null
            }
            
            // Cache the fresh servers
            serverCache.saveServers(servers)
            
            // Use smart selection to find best server
            val best = serverSelector.selectBest(servers)
            
            if (best == null) {
                _state.value = QuickConnectState.Error("No suitable servers found")
                return null
            }
            
            // Optionally measure actual ping
            val measuredPing = measurePing(best)
            
            val recommendation = ServerRecommendation(
                server = best,
                reason = "Best overall performance",
                measuredPing = measuredPing,
                confidence = 0.9f
            )
            
            // Save as last server
            serverCache.saveLastServer(best)
            
            _state.value = QuickConnectState.Found(listOf(recommendation))
            recommendation
            
        } catch (e: Exception) {
            Log.e(TAG, "Quick connect failed", e)
            _state.value = QuickConnectState.Error(e.message ?: "Unknown error")
            null
        }
    }

    /**
     * Quick Connect to last used server
     */
    suspend fun quickReconnect(): ServerRecommendation? {
        val lastServer = serverCache.getLastServer()
        
        if (lastServer != null) {
            val measuredPing = measurePing(lastServer)
            
            return ServerRecommendation(
                server = lastServer,
                reason = "Last connected server",
                measuredPing = measuredPing,
                confidence = 0.8f
            )
        }
        
        // Fall back to regular quick connect
        return quickConnect()
    }

    /**
     * Find best server for a specific country
     */
    suspend fun findBestForCountry(countryCode: String): ServerRecommendation? {
        _state.value = QuickConnectState.Searching
        
        return try {
            val servers = repository.fetchServers().getOrDefault(emptyList())
            val countryServers = servers.filter { 
                it.countryShort.equals(countryCode, ignoreCase = true) 
            }
            
            if (countryServers.isEmpty()) {
                _state.value = QuickConnectState.Error("No servers in $countryCode")
                return null
            }
            
            val best = serverSelector.selectBest(countryServers)
            
            if (best != null) {
                val recommendation = ServerRecommendation(
                    server = best,
                    reason = "Best server in ${best.countryLong}",
                    measuredPing = measurePing(best),
                    confidence = 0.85f
                )
                _state.value = QuickConnectState.Found(listOf(recommendation))
                recommendation
            } else {
                _state.value = QuickConnectState.Error("No suitable servers in $countryCode")
                null
            }
        } catch (e: Exception) {
            _state.value = QuickConnectState.Error(e.message ?: "Unknown error")
            null
        }
    }

    /**
     * Find best servers for a region
     */
    suspend fun findBestForRegion(region: ServerSelector.Region): List<ServerRecommendation> {
        _state.value = QuickConnectState.Searching
        
        return try {
            val servers = repository.fetchServers().getOrDefault(emptyList())
            val regionServers = serverSelector.filterByRegion(servers, region)
            
            if (regionServers.isEmpty()) {
                _state.value = QuickConnectState.Error("No servers in ${region.name}")
                return emptyList()
            }
            
            val topServers = serverSelector.selectTop(regionServers, count = 5)
            
            // Measure pings in parallel
            val recommendations = measurePingsParallel(topServers).map { (server, ping) ->
                ServerRecommendation(
                    server = server,
                    reason = "Top server in ${region.name}",
                    measuredPing = ping,
                    confidence = 0.8f
                )
            }
            
            _state.value = QuickConnectState.Found(recommendations)
            recommendations
        } catch (e: Exception) {
            _state.value = QuickConnectState.Error(e.message ?: "Unknown error")
            emptyList()
        }
    }

    /**
     * Get top servers with real-time ping measurements
     */
    suspend fun getTopServersWithPing(count: Int = 10): List<ServerRecommendation> {
        _state.value = QuickConnectState.Searching
        
        return try {
            val servers = repository.fetchServers().getOrDefault(emptyList())
            val topServers = serverSelector.selectTop(servers, count = count)
            
            val recommendations = measurePingsParallel(topServers).map { (server, ping) ->
                ServerRecommendation(
                    server = server,
                    reason = "Top ${server.countryShort} server",
                    measuredPing = ping,
                    confidence = 0.9f
                )
            }.sortedBy { it.measuredPing ?: Int.MAX_VALUE }
            
            _state.value = QuickConnectState.Found(recommendations)
            recommendations
        } catch (e: Exception) {
            _state.value = QuickConnectState.Error(e.message ?: "Unknown error")
            emptyList()
        }
    }

    /**
     * Get all available countries
     */
    suspend fun getAvailableCountries(): List<CountryInfo> {
        val servers = repository.fetchServers().getOrDefault(emptyList())
        
        return servers
            .groupBy { it.countryShort }
            .map { (code, serverList) ->
                CountryInfo(
                    code = code,
                    name = serverList.first().countryLong,
                    serverCount = serverList.size,
                    bestSpeed = serverList.maxOfOrNull { it.speed } ?: 0,
                    bestPing = serverList.minOfOrNull { it.ping } ?: 999
                )
            }
            .sortedBy { it.name }
    }

    /**
     * Measure ping to a single server
     */
    private suspend fun measurePing(server: VpnGateServer): Int? = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()
            val address = InetAddress.getByName(server.ip)
            val reachable = address.isReachable(PING_TIMEOUT_MS)
            
            if (reachable) {
                (System.currentTimeMillis() - startTime).toInt()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Ping failed for ${server.ip}")
            null
        }
    }

    /**
     * Measure pings to multiple servers in parallel
     */
    private suspend fun measurePingsParallel(
        servers: List<VpnGateServer>
    ): List<Pair<VpnGateServer, Int?>> = withContext(Dispatchers.IO) {
        servers.chunked(MAX_PARALLEL_PINGS).flatMap { chunk ->
            chunk.map { server ->
                async {
                    server to measurePing(server)
                }
            }.awaitAll()
        }
    }

    /**
     * Country information for UI
     */
    data class CountryInfo(
        val code: String,
        val name: String,
        val serverCount: Int,
        val bestSpeed: Long,
        val bestPing: Int
    ) {
        val formattedSpeed: String
            get() = when {
                bestSpeed >= 1_000_000_000 -> "${bestSpeed / 1_000_000_000.0} Gbps"
                bestSpeed >= 1_000_000 -> "${bestSpeed / 1_000_000.0} Mbps"
                else -> "${bestSpeed / 1_000.0} Kbps"
            }
    }

    /**
     * Get favorite servers
     */
    fun getFavorites(): List<VpnGateServer> = serverCache.getFavorites()

    /**
     * Add server to favorites
     */
    fun addFavorite(server: VpnGateServer): Boolean = serverCache.addFavorite(server)

    /**
     * Remove server from favorites
     */
    fun removeFavorite(server: VpnGateServer): Boolean = serverCache.removeFavorite(server)

    /**
     * Check if server is favorite
     */
    fun isFavorite(server: VpnGateServer): Boolean = serverCache.isFavorite(server)

    /**
     * Get recent servers
     */
    fun getRecentServers(): List<VpnGateServer> = serverCache.getRecentServers()
}
