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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.freedomvpn.ui.theme.FreedomVPNTheme
import com.freedomvpn.vpn.VpnConnectionManager
import com.freedomvpn.vpn.FreedomVpnService
import com.freedomvpn.vpngate.VpnGateServer
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
                    val viewModel: MainViewModel = hiltViewModel()
                    val uiState by viewModel.uiState.collectAsState()
                    val connectionState by vpnConnectionManager.connectionState.collectAsState()
                    
                    // Connection stats
                    var stats by remember { mutableStateOf(ConnectionStats()) }
                    var currentIP by remember { mutableStateOf<String?>(null) }
                    var isLoadingIP by remember { mutableStateOf(true) }
                    
                    // Simulate IP detection
                    LaunchedEffect(connectionState) {
                        isLoadingIP = true
                        kotlinx.coroutines.delay(1500)
                        currentIP = if (connectionState == FreedomVpnService.ConnectionState.CONNECTED) {
                            "10.${(1..255).random()}.${(1..255).random()}.${(1..255).random()}"
                        } else {
                            "197.${(1..255).random()}.${(1..255).random()}.${(1..255).random()}"
                        }
                        isLoadingIP = false
                    }
                    
                    // Update stats while connected
                    LaunchedEffect(connectionState) {
                        if (connectionState == FreedomVpnService.ConnectionState.CONNECTED) {
                            var seconds = 0L
                            var bytes = 0L
                            while (true) {
                                kotlinx.coroutines.delay(1000)
                                seconds++
                                bytes += (50_000..500_000).random().toLong()
                                stats = ConnectionStats(
                                    bytesIn = bytes,
                                    bytesOut = bytes / 3,
                                    connectionDuration = seconds,
                                    currentSpeed = (100_000..2_000_000).random().toLong()
                                )
                            }
                        }
                    }
                    
                    BeautifulMainScreen(
                        connectionState = connectionState,
                        selectedServer = uiState.selectedServer,
                        servers = uiState.servers,
                        stats = stats,
                        currentIP = currentIP,
                        isLoadingIP = isLoadingIP,
                        onConnectClick = {
                            val server = uiState.selectedServer
                            if (server != null) {
                                requestVpnPermissionAndConnect(server)
                            } else {
                                // Auto-select best server if none selected
                                viewModel.autoSelectBestServer()
                                val autoServer = uiState.selectedServer
                                if (autoServer != null) {
                                    requestVpnPermissionAndConnect(autoServer)
                                } else {
                                    Toast.makeText(this@MainActivity, "Please select a server first", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onDisconnectClick = {
                            vpnConnectionManager.disconnect()
                        },
                        onServerSelect = { server ->
                            viewModel.selectServer(server)
                        },
                        onRefresh = {
                            viewModel.loadServers(forceRefresh = true)
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
    private fun requestVpnPermissionAndConnect(server: VpnGateServer?) {
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
