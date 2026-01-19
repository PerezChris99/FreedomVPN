package com.freedomvpn.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.freedomvpn.ui.theme.FreedomVPNTheme
import com.freedomvpn.vpn.VpnConnectionManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Main Activity for FreedomVPN
 * 
 * Handles:
 * - VPN permission requests
 * - Hosting the Compose UI
 * - Managing VPN service lifecycle
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var vpnConnectionManager: VpnConnectionManager

    // VPN permission launcher
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Permission granted, proceed with connection
            vpnConnectionManager.connect()
        } else {
            Toast.makeText(
                this,
                "VPN permission is required to connect",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            FreedomVPNTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        viewModel = hiltViewModel(),
                        onConnectClick = { server ->
                            requestVpnPermissionAndConnect(server)
                        },
                        onDisconnectClick = {
                            vpnConnectionManager.disconnect()
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vpnConnectionManager.bindService()
    }

    override fun onStop() {
        super.onStop()
        vpnConnectionManager.unbindService()
    }

    /**
     * Request VPN permission and connect
     * Android requires explicit user consent before establishing a VPN
     */
    private fun requestVpnPermissionAndConnect(server: com.freedomvpn.vpngate.VpnGateServer?) {
        val vpnIntent = VpnService.prepare(this)
        
        if (vpnIntent != null) {
            // Permission needed - launch system dialog
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            // Permission already granted
            vpnConnectionManager.connect(server)
        }
    }
}
