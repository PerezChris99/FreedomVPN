package com.freedomvpn.tile

import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.freedomvpn.R
import com.freedomvpn.vpn.FreedomVpnService

/**
 * Quick Settings Tile for VPN toggle
 * Allows users to connect/disconnect VPN from the notification shade
 */
class VpnTileService : TileService() {
    
    companion object {
        private const val TAG = "VpnTileService"
    }
    
    private var isConnected = false
    
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }
    
    override fun onClick() {
        super.onClick()
        
        Log.d(TAG, "Tile clicked, isConnected: $isConnected")
        
        if (isConnected) {
            // Disconnect
            val intent = Intent(this, FreedomVpnService::class.java).apply {
                action = FreedomVpnService.ACTION_DISCONNECT
            }
            startService(intent)
            isConnected = false
        } else {
            // Check VPN permission
            val prepareIntent = VpnService.prepare(this)
            if (prepareIntent != null) {
                // Need permission - can't request from tile, show notification or open app
                Log.w(TAG, "VPN permission required")
                // Could show a notification here directing user to open the app
                return
            }
            
            // Connect
            val intent = Intent(this, FreedomVpnService::class.java).apply {
                action = FreedomVpnService.ACTION_CONNECT
            }
            startForegroundService(intent)
            isConnected = true
        }
        
        updateTile()
    }
    
    private fun updateTile() {
        qsTile?.let { tile ->
            if (isConnected) {
                tile.state = Tile.STATE_ACTIVE
                tile.label = getString(R.string.vpn_connected)
                tile.icon = Icon.createWithResource(this, R.drawable.ic_vpn_key)
            } else {
                tile.state = Tile.STATE_INACTIVE
                tile.label = getString(R.string.vpn_disconnected)
                tile.icon = Icon.createWithResource(this, R.drawable.ic_vpn_key)
            }
            tile.updateTile()
        }
    }
}
