package com.freedomvpn.vpn

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.VpnService
import android.os.IBinder
import android.util.Log
import com.freedomvpn.vpngate.VpnGateServer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager class that handles VPN connection lifecycle
 * Provides a clean API for UI components to interact with the VPN service
 */
@Singleton
class VpnConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "VpnConnectionManager"
        const val VPN_PERMISSION_REQUEST_CODE = 1001
    }

    private var vpnService: FreedomVpnService? = null
    private var isBound = false

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectionState = MutableStateFlow(FreedomVpnService.ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<FreedomVpnService.ConnectionState> = _connectionState.asStateFlow()

    private val _selectedServer = MutableStateFlow<VpnGateServer?>(null)
    val selectedServer: StateFlow<VpnGateServer?> = _selectedServer.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as FreedomVpnService.LocalBinder
            vpnService = binder.getService()
            isBound = true
            Log.d(TAG, "VPN Service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            vpnService = null
            isBound = false
            Log.d(TAG, "VPN Service disconnected")
        }
    }

    /**
     * Bind to the VPN service
     * Call this in your Activity's onCreate or onStart
     */
    fun bindService() {
        val intent = Intent(context, FreedomVpnService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    /**
     * Unbind from the VPN service
     * Call this in your Activity's onStop or onDestroy
     */
    fun unbindService() {
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
        }
    }

    /**
     * Check if VPN permission is granted
     * Returns null if permission is already granted, otherwise returns an Intent
     * that should be started with startActivityForResult
     */
    fun prepareVpn(): Intent? {
        return VpnService.prepare(context)
    }

    /**
     * Connect to VPN
     * Make sure to call prepareVpn first and handle the permission request
     */
    fun connect(server: VpnGateServer? = null) {
        Log.d(TAG, "Connecting to VPN...")
        _selectedServer.value = server
        
        val intent = Intent(context, FreedomVpnService::class.java).apply {
            action = FreedomVpnService.ACTION_CONNECT
            server?.let {
                // Pass server data as needed
                putExtra(FreedomVpnService.EXTRA_SERVER, it.hostName)
            }
        }
        
        context.startForegroundService(intent)
        _connectionState.value = FreedomVpnService.ConnectionState.CONNECTING
    }

    /**
     * Disconnect from VPN
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting from VPN...")
        
        val intent = Intent(context, FreedomVpnService::class.java).apply {
            action = FreedomVpnService.ACTION_DISCONNECT
        }
        
        context.startService(intent)
        _connectionState.value = FreedomVpnService.ConnectionState.DISCONNECTING
    }

    /**
     * Toggle VPN connection
     */
    fun toggle(server: VpnGateServer? = null) {
        when (_connectionState.value) {
            FreedomVpnService.ConnectionState.CONNECTED -> disconnect()
            FreedomVpnService.ConnectionState.DISCONNECTED -> connect(server)
            else -> { /* Ignore during transition states */ }
        }
    }

    /**
     * Select a server for connection
     */
    fun selectServer(server: VpnGateServer) {
        _selectedServer.value = server
    }

    /**
     * Get current connection statistics
     */
    fun getConnectionStats(): FreedomVpnService.ConnectionStats? {
        return vpnService?.connectionStats?.value
    }
}
