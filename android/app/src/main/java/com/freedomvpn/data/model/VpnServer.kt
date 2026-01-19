package com.freedomvpn.data.model

/**
 * VPN Server data model
 */
data class VpnServer(
    val id: String,
    val hostname: String?,
    val ip: String,
    val port: Int,
    val countryShort: String,
    val countryLong: String,
    val speed: Long,           // bytes per second
    val ping: Int,             // milliseconds
    val score: Double,         // 0-100 quality score
    val operator: String,
    val protocol: String = "WireGuard",
    val uptime: Float = 100f,  // percentage
    val numSessions: Int = 0,
    val configData: String? = null,
    val publicKey: String? = null,
    val isFavorite: Boolean = false,
    val lastConnected: Long? = null
) {
    /**
     * Get speed in Mbps
     */
    val speedMbps: Float
        get() = speed / 1_000_000f
    
    /**
     * Get formatted speed string
     */
    val speedFormatted: String
        get() = when {
            speed >= 1_000_000_000 -> String.format("%.1f Gbps", speed / 1_000_000_000f)
            speed >= 1_000_000 -> String.format("%.0f Mbps", speed / 1_000_000f)
            else -> String.format("%.0f Kbps", speed / 1000f)
        }
    
    /**
     * Get ping quality indicator
     */
    val pingQuality: PingQuality
        get() = when {
            ping < 50 -> PingQuality.EXCELLENT
            ping < 100 -> PingQuality.GOOD
            ping < 200 -> PingQuality.FAIR
            else -> PingQuality.POOR
        }
    
    /**
     * Get display name for UI
     */
    val displayName: String
        get() = hostname ?: ip
}

enum class PingQuality {
    EXCELLENT, GOOD, FAIR, POOR
}
