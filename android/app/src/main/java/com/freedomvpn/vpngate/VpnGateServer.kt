package com.freedomvpn.vpngate

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Data class representing a VPN Gate server
 * 
 * VPN Gate provides free VPN servers from volunteers around the world.
 * The server list is available as a CSV file at:
 * https://www.vpngate.net/api/iphone/
 * 
 * CSV Columns:
 * #HostName,IP,Score,Ping,Speed,CountryLong,CountryShort,NumVpnSessions,
 * Uptime,TotalUsers,TotalTraffic,LogType,Operator,Message,OpenVPN_ConfigData_Base64
 */
@Parcelize
data class VpnGateServer(
    val hostName: String,
    val ip: String,
    val score: Long,
    val ping: Int,  // in milliseconds
    val speed: Long,  // in bps
    val countryLong: String,
    val countryShort: String,
    val numVpnSessions: Int,
    val uptime: Long,
    val totalUsers: Long,
    val totalTraffic: Long,
    val logType: String,
    val operator: String,
    val message: String,
    val openVpnConfigBase64: String,
    val port: Int = 443  // Default OpenVPN port
) : Parcelable {

    /**
     * Get speed in Mbps for display
     */
    val speedMbps: Double
        get() = speed / 1_000_000.0

    /**
     * Get formatted speed string
     */
    val formattedSpeed: String
        get() = when {
            speed >= 1_000_000_000 -> String.format("%.1f Gbps", speed / 1_000_000_000.0)
            speed >= 1_000_000 -> String.format("%.1f Mbps", speed / 1_000_000.0)
            speed >= 1_000 -> String.format("%.1f Kbps", speed / 1_000.0)
            else -> "$speed bps"
        }

    /**
     * Get uptime in hours
     */
    val uptimeHours: Long
        get() = uptime / 1000 / 60 / 60

    /**
     * Calculate a quality score based on ping, speed, and sessions
     * Higher is better
     */
    val qualityScore: Double
        get() {
            val pingScore = if (ping > 0) 100.0 / ping else 0.0
            val speedScore = speedMbps
            val loadScore = if (numVpnSessions > 0) 100.0 / numVpnSessions else 100.0
            return pingScore * 0.3 + speedScore * 0.5 + loadScore * 0.2
        }

    /**
     * Check if this server has a valid OpenVPN config
     */
    val hasOpenVpnConfig: Boolean
        get() = openVpnConfigBase64.isNotBlank()

    /**
     * Get country flag emoji based on country code
     */
    val countryFlag: String
        get() {
            if (countryShort.length != 2) return "🌐"
            val firstChar = Character.codePointAt(countryShort, 0) - 0x41 + 0x1F1E6
            val secondChar = Character.codePointAt(countryShort, 1) - 0x41 + 0x1F1E6
            return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
        }

    companion object {
        /**
         * Create a server from CSV line
         * Returns null if parsing fails
         */
        fun fromCsvLine(line: String): VpnGateServer? {
            return try {
                val parts = line.split(",")
                if (parts.size < 15) return null
                
                VpnGateServer(
                    hostName = parts[0],
                    ip = parts[1],
                    score = parts[2].toLongOrNull() ?: 0,
                    ping = parts[3].toIntOrNull() ?: 999,
                    speed = parts[4].toLongOrNull() ?: 0,
                    countryLong = parts[5],
                    countryShort = parts[6],
                    numVpnSessions = parts[7].toIntOrNull() ?: 0,
                    uptime = parts[8].toLongOrNull() ?: 0,
                    totalUsers = parts[9].toLongOrNull() ?: 0,
                    totalTraffic = parts[10].toLongOrNull() ?: 0,
                    logType = parts[11],
                    operator = parts[12],
                    message = parts[13],
                    openVpnConfigBase64 = if (parts.size > 14) parts[14] else ""
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Enum for filtering servers by country
 */
enum class ServerRegion(val displayName: String, val countryCodes: List<String>) {
    ALL("All Regions", emptyList()),
    ASIA("Asia", listOf("JP", "KR", "TW", "HK", "SG", "TH", "VN", "ID", "MY", "PH")),
    EUROPE("Europe", listOf("DE", "FR", "GB", "NL", "SE", "CH", "IT", "ES", "PL", "CZ")),
    NORTH_AMERICA("North America", listOf("US", "CA", "MX")),
    SOUTH_AMERICA("South America", listOf("BR", "AR", "CL", "CO")),
    OCEANIA("Oceania", listOf("AU", "NZ"))
}

/**
 * Sort options for server list
 */
enum class ServerSortOption(val displayName: String) {
    SPEED("Speed"),
    PING("Ping"),
    SCORE("Score"),
    SESSIONS("Load"),
    COUNTRY("Country")
}
