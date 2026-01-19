package com.freedomvpn.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.freedomvpn.vpn.FreedomVpnService

/**
 * Receiver for boot completed event
 * Can be used to auto-start VPN after device restart if configured
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            Log.d(TAG, "Boot completed received")
            
            // Check if auto-connect is enabled in preferences
            // If so, start the VPN service
            // val prefs = context.getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE)
            // if (prefs.getBoolean("auto_connect", false)) {
            //     val serviceIntent = Intent(context, FreedomVpnService::class.java).apply {
            //         action = FreedomVpnService.ACTION_CONNECT
            //     }
            //     context.startForegroundService(serviceIntent)
            // }
        }
    }
}
