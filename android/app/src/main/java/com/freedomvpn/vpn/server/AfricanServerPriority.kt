package com.freedomvpn.vpn.server

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * African Server Priority Manager
 * 
 * Prioritizes VPN servers geographically closer to Africa to reduce latency.
 * Most VPN services only have servers in Europe/US - this helps find the best ones.
 * 
 * Key features:
 * - Geographic distance calculation
 * - African region detection
 * - Latency-based server selection
 * - Fallback chains for reliability
 */
@Singleton
class AfricanServerPriority @Inject constructor() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _prioritizedServers = MutableStateFlow<List<PrioritizedServer>>(emptyList())
    val prioritizedServers: StateFlow<List<PrioritizedServer>> = _prioritizedServers

    data class PrioritizedServer(
        val server: VpnServerInfo,
        val distanceKm: Double,
        val estimatedLatencyMs: Int,
        val region: ServerRegion,
        val priorityScore: Float  // 0-100, higher is better
    )

    data class VpnServerInfo(
        val hostname: String,
        val ip: String,
        val country: String,
        val countryCode: String,
        val city: String? = null,
        val latitude: Double,
        val longitude: Double,
        val speedMbps: Int = 0,
        val ping: Int = 0
    )

    enum class ServerRegion(val description: String) {
        AFRICA("Africa"),
        MIDDLE_EAST("Middle East"),
        EUROPE_SOUTH("Southern Europe"),
        EUROPE_CENTRAL("Central Europe"),
        ASIA_WEST("Western Asia"),
        EUROPE_NORTH("Northern Europe"),
        ASIA_EAST("Eastern Asia"),
        AMERICAS("Americas"),
        OCEANIA("Oceania")
    }

    // African capital cities as reference points
    private val africanCities = mapOf(
        "UG" to Pair(0.3476, 32.5825),     // Kampala, Uganda
        "KE" to Pair(-1.2921, 36.8219),    // Nairobi, Kenya
        "TZ" to Pair(-6.7924, 39.2083),    // Dar es Salaam, Tanzania
        "RW" to Pair(-1.9403, 29.8739),    // Kigali, Rwanda
        "ET" to Pair(9.0320, 38.7469),     // Addis Ababa, Ethiopia
        "NG" to Pair(6.5244, 3.3792),      // Lagos, Nigeria
        "ZA" to Pair(-26.2041, 28.0473),   // Johannesburg, South Africa
        "EG" to Pair(30.0444, 31.2357),    // Cairo, Egypt
        "GH" to Pair(5.6037, -0.1870),     // Accra, Ghana
        "SN" to Pair(14.7167, -17.4677),   // Dakar, Senegal
        "MA" to Pair(33.9716, -6.8498),    // Rabat, Morocco
        "DZ" to Pair(36.7538, 3.0588),     // Algiers, Algeria
        "TN" to Pair(36.8065, 10.1815),    // Tunis, Tunisia
        "CD" to Pair(-4.4419, 15.2663),    // Kinshasa, DRC
        "CM" to Pair(3.8480, 11.5021),     // Yaoundé, Cameroon
        "CI" to Pair(5.3600, -4.0083),     // Abidjan, Ivory Coast
        "AO" to Pair(-8.8390, 13.2894),    // Luanda, Angola
        "MZ" to Pair(-25.9692, 32.5732),   // Maputo, Mozambique
    )

    // Servers that are good for African users (closer or better routes)
    private val preferredRegions = listOf(
        ServerRegion.AFRICA,
        ServerRegion.MIDDLE_EAST,
        ServerRegion.EUROPE_SOUTH,
        ServerRegion.ASIA_WEST
    )

    // Default reference point (Kampala, Uganda - as user is in Uganda)
    private var userLocation = africanCities["UG"]!!

    fun setUserCountry(countryCode: String) {
        africanCities[countryCode]?.let { userLocation = it }
    }

    /**
     * Prioritize servers based on distance from user's location
     */
    fun prioritizeServers(servers: List<VpnServerInfo>): List<PrioritizedServer> {
        val prioritized = servers.map { server ->
            val distance = calculateDistance(
                userLocation.first, userLocation.second,
                server.latitude, server.longitude
            )
            val region = determineRegion(server.latitude, server.longitude, server.countryCode)
            val estimatedLatency = estimateLatency(distance, region)
            val score = calculatePriorityScore(server, distance, region)

            PrioritizedServer(
                server = server,
                distanceKm = distance,
                estimatedLatencyMs = estimatedLatency,
                region = region,
                priorityScore = score
            )
        }.sortedByDescending { it.priorityScore }

        _prioritizedServers.value = prioritized
        return prioritized
    }

    /**
     * Get best servers for African users
     */
    fun getBestServersForAfrica(servers: List<VpnServerInfo>, count: Int = 10): List<PrioritizedServer> {
        return prioritizeServers(servers).take(count)
    }

    /**
     * Get servers in specific regions (for fallback)
     */
    fun getServersByRegion(servers: List<VpnServerInfo>, region: ServerRegion): List<PrioritizedServer> {
        return prioritizeServers(servers).filter { it.region == region }
    }

    /**
     * Build fallback chain - best servers from each region
     */
    fun buildFallbackChain(servers: List<VpnServerInfo>): List<PrioritizedServer> {
        val prioritized = prioritizeServers(servers)
        val chain = mutableListOf<PrioritizedServer>()

        // Add best from each preferred region
        for (region in preferredRegions) {
            prioritized.filter { it.region == region }
                .take(2)
                .forEach { chain.add(it) }
        }

        // Add remaining best servers
        prioritized.filter { it !in chain }
            .take(5)
            .forEach { chain.add(it) }

        return chain.sortedByDescending { it.priorityScore }
    }

    // ==================== KNOWN AFRICAN/NEARBY SERVERS ====================

    /**
     * List of known VPN server locations good for Africa
     * These can be used when VPN Gate doesn't have African servers
     */
    val knownGoodServers = listOf(
        // Africa (rare but ideal)
        VpnServerInfo("za.vpngate.net", "", "South Africa", "ZA", "Johannesburg", -26.2041, 28.0473),
        VpnServerInfo("eg.vpngate.net", "", "Egypt", "EG", "Cairo", 30.0444, 31.2357),
        
        // Middle East (relatively close)
        VpnServerInfo("ae.vpngate.net", "", "UAE", "AE", "Dubai", 25.2048, 55.2708),
        VpnServerInfo("il.vpngate.net", "", "Israel", "IL", "Tel Aviv", 32.0853, 34.7818),
        VpnServerInfo("tr.vpngate.net", "", "Turkey", "TR", "Istanbul", 41.0082, 28.9784),
        
        // Southern Europe (good routes to Africa)
        VpnServerInfo("it.vpngate.net", "", "Italy", "IT", "Rome", 41.9028, 12.4964),
        VpnServerInfo("es.vpngate.net", "", "Spain", "ES", "Madrid", 40.4168, -3.7038),
        VpnServerInfo("pt.vpngate.net", "", "Portugal", "PT", "Lisbon", 38.7223, -9.1393),
        VpnServerInfo("gr.vpngate.net", "", "Greece", "GR", "Athens", 37.9838, 23.7275),
        VpnServerInfo("cy.vpngate.net", "", "Cyprus", "CY", "Nicosia", 35.1856, 33.3823),
        
        // France (many Africa cables go through)
        VpnServerInfo("fr.vpngate.net", "", "France", "FR", "Paris", 48.8566, 2.3522),
        VpnServerInfo("fr2.vpngate.net", "", "France", "FR", "Marseille", 43.2965, 5.3698),
        
        // India (undersea cables to East Africa)
        VpnServerInfo("in.vpngate.net", "", "India", "IN", "Mumbai", 19.0760, 72.8777)
    )

    // ==================== CALCULATION UTILITIES ====================

    /**
     * Haversine formula for distance between two points on Earth
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0  // Earth's radius in km

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return R * c
    }

    /**
     * Determine server region based on coordinates
     */
    private fun determineRegion(lat: Double, lon: Double, countryCode: String): ServerRegion {
        // African countries
        if (countryCode in africanCities.keys || 
            (lat in -35.0..37.0 && lon in -18.0..52.0 && countryCode.length == 2)) {
            return ServerRegion.AFRICA
        }

        // Middle East
        if (lat in 12.0..42.0 && lon in 25.0..63.0) {
            return ServerRegion.MIDDLE_EAST
        }

        // Europe regions
        if (lat in 35.0..48.0 && lon in -10.0..35.0) {
            return ServerRegion.EUROPE_SOUTH
        }
        if (lat in 48.0..55.0 && lon in -10.0..25.0) {
            return ServerRegion.EUROPE_CENTRAL
        }
        if (lat > 55.0 && lon in -10.0..30.0) {
            return ServerRegion.EUROPE_NORTH
        }

        // Asia
        if (lat in 0.0..50.0 && lon in 63.0..100.0) {
            return ServerRegion.ASIA_WEST
        }
        if (lon > 100.0 && lat > -10.0) {
            return ServerRegion.ASIA_EAST
        }

        // Americas
        if (lon in -170.0..-30.0) {
            return ServerRegion.AMERICAS
        }

        // Oceania
        return ServerRegion.OCEANIA
    }

    /**
     * Estimate latency based on distance and region
     * Accounts for undersea cable routes
     */
    private fun estimateLatency(distanceKm: Double, region: ServerRegion): Int {
        // Base latency: ~0.05ms per km (speed of light in fiber is ~200,000 km/s)
        // But real latency is higher due to routing, processing, etc.
        val baseLatency = (distanceKm * 0.08).toInt()

        // Adjust for region (some have better infrastructure/routes)
        val regionMultiplier = when (region) {
            ServerRegion.AFRICA -> 1.0         // If in Africa, great
            ServerRegion.MIDDLE_EAST -> 1.1    // Good undersea cables
            ServerRegion.EUROPE_SOUTH -> 1.2   // Multiple Africa cables
            ServerRegion.ASIA_WEST -> 1.3      // India cables
            ServerRegion.EUROPE_CENTRAL -> 1.4
            ServerRegion.EUROPE_NORTH -> 1.5
            ServerRegion.ASIA_EAST -> 1.8      // Longer route
            ServerRegion.AMERICAS -> 2.0       // Very long route
            ServerRegion.OCEANIA -> 2.2        // Longest route
        }

        return (baseLatency * regionMultiplier).toInt().coerceIn(20, 500)
    }

    /**
     * Calculate priority score (0-100)
     */
    private fun calculatePriorityScore(
        server: VpnServerInfo,
        distanceKm: Double,
        region: ServerRegion
    ): Float {
        var score = 100f

        // Distance penalty (max 40 points)
        // 1000km = 5 points, 5000km = 25 points, 10000km = 40 points
        val distancePenalty = (distanceKm / 250).coerceAtMost(40.0)
        score -= distancePenalty.toFloat()

        // Region bonus
        val regionBonus = when (region) {
            ServerRegion.AFRICA -> 15f
            ServerRegion.MIDDLE_EAST -> 10f
            ServerRegion.EUROPE_SOUTH -> 8f
            ServerRegion.ASIA_WEST -> 5f
            else -> 0f
        }
        score += regionBonus

        // Speed bonus (if available)
        if (server.speedMbps > 0) {
            val speedBonus = (server.speedMbps / 10f).coerceAtMost(10f)
            score += speedBonus
        }

        // Ping bonus (if available)
        if (server.ping > 0) {
            val pingPenalty = (server.ping / 50f).coerceAtMost(15f)
            score -= pingPenalty
        }

        return score.coerceIn(0f, 100f)
    }

    // ==================== REAL-TIME LATENCY TEST ====================

    /**
     * Ping servers to get actual latency
     */
    suspend fun testServerLatency(server: VpnServerInfo): Int = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()
            val address = InetAddress.getByName(server.ip.ifEmpty { server.hostname })
            val reachable = address.isReachable(5000)
            val latency = (System.currentTimeMillis() - startTime).toInt()
            
            if (reachable) latency else 9999
        } catch (e: Exception) {
            9999  // Unreachable
        }
    }

    /**
     * Test multiple servers in parallel
     */
    suspend fun testServerLatencies(servers: List<VpnServerInfo>): Map<String, Int> = 
        coroutineScope {
            servers.map { server ->
                async {
                    server.hostname to testServerLatency(server)
                }
            }.awaitAll().toMap()
        }

    fun destroy() {
        scope.cancel()
    }
}
