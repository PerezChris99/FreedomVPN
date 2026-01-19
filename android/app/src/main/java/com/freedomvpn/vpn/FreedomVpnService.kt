package com.freedomvpn.vpn

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.freedomvpn.FreedomVpnApplication
import com.freedomvpn.R
import com.freedomvpn.ui.MainActivity
import com.freedomvpn.vpngate.VpnGateServer
import com.freedomvpn.vpn.wireguard.WireGuardManager
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * Core VPN Service that manages the VPN tunnel
 * 
 * This service:
 * 1. Creates a virtual network interface using VpnService.Builder
 * 2. Routes all traffic through the VPN tunnel
 * 3. Handles connection lifecycle (connect, disconnect, reconnect)
 * 4. Shows persistent notification while connected
 * 5. Supports both WireGuard and custom packet forwarding
 * 
 * For Uganda and other censored regions:
 * - Automatic reconnection on network changes
 * - Alternative port support for bypassing blocks
 * - Minimal traffic fingerprint
 */
@AndroidEntryPoint
class FreedomVpnService : VpnService() {

    @Inject
    lateinit var wireGuardManager: WireGuardManager

    companion object {
        private const val TAG = "FreedomVpnService"
        
        // Actions for controlling the service
        const val ACTION_CONNECT = "com.freedomvpn.CONNECT"
        const val ACTION_DISCONNECT = "com.freedomvpn.DISCONNECT"
        const val ACTION_CONNECT_WIREGUARD = "com.freedomvpn.CONNECT_WIREGUARD"
        const val EXTRA_SERVER = "extra_server"
        const val EXTRA_WG_PUBLIC_KEY = "extra_wg_public_key"
        const val EXTRA_WG_ENDPOINT = "extra_wg_endpoint"
        const val EXTRA_WG_PORT = "extra_wg_port"
        
        // VPN Configuration
        private const val VPN_MTU = 1280
        private const val VPN_ADDRESS = "10.0.0.2"
        private const val VPN_ROUTE = "0.0.0.0"
        private const val VPN_DNS = "8.8.8.8"
        private const val VPN_DNS_ALT = "8.8.4.4"
    }

    /**
     * Connection state enum
     */
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        ERROR
    }

    /**
     * Data class to hold connection statistics
     */
    data class ConnectionStats(
        val bytesIn: Long = 0,
        val bytesOut: Long = 0,
        val connectedAt: Long = 0,
        val serverName: String = ""
    )

    // Binder for local binding
    private val binder = LocalBinder()
    
    // Connection state
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _connectionStats = MutableStateFlow(ConnectionStats())
    val connectionStats: StateFlow<ConnectionStats> = _connectionStats.asStateFlow()
    
    // VPN interface
    private var vpnInterface: ParcelFileDescriptor? = null
    
    // Coroutine scope for VPN operations
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Current server
    private var currentServer: VpnGateServer? = null
    
    // WireGuard backend (if using WireGuard)
    private var wireGuardBackend: GoBackend? = null
    private var currentTunnel: FreedomTunnel? = null
    
    // VPN Tunnel for packet forwarding
    private var vpnTunnel: VpnTunnel? = null
    
    // Auto-reconnect settings
    private var autoReconnect = true
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val reconnectDelayMs = 3000L

    inner class LocalBinder : Binder() {
        fun getService(): FreedomVpnService = this@FreedomVpnService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VPN Service created")
        // Initialize WireGuard manager
        wireGuardManager.initialize(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_CONNECT -> {
                val serverJson = intent.getStringExtra(EXTRA_SERVER)
                // Parse server from JSON and connect
                startVpnConnection()
            }
            ACTION_CONNECT_WIREGUARD -> {
                val publicKey = intent.getStringExtra(EXTRA_WG_PUBLIC_KEY) ?: return START_STICKY
                val endpoint = intent.getStringExtra(EXTRA_WG_ENDPOINT) ?: return START_STICKY
                val port = intent.getIntExtra(EXTRA_WG_PORT, 51820)
                startWireGuardConnection(publicKey, endpoint, port)
            }
            ACTION_DISCONNECT -> {
                stopVpnConnection()
            }
        }
        
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopVpnConnection()
        Log.d(TAG, "VPN Service destroyed")
    }

    /**
     * Start a WireGuard VPN connection
     * Uses native WireGuard protocol for faster, more secure connections
     */
    fun startWireGuardConnection(
        serverPublicKey: String,
        serverEndpoint: String,
        serverPort: Int = 51820
    ) {
        if (_connectionState.value == ConnectionState.CONNECTING || 
            _connectionState.value == ConnectionState.CONNECTED) {
            Log.w(TAG, "Already connected or connecting")
            return
        }

        serviceScope.launch {
            try {
                _connectionState.value = ConnectionState.CONNECTING
                
                // Start foreground service with notification
                startForeground(
                    FreedomVpnApplication.VPN_NOTIFICATION_ID,
                    createNotification("Connecting via WireGuard...")
                )

                // Connect using WireGuard
                val success = wireGuardManager.connect(
                    serverPublicKey = serverPublicKey,
                    serverEndpoint = serverEndpoint,
                    serverPort = serverPort
                )

                if (success) {
                    _connectionState.value = ConnectionState.CONNECTED
                    _connectionStats.value = ConnectionStats(
                        connectedAt = System.currentTimeMillis(),
                        serverName = serverEndpoint
                    )
                    updateNotification("Connected via WireGuard")
                    Log.d(TAG, "WireGuard connected successfully")
                } else {
                    _connectionState.value = ConnectionState.ERROR
                    updateNotification("Connection failed")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "WireGuard connection failed", e)
                _connectionState.value = ConnectionState.ERROR
                updateNotification("Connection failed")
            }
        }
    }

    /**
     * Start the VPN connection
     * This sets up the VPN interface and begins routing traffic
     */
    fun startVpnConnection(server: VpnGateServer? = null) {
        if (_connectionState.value == ConnectionState.CONNECTING || 
            _connectionState.value == ConnectionState.CONNECTED) {
            Log.w(TAG, "Already connected or connecting")
            return
        }

        serviceScope.launch {
            try {
                _connectionState.value = ConnectionState.CONNECTING
                currentServer = server
                
                // Start foreground service with notification
                startForeground(
                    FreedomVpnApplication.VPN_NOTIFICATION_ID,
                    createNotification("Connecting...")
                )

                // Establish VPN interface
                establishVpnInterface()
                
                _connectionState.value = ConnectionState.CONNECTED
                _connectionStats.value = ConnectionStats(
                    connectedAt = System.currentTimeMillis(),
                    serverName = server?.hostName ?: "Unknown"
                )
                
                // Update notification
                updateNotification("Connected to ${server?.countryShort ?: "VPN"}")
                
                Log.d(TAG, "VPN Connected successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to establish VPN", e)
                _connectionState.value = ConnectionState.ERROR
                stopSelf()
            }
        }
    }

    /**
     * Stop the VPN connection
     */
    fun stopVpnConnection() {
        serviceScope.launch {
            try {
                _connectionState.value = ConnectionState.DISCONNECTING
                
                // Stop packet forwarding
                vpnTunnel?.stop()
                vpnTunnel = null
                
                // Close VPN interface
                vpnInterface?.close()
                vpnInterface = null
                
                // Stop WireGuard tunnel if active
                currentTunnel?.let { tunnel ->
                    try {
                        wireGuardBackend?.setState(tunnel, Tunnel.State.DOWN, null)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping WireGuard tunnel", e)
                    }
                }
                currentTunnel = null
                
                _connectionState.value = ConnectionState.DISCONNECTED
                _connectionStats.value = ConnectionStats()
                reconnectAttempts = 0
                
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                
                Log.d(TAG, "VPN Disconnected")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting VPN", e)
                _connectionState.value = ConnectionState.ERROR
            }
        }
    }

    /**
     * Establish the VPN interface using VpnService.Builder
     * This creates a virtual network interface and routes traffic through it
     */
    private suspend fun establishVpnInterface() = withContext(Dispatchers.IO) {
        val builder = Builder()
            .setSession("FreedomVPN")
            .setMtu(VPN_MTU)
            .addAddress(VPN_ADDRESS, 32)
            .addRoute(VPN_ROUTE, 0)  // Route all traffic
            .addDnsServer(VPN_DNS)
            .addDnsServer(VPN_DNS_ALT)
            .setBlocking(false) // Non-blocking for async packet handling
        
        // Allow apps to bypass VPN if needed
        // builder.addDisallowedApplication("com.example.bypass")
        
        // Establish the VPN interface
        vpnInterface = builder.establish() 
            ?: throw IllegalStateException("Failed to establish VPN interface")
        
        Log.d(TAG, "VPN Interface established: ${vpnInterface?.fd}")
        
        // Start packet forwarding with VpnTunnel
        startPacketForwarding()
    }
    
    /**
     * Start packet forwarding between VPN interface and server
     * This is the core of the VPN - all traffic flows through here
     */
    private fun startPacketForwarding() {
        val server = currentServer ?: run {
            Log.w(TAG, "No server configured, using default")
            return
        }
        
        val vpnFd = vpnInterface ?: run {
            Log.e(TAG, "VPN interface not established")
            return
        }
        
        // Create and start the tunnel
        vpnTunnel = VpnTunnel(
            vpnInterface = vpnFd,
            serverAddress = server.ip,
            serverPort = server.port,
            onStatsUpdate = { stats ->
                // Update connection statistics
                _connectionStats.value = _connectionStats.value.copy(
                    bytesIn = stats.bytesIn,
                    bytesOut = stats.bytesOut
                )
            },
            onError = { error ->
                Log.e(TAG, "Tunnel error", error)
                handleTunnelError(error)
            }
        )
        
        vpnTunnel?.start()
        Log.d(TAG, "Packet forwarding started")
    }
    
    /**
     * Handle tunnel errors with auto-reconnect
     */
    private fun handleTunnelError(error: Exception) {
        serviceScope.launch {
            if (autoReconnect && reconnectAttempts < maxReconnectAttempts) {
                reconnectAttempts++
                Log.d(TAG, "Attempting reconnect ($reconnectAttempts/$maxReconnectAttempts)")
                
                _connectionState.value = ConnectionState.CONNECTING
                updateNotification("Reconnecting... ($reconnectAttempts/$maxReconnectAttempts)")
                
                delay(reconnectDelayMs * reconnectAttempts) // Exponential backoff
                
                try {
                    // Stop current tunnel
                    vpnTunnel?.stop()
                    vpnInterface?.close()
                    
                    // Re-establish connection
                    establishVpnInterface()
                    
                    _connectionState.value = ConnectionState.CONNECTED
                    updateNotification("Connected to ${currentServer?.countryShort ?: "VPN"}")
                    reconnectAttempts = 0
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Reconnect failed", e)
                    if (reconnectAttempts >= maxReconnectAttempts) {
                        _connectionState.value = ConnectionState.ERROR
                        updateNotification("Connection failed")
                    }
                }
            } else {
                _connectionState.value = ConnectionState.ERROR
                updateNotification("Connection failed - tap to retry")
            }
        }
    }
    
    /**
     * Get current tunnel statistics
     */
    fun getTunnelStats(): VpnTunnel.VpnStats? {
        return vpnTunnel?.getStats()
    }
    
    /**
     * Enable or disable auto-reconnect
     */
    fun setAutoReconnect(enabled: Boolean) {
        autoReconnect = enabled
        Log.d(TAG, "Auto-reconnect ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Protect a socket from VPN routing
     * Call this for sockets that should bypass the VPN (e.g., tunnel socket itself)
     */
    fun protectSocket(socket: java.net.Socket): Boolean {
        return protect(socket)
    }
    
    fun protectSocket(socket: java.net.DatagramSocket): Boolean {
        return protect(socket)
    }
    
    fun protectSocket(fd: Int): Boolean {
        return protect(fd)
    }

    /**
     * Create notification for foreground service
     */
    private fun createNotification(status: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, FreedomVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, FreedomVpnApplication.VPN_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_vpn_key)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_close, getString(R.string.disconnect), disconnectIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /**
     * Update the notification with new status
     */
    private fun updateNotification(status: String) {
        val notification = createNotification(status)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(FreedomVpnApplication.VPN_NOTIFICATION_ID, notification)
    }

    /**
     * WireGuard Tunnel implementation
     */
    private inner class FreedomTunnel(private val name: String) : Tunnel {
        override fun getName(): String = name
        override fun onStateChange(newState: Tunnel.State) {
            Log.d(TAG, "Tunnel state changed to: $newState")
        }
    }
}
