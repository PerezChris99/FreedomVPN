package com.freedomvpn.vpngate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import kotlin.math.ln

/**
 * Smart Server Selection Engine
 * 
 * Ranks and selects optimal VPN servers based on multiple factors:
 * - Latency (ping) - Lower is better
 * - Speed - Higher is better
 * - Load (active sessions) - Lower is better
 * - Uptime - Higher is better
 * - Geographic distance - Closer is better
 * 
 * Optimized for Uganda and censored regions:
 * - Prefers servers with good African connectivity
 * - Avoids servers in countries with censorship agreements
 * - Favors servers with high uptime (reliability)
 */
class ServerSelector {

    companion object {
        // Weight factors for scoring (total = 1.0)
        private const val WEIGHT_PING = 0.30
        private const val WEIGHT_SPEED = 0.35
        private const val WEIGHT_LOAD = 0.15
        private const val WEIGHT_UPTIME = 0.10
        private const val WEIGHT_STABILITY = 0.10
        
        // Preferred countries for African users (good connectivity)
        private val PREFERRED_COUNTRIES_AFRICA = listOf(
            "NL", "DE", "GB", "FR", "SE", "CH", "BE",  // Europe (good peering)
            "SG", "JP", "HK", "KR",                     // Asia (major hubs)
            "US", "CA"                                   // Americas
        )
        
        // Countries to avoid (may cooperate with censorship)
        private val AVOID_COUNTRIES = listOf(
            "CN", "RU", "IR", "BY", "TR"
        )
        
        // Maximum acceptable ping for selection
        private const val MAX_ACCEPTABLE_PING = 500
        
        // Minimum acceptable speed (10 Mbps)
        private const val MIN_ACCEPTABLE_SPEED = 10_000_000L
    }

    /**
     * Server score breakdown for transparency
     */
    data class ServerScore(
        val server: VpnGateServer,
        val totalScore: Double,
        val pingScore: Double,
        val speedScore: Double,
        val loadScore: Double,
        val uptimeScore: Double,
        val stabilityScore: Double,
        val isPreferred: Boolean = false,
        val isAvoided: Boolean = false
    ) : Comparable<ServerScore> {
        override fun compareTo(other: ServerScore): Int {
            return other.totalScore.compareTo(this.totalScore)
        }
    }

    /**
     * Select the best server from a list
     */
    fun selectBest(
        servers: List<VpnGateServer>,
        preferredCountry: String? = null,
        minSpeedMbps: Double = 10.0,
        maxPing: Int = MAX_ACCEPTABLE_PING
    ): VpnGateServer? {
        val ranked = rankServers(servers, preferredCountry, minSpeedMbps, maxPing)
        return ranked.firstOrNull()?.server
    }

    /**
     * Select top N servers
     */
    fun selectTop(
        servers: List<VpnGateServer>,
        count: Int = 5,
        preferredCountry: String? = null,
        minSpeedMbps: Double = 10.0,
        maxPing: Int = MAX_ACCEPTABLE_PING
    ): List<VpnGateServer> {
        val ranked = rankServers(servers, preferredCountry, minSpeedMbps, maxPing)
        return ranked.take(count).map { it.server }
    }

    /**
     * Rank all servers with detailed scoring
     */
    fun rankServers(
        servers: List<VpnGateServer>,
        preferredCountry: String? = null,
        minSpeedMbps: Double = 0.0,
        maxPing: Int = MAX_ACCEPTABLE_PING
    ): List<ServerScore> {
        // Filter out invalid servers
        val validServers = servers.filter { server ->
            server.ping in 1..maxPing &&
            server.speed >= (minSpeedMbps * 1_000_000) &&
            server.ip.isNotEmpty() &&
            !AVOID_COUNTRIES.contains(server.countryShort.uppercase())
        }
        
        if (validServers.isEmpty()) return emptyList()
        
        // Calculate min/max for normalization
        val minPing = validServers.minOf { it.ping }.toDouble()
        val maxPingVal = validServers.maxOf { it.ping }.toDouble()
        val minSpeed = validServers.minOf { it.speed }.toDouble()
        val maxSpeed = validServers.maxOf { it.speed }.toDouble()
        val minLoad = validServers.minOf { it.numVpnSessions }.toDouble()
        val maxLoad = validServers.maxOf { it.numVpnSessions }.toDouble().coerceAtLeast(1.0)
        val maxUptime = validServers.maxOf { it.uptime }.toDouble().coerceAtLeast(1.0)
        
        // Score each server
        val scores = validServers.map { server ->
            // Normalize values to 0-1 range
            val pingNorm = if (maxPingVal > minPing) {
                1.0 - (server.ping - minPing) / (maxPingVal - minPing)
            } else 1.0
            
            val speedNorm = if (maxSpeed > minSpeed) {
                // Use logarithmic scale for speed (diminishing returns)
                val logSpeed = ln(server.speed.toDouble() + 1)
                val logMax = ln(maxSpeed + 1)
                val logMin = ln(minSpeed + 1)
                (logSpeed - logMin) / (logMax - logMin)
            } else 1.0
            
            val loadNorm = if (maxLoad > minLoad) {
                1.0 - (server.numVpnSessions - minLoad) / (maxLoad - minLoad)
            } else 1.0
            
            val uptimeNorm = server.uptime.toDouble() / maxUptime
            
            // Stability score based on total users (more users = more tested)
            val stabilityNorm = if (server.totalUsers > 0) {
                (ln(server.totalUsers.toDouble() + 1) / ln(10000.0)).coerceIn(0.0, 1.0)
            } else 0.5
            
            // Check if this is a preferred country
            val isPreferred = preferredCountry?.let { 
                server.countryShort.equals(it, ignoreCase = true)
            } ?: PREFERRED_COUNTRIES_AFRICA.contains(server.countryShort.uppercase())
            
            val isAvoided = AVOID_COUNTRIES.contains(server.countryShort.uppercase())
            
            // Calculate weighted scores
            val pingScore = pingNorm * 100
            val speedScore = speedNorm * 100
            val loadScore = loadNorm * 100
            val uptimeScore = uptimeNorm * 100
            val stabilityScore = stabilityNorm * 100
            
            var totalScore = (
                pingScore * WEIGHT_PING +
                speedScore * WEIGHT_SPEED +
                loadScore * WEIGHT_LOAD +
                uptimeScore * WEIGHT_UPTIME +
                stabilityScore * WEIGHT_STABILITY
            )
            
            // Boost preferred countries
            if (isPreferred) totalScore *= 1.2
            
            // Penalize avoided countries (shouldn't happen due to filter, but just in case)
            if (isAvoided) totalScore *= 0.5
            
            ServerScore(
                server = server,
                totalScore = totalScore,
                pingScore = pingScore,
                speedScore = speedScore,
                loadScore = loadScore,
                uptimeScore = uptimeScore,
                stabilityScore = stabilityScore,
                isPreferred = isPreferred,
                isAvoided = isAvoided
            )
        }
        
        return scores.sorted()
    }

    /**
     * Find servers in a specific region
     */
    fun filterByRegion(
        servers: List<VpnGateServer>,
        region: Region
    ): List<VpnGateServer> {
        return servers.filter { server ->
            region.countries.contains(server.countryShort.uppercase())
        }
    }

    /**
     * Ping a server to measure actual latency
     */
    suspend fun measurePing(server: VpnGateServer): Int = withContext(Dispatchers.IO) {
        try {
            val address = InetAddress.getByName(server.ip)
            val startTime = System.currentTimeMillis()
            val reachable = address.isReachable(3000)
            val endTime = System.currentTimeMillis()
            
            if (reachable) {
                (endTime - startTime).toInt()
            } else {
                server.ping // Fall back to reported ping
            }
        } catch (e: Exception) {
            server.ping // Fall back to reported ping
        }
    }

    /**
     * Regions for filtering
     */
    enum class Region(val countries: List<String>) {
        EUROPE(listOf("NL", "DE", "GB", "FR", "SE", "CH", "BE", "AT", "IT", "ES", "PL", "CZ", "NO", "DK", "FI")),
        ASIA(listOf("JP", "KR", "SG", "HK", "TW", "TH", "MY", "VN", "ID", "PH", "IN")),
        AMERICAS(listOf("US", "CA", "BR", "MX", "AR", "CL")),
        OCEANIA(listOf("AU", "NZ")),
        AFRICA(listOf("ZA", "EG", "KE", "NG")), // Limited VPN Gate servers
        MIDDLE_EAST(listOf("AE", "IL", "SA")) // Limited, use with caution
    }
}
