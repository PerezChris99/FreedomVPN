package com.freedomvpn.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.freedomvpn.ui.components.ConnectionState
import com.freedomvpn.ui.components.ConnectionStats
import com.freedomvpn.data.model.VpnServer

/**
 * Main ViewModel for the connection screen
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    // TODO: Inject VPN service, server repository, etc.
) : ViewModel() {
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _currentServer = MutableStateFlow<VpnServer?>(null)
    val currentServer: StateFlow<VpnServer?> = _currentServer.asStateFlow()
    
    private val _connectionStats = MutableStateFlow(ConnectionStats())
    val connectionStats: StateFlow<ConnectionStats> = _connectionStats.asStateFlow()
    
    private val _speedHistory = MutableStateFlow<List<Float>>(emptyList())
    val speedHistory: StateFlow<List<Float>> = _speedHistory.asStateFlow()
    
    private var statsJob: kotlinx.coroutines.Job? = null
    
    /**
     * Toggle connection state
     */
    fun toggleConnection() {
        viewModelScope.launch {
            when (_connectionState.value) {
                ConnectionState.DISCONNECTED -> connect()
                ConnectionState.CONNECTED -> disconnect()
                ConnectionState.CONNECTING, ConnectionState.DISCONNECTING -> {
                    // Already in progress, ignore
                }
            }
        }
    }
    
    /**
     * Connect to VPN
     */
    private suspend fun connect() {
        _connectionState.value = ConnectionState.CONNECTING
        
        // If no server selected, use quick connect
        if (_currentServer.value == null) {
            quickConnect()
        }
        
        // TODO: Actual VPN connection logic
        // For now, simulate connection delay
        delay(2000)
        
        _connectionState.value = ConnectionState.CONNECTED
        startStatsUpdates()
    }
    
    /**
     * Disconnect from VPN
     */
    private suspend fun disconnect() {
        _connectionState.value = ConnectionState.DISCONNECTING
        
        stopStatsUpdates()
        
        // TODO: Actual VPN disconnection logic
        delay(500)
        
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectionStats.value = ConnectionStats()
        _speedHistory.value = emptyList()
    }
    
    /**
     * Quick connect to best available server
     */
    fun quickConnect() {
        viewModelScope.launch {
            // TODO: Use SmartConnectService to find best server
            // For now, set a placeholder server
            _currentServer.value = VpnServer(
                id = "demo-1",
                hostname = "vpn123.freedom.net",
                ip = "10.20.30.40",
                port = 51820,
                countryShort = "JP",
                countryLong = "Japan",
                speed = 150_000_000, // 150 Mbps
                ping = 45,
                score = 95.0,
                operator = "VPN Gate"
            )
            
            if (_connectionState.value == ConnectionState.DISCONNECTED) {
                toggleConnection()
            }
        }
    }
    
    /**
     * Set selected server
     */
    fun setServer(server: VpnServer) {
        _currentServer.value = server
    }
    
    /**
     * Start updating connection stats
     */
    private fun startStatsUpdates() {
        statsJob = viewModelScope.launch {
            var seconds = 0L
            var totalDownload = 0L
            var totalUpload = 0L
            val history = mutableListOf<Float>()
            
            while (true) {
                delay(1000)
                seconds++
                
                // Simulate varying speeds
                val downloadSpeed = (5f + Math.random() * 45f).toFloat()
                val uploadSpeed = (1f + Math.random() * 15f).toFloat()
                
                totalDownload += (downloadSpeed * 125_000).toLong() // bytes per second
                totalUpload += (uploadSpeed * 125_000).toLong()
                
                // Update history (keep last 60 samples)
                history.add(downloadSpeed)
                if (history.size > 60) history.removeAt(0)
                
                _connectionStats.value = ConnectionStats(
                    downloadSpeed = downloadSpeed,
                    uploadSpeed = uploadSpeed,
                    totalDownload = totalDownload,
                    totalUpload = totalUpload,
                    connectedTime = seconds,
                    ping = _currentServer.value?.ping ?: 0
                )
                
                _speedHistory.value = history.toList()
            }
        }
    }
    
    /**
     * Stop stats updates
     */
    private fun stopStatsUpdates() {
        statsJob?.cancel()
        statsJob = null
    }
    
    override fun onCleared() {
        super.onCleared()
        stopStatsUpdates()
    }
}
