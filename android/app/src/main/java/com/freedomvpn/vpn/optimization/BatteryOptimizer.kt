package com.freedomvpn.vpn.optimization

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Battery-aware VPN optimization
 * 
 * Adjusts VPN behavior based on:
 * - Battery level and charging state
 * - Network type (WiFi vs cellular)
 * - Power saving mode
 * - Doze mode
 */
@Singleton
class BatteryOptimizer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "BatteryOptimizer"
        
        // Battery thresholds
        private const val LOW_BATTERY_THRESHOLD = 20
        private const val CRITICAL_BATTERY_THRESHOLD = 10
        
        // Keepalive intervals (seconds)
        private const val NORMAL_KEEPALIVE = 25
        private const val LOW_BATTERY_KEEPALIVE = 60
        private const val CRITICAL_KEEPALIVE = 120
        
        // Stats update intervals (ms)
        private const val NORMAL_STATS_INTERVAL = 1000L
        private const val LOW_BATTERY_STATS_INTERVAL = 5000L
        private const val CRITICAL_STATS_INTERVAL = 10000L
    }
    
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val _powerMode = MutableStateFlow(PowerMode.NORMAL)
    val powerMode: StateFlow<PowerMode> = _powerMode.asStateFlow()
    
    private val _batteryStatus = MutableStateFlow(BatteryStatus())
    val batteryStatus: StateFlow<BatteryStatus> = _batteryStatus.asStateFlow()
    
    private var monitoringJob: Job? = null
    
    /**
     * Start battery monitoring
     */
    fun startMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = scope.launch {
            while (isActive) {
                updateBatteryStatus()
                updatePowerMode()
                delay(30000) // Check every 30 seconds
            }
        }
        Log.d(TAG, "Battery monitoring started")
    }
    
    /**
     * Stop battery monitoring
     */
    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }
    
    /**
     * Update battery status
     */
    private fun updateBatteryStatus() {
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = batteryManager.isCharging
        val isPowerSaveMode = powerManager.isPowerSaveMode
        
        _batteryStatus.value = BatteryStatus(
            level = level,
            isCharging = isCharging,
            isPowerSaveMode = isPowerSaveMode,
            isInteractive = powerManager.isInteractive
        )
    }
    
    /**
     * Update power mode based on conditions
     */
    private fun updatePowerMode() {
        val status = _batteryStatus.value
        
        val newMode = when {
            status.isCharging -> PowerMode.PERFORMANCE
            status.isPowerSaveMode -> PowerMode.ULTRA_SAVER
            status.level <= CRITICAL_BATTERY_THRESHOLD -> PowerMode.ULTRA_SAVER
            status.level <= LOW_BATTERY_THRESHOLD -> PowerMode.BATTERY_SAVER
            !status.isInteractive -> PowerMode.BACKGROUND
            else -> PowerMode.NORMAL
        }
        
        if (newMode != _powerMode.value) {
            Log.d(TAG, "Power mode changed: ${_powerMode.value} -> $newMode")
            _powerMode.value = newMode
        }
    }
    
    /**
     * Get optimal keepalive interval based on current power mode
     */
    fun getKeepaliveInterval(): Int {
        return when (_powerMode.value) {
            PowerMode.PERFORMANCE -> NORMAL_KEEPALIVE
            PowerMode.NORMAL -> NORMAL_KEEPALIVE
            PowerMode.BACKGROUND -> LOW_BATTERY_KEEPALIVE
            PowerMode.BATTERY_SAVER -> LOW_BATTERY_KEEPALIVE
            PowerMode.ULTRA_SAVER -> CRITICAL_KEEPALIVE
        }
    }
    
    /**
     * Get stats update interval based on current power mode
     */
    fun getStatsUpdateInterval(): Long {
        return when (_powerMode.value) {
            PowerMode.PERFORMANCE -> NORMAL_STATS_INTERVAL
            PowerMode.NORMAL -> NORMAL_STATS_INTERVAL
            PowerMode.BACKGROUND -> LOW_BATTERY_STATS_INTERVAL
            PowerMode.BATTERY_SAVER -> LOW_BATTERY_STATS_INTERVAL
            PowerMode.ULTRA_SAVER -> CRITICAL_STATS_INTERVAL
        }
    }
    
    /**
     * Should reduce network activity (for battery saving)
     */
    fun shouldReduceNetworkActivity(): Boolean {
        return _powerMode.value in listOf(
            PowerMode.BATTERY_SAVER,
            PowerMode.ULTRA_SAVER
        )
    }
    
    /**
     * Should use WiFi only (for battery saving on low battery)
     */
    fun shouldPreferWifi(): Boolean {
        val status = _batteryStatus.value
        return !status.isCharging && status.level <= LOW_BATTERY_THRESHOLD
    }
    
    /**
     * Check if on WiFi
     */
    fun isOnWifi(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
    
    /**
     * Check if on cellular
     */
    fun isOnCellular(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }
    
    /**
     * Get recommended VPN settings for current conditions
     */
    fun getRecommendedSettings(): VpnPowerSettings {
        val mode = _powerMode.value
        val status = _batteryStatus.value
        
        return VpnPowerSettings(
            keepaliveInterval = getKeepaliveInterval(),
            statsUpdateInterval = getStatsUpdateInterval(),
            enableSpeedGraph = mode != PowerMode.ULTRA_SAVER,
            enableAnimations = mode in listOf(PowerMode.PERFORMANCE, PowerMode.NORMAL),
            reducedLogging = mode in listOf(PowerMode.BATTERY_SAVER, PowerMode.ULTRA_SAVER),
            backgroundReconnect = !status.isPowerSaveMode,
            aggressiveReconnect = mode == PowerMode.PERFORMANCE
        )
    }
    
    fun cleanup() {
        stopMonitoring()
        scope.cancel()
    }
}

/**
 * Power modes
 */
enum class PowerMode {
    PERFORMANCE,    // Charging, optimize for speed
    NORMAL,         // Regular usage
    BACKGROUND,     // Screen off
    BATTERY_SAVER,  // Low battery
    ULTRA_SAVER     // Critical battery or power save mode
}

/**
 * Battery status data
 */
data class BatteryStatus(
    val level: Int = 100,
    val isCharging: Boolean = false,
    val isPowerSaveMode: Boolean = false,
    val isInteractive: Boolean = true
)

/**
 * Recommended VPN settings based on power conditions
 */
data class VpnPowerSettings(
    val keepaliveInterval: Int,
    val statsUpdateInterval: Long,
    val enableSpeedGraph: Boolean,
    val enableAnimations: Boolean,
    val reducedLogging: Boolean,
    val backgroundReconnect: Boolean,
    val aggressiveReconnect: Boolean
)
