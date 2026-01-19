package com.freedomvpn.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Network Monitor and Auto-Reconnect Service
 * 
 * Monitors network connectivity and handles:
 * 1. Network state changes (WiFi, Mobile, etc.)
 * 2. Automatic VPN reconnection on network change
 * 3. Exponential backoff for failed connections
 * 4. Smart server switching on persistent failures
 * 
 * Critical for Uganda and censored regions where:
 * - Network connectivity can be unstable
 * - ISPs may drop VPN connections
 * - Mobile networks may switch frequently
 */
class NetworkMonitor(
    private val context: Context,
    private val onNetworkChanged: (NetworkState) -> Unit,
    private val onReconnectNeeded: () -> Unit
) {
    companion object {
        private const val TAG = "NetworkMonitor"
        
        // Reconnect settings
        private const val MIN_RECONNECT_DELAY_MS = 1000L
        private const val MAX_RECONNECT_DELAY_MS = 60000L
        private const val RECONNECT_BACKOFF_MULTIPLIER = 2.0
        private const val MAX_RECONNECT_ATTEMPTS = 10
    }

    /**
     * Network state
     */
    sealed class NetworkState {
        object Available : NetworkState()
        object Unavailable : NetworkState()
        data class Changed(val type: NetworkType) : NetworkState()
    }

    enum class NetworkType {
        WIFI,
        MOBILE,
        ETHERNET,
        VPN,
        UNKNOWN
    }

    // State flows
    private val _networkState = MutableStateFlow<NetworkState>(NetworkState.Unavailable)
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // Reconnect state
    private var reconnectAttempts = 0
    private var currentReconnectDelay = MIN_RECONNECT_DELAY_MS
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Connectivity manager
    private val connectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    // Network callback
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Network available: $network")
            val type = getNetworkType(network)
            _networkState.value = NetworkState.Available
            _isConnected.value = true
            onNetworkChanged(NetworkState.Changed(type))
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "Network lost: $network")
            _networkState.value = NetworkState.Unavailable
            _isConnected.value = false
            onNetworkChanged(NetworkState.Unavailable)
        }

        override fun onCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities
        ) {
            val type = getNetworkType(capabilities)
            Log.d(TAG, "Network capabilities changed: $type")
            onNetworkChanged(NetworkState.Changed(type))
        }
    }

    /**
     * Start monitoring network changes
     */
    fun startMonitoring() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
            Log.d(TAG, "Network monitoring started")

            // Check initial state
            checkCurrentNetwork()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    /**
     * Stop monitoring network changes
     */
    fun stopMonitoring() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            reconnectJob?.cancel()
            scope.cancel()
            Log.d(TAG, "Network monitoring stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping network monitor", e)
        }
    }

    /**
     * Check current network state
     */
    fun checkCurrentNetwork() {
        val network = connectivityManager.activeNetwork
        if (network != null) {
            val type = getNetworkType(network)
            _isConnected.value = true
            _networkState.value = NetworkState.Available
            onNetworkChanged(NetworkState.Changed(type))
        } else {
            _isConnected.value = false
            _networkState.value = NetworkState.Unavailable
        }
    }

    /**
     * Get network type from Network
     */
    private fun getNetworkType(network: Network): NetworkType {
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return getNetworkType(capabilities)
    }

    /**
     * Get network type from NetworkCapabilities
     */
    private fun getNetworkType(capabilities: NetworkCapabilities?): NetworkType {
        return when {
            capabilities == null -> NetworkType.UNKNOWN
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkType.VPN
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            else -> NetworkType.UNKNOWN
        }
    }

    /**
     * Request VPN reconnection with exponential backoff
     */
    fun requestReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "Max reconnect attempts reached")
            resetReconnectState()
            return
        }

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            try {
                reconnectAttempts++
                Log.d(TAG, "Scheduling reconnect attempt $reconnectAttempts in ${currentReconnectDelay}ms")

                delay(currentReconnectDelay)

                // Increase delay for next attempt (exponential backoff)
                currentReconnectDelay = (currentReconnectDelay * RECONNECT_BACKOFF_MULTIPLIER)
                    .toLong()
                    .coerceAtMost(MAX_RECONNECT_DELAY_MS)

                // Trigger reconnection
                onReconnectNeeded()

            } catch (e: CancellationException) {
                Log.d(TAG, "Reconnect cancelled")
            }
        }
    }

    /**
     * Reset reconnect state (call after successful connection)
     */
    fun resetReconnectState() {
        reconnectAttempts = 0
        currentReconnectDelay = MIN_RECONNECT_DELAY_MS
        reconnectJob?.cancel()
        reconnectJob = null
    }

    /**
     * Get current reconnect attempt
     */
    fun getReconnectAttempt(): Int = reconnectAttempts

    /**
     * Check if internet is currently available
     */
    fun isInternetAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
