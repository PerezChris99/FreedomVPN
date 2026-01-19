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
 */
@AndroidEntryPoint
class FreedomVpnService : VpnService() {

    companion object {
        private const val TAG = "FreedomVpnService"
        
        // Actions for controlling the service
        const val ACTION_CONNECT = "com.freedomvpn.CONNECT"
        const val ACTION_DISCONNECT = "com.freedomvpn.DISCONNECT"
        const val EXTRA_SERVER = "extra_server"
        
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

    inner class LocalBinder : Binder() {
        fun getService(): FreedomVpnService = this@FreedomVpnService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VPN Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_CONNECT -> {
                val serverJson = intent.getStringExtra(EXTRA_SERVER)
                // Parse server from JSON and connect
                startVpnConnection()
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
            .setBlocking(true)
        
        // Allow apps to bypass VPN if needed
        // builder.addDisallowedApplication("com.example.bypass")
        
        // Establish the VPN interface
        vpnInterface = builder.establish() 
            ?: throw IllegalStateException("Failed to establish VPN interface")
        
        Log.d(TAG, "VPN Interface established: ${vpnInterface?.fd}")
        
        // At this point, you would typically:
        // 1. Start WireGuard tunnel using the established interface
        // 2. Or implement custom protocol handling
        // 3. Forward packets between the VPN interface and the server
        
        // For WireGuard integration:
        // wireGuardBackend = GoBackend(this)
        // val config = createWireGuardConfig(currentServer)
        // currentTunnel = FreedomTunnel("freedom")
        // wireGuardBackend?.setState(currentTunnel, Tunnel.State.UP, config)
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
