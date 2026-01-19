package com.freedomvpn.vpngate

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.StringReader
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for fetching and parsing VPN Gate server list
 * 
 * VPN Gate is a volunteer-run project providing free VPN servers globally.
 * API Endpoint: https://www.vpngate.net/api/iphone/
 * 
 * The response is a CSV file with server information and OpenVPN configs.
 */
@Singleton
class VpnGateRepository @Inject constructor() {

    companion object {
        private const val TAG = "VpnGateRepository"
        private const val VPNGATE_API_URL = "https://www.vpngate.net/api/iphone/"
        private const val VPNGATE_MIRROR_URL = "http://www.vpngate.net/api/iphone/"
        
        // Cache duration - 5 minutes
        private const val CACHE_DURATION_MS = 5 * 60 * 1000L
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var cachedServers: List<VpnGateServer> = emptyList()
    private var lastFetchTime: Long = 0

    /**
     * Fetch server list from VPN Gate API
     * Returns cached list if still valid
     */
    suspend fun fetchServers(forceRefresh: Boolean = false): Result<List<VpnGateServer>> {
        return withContext(Dispatchers.IO) {
            try {
                // Return cached if still valid
                if (!forceRefresh && cachedServers.isNotEmpty() && 
                    System.currentTimeMillis() - lastFetchTime < CACHE_DURATION_MS) {
                    return@withContext Result.success(cachedServers)
                }

                Log.d(TAG, "Fetching VPN Gate server list...")
                
                val response = try {
                    fetchFromUrl(VPNGATE_API_URL)
                } catch (e: Exception) {
                    Log.w(TAG, "Primary URL failed, trying mirror...", e)
                    fetchFromUrl(VPNGATE_MIRROR_URL)
                }

                val servers = parseServerList(response)
                    .filter { it.hasOpenVpnConfig }
                    .filter { it.ping > 0 && it.ping < 1000 }
                    .sortedByDescending { it.qualityScore }

                cachedServers = servers
                lastFetchTime = System.currentTimeMillis()

                Log.d(TAG, "Fetched ${servers.size} servers")
                Result.success(servers)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch servers", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Fetch content from URL
     */
    private fun fetchFromUrl(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "FreedomVPN/1.0")
            .build()

        val response = httpClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}: ${response.message}")
        }

        return response.body?.string() ?: throw Exception("Empty response body")
    }

    /**
     * Parse CSV response into server list
     */
    private fun parseServerList(csvData: String): List<VpnGateServer> {
        val servers = mutableListOf<VpnGateServer>()
        val reader = BufferedReader(StringReader(csvData))
        
        var lineNumber = 0
        reader.forEachLine { line ->
            lineNumber++
            
            // Skip header lines (starts with * or #)
            if (line.startsWith("*") || line.startsWith("#") || line.isBlank()) {
                return@forEachLine
            }
            
            // Parse server
            VpnGateServer.fromCsvLine(line)?.let { server ->
                servers.add(server)
            }
        }
        
        return servers
    }

    /**
     * Get servers filtered by region
     */
    suspend fun getServersByRegion(region: ServerRegion): List<VpnGateServer> {
        val allServers = fetchServers().getOrDefault(emptyList())
        
        if (region == ServerRegion.ALL) {
            return allServers
        }
        
        return allServers.filter { server ->
            region.countryCodes.contains(server.countryShort)
        }
    }

    /**
     * Get servers sorted by specified option
     */
    suspend fun getServersSorted(sortOption: ServerSortOption): List<VpnGateServer> {
        val servers = fetchServers().getOrDefault(emptyList())
        
        return when (sortOption) {
            ServerSortOption.SPEED -> servers.sortedByDescending { it.speed }
            ServerSortOption.PING -> servers.sortedBy { it.ping }
            ServerSortOption.SCORE -> servers.sortedByDescending { it.score }
            ServerSortOption.SESSIONS -> servers.sortedBy { it.numVpnSessions }
            ServerSortOption.COUNTRY -> servers.sortedBy { it.countryLong }
        }
    }

    /**
     * Get the best server based on quality score
     */
    suspend fun getBestServer(): VpnGateServer? {
        return fetchServers().getOrNull()?.maxByOrNull { it.qualityScore }
    }

    /**
     * Get best server for a specific country
     */
    suspend fun getBestServerForCountry(countryCode: String): VpnGateServer? {
        return fetchServers().getOrNull()
            ?.filter { it.countryShort.equals(countryCode, ignoreCase = true) }
            ?.maxByOrNull { it.qualityScore }
    }

    /**
     * Ping a server to get real-time latency
     */
    suspend fun pingServer(server: VpnGateServer): Int {
        return withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                val address = InetAddress.getByName(server.ip)
                val reachable = address.isReachable(5000)
                
                if (reachable) {
                    (System.currentTimeMillis() - startTime).toInt()
                } else {
                    -1
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ping failed for ${server.ip}", e)
                -1
            }
        }
    }

    /**
     * Decode OpenVPN config from Base64
     */
    fun decodeOpenVpnConfig(server: VpnGateServer): String? {
        return try {
            val decoded = Base64.decode(server.openVpnConfigBase64, Base64.DEFAULT)
            String(decoded, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode OpenVPN config", e)
            null
        }
    }

    /**
     * Get unique countries from server list
     */
    suspend fun getAvailableCountries(): List<Pair<String, String>> {
        return fetchServers().getOrDefault(emptyList())
            .map { Pair(it.countryShort, it.countryLong) }
            .distinct()
            .sortedBy { it.second }
    }

    /**
     * Clear cached servers
     */
    fun clearCache() {
        cachedServers = emptyList()
        lastFetchTime = 0
    }
}
