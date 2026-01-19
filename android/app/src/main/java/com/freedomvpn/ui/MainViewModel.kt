package com.freedomvpn.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freedomvpn.vpn.FreedomVpnService
import com.freedomvpn.vpn.VpnConnectionManager
import com.freedomvpn.vpngate.ServerRegion
import com.freedomvpn.vpngate.ServerSortOption
import com.freedomvpn.vpngate.VpnGateRepository
import com.freedomvpn.vpngate.VpnGateServer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Main ViewModel for the VPN application
 * Manages UI state and coordinates between VPN service and repository
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val vpnGateRepository: VpnGateRepository,
    private val vpnConnectionManager: VpnConnectionManager
) : ViewModel() {

    // UI State
    data class UiState(
        val servers: List<VpnGateServer> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
        val selectedServer: VpnGateServer? = null,
        val connectionState: FreedomVpnService.ConnectionState = FreedomVpnService.ConnectionState.DISCONNECTED,
        val selectedRegion: ServerRegion = ServerRegion.ALL,
        val sortOption: ServerSortOption = ServerSortOption.SCORE,
        val connectedTime: Long = 0
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Connection state from manager
    val connectionState = vpnConnectionManager.connectionState

    init {
        loadServers()
        observeConnectionState()
    }

    /**
     * Observe connection state changes
     */
    private fun observeConnectionState() {
        viewModelScope.launch {
            vpnConnectionManager.connectionState.collect { state ->
                _uiState.value = _uiState.value.copy(connectionState = state)
            }
        }
    }

    /**
     * Load servers from VPN Gate
     */
    fun loadServers(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            val result = vpnGateRepository.fetchServers(forceRefresh)
            
            result.fold(
                onSuccess = { servers ->
                    _uiState.value = _uiState.value.copy(
                        servers = filterAndSortServers(servers),
                        isLoading = false
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        error = error.message ?: "Failed to load servers",
                        isLoading = false
                    )
                }
            )
        }
    }

    /**
     * Filter and sort servers based on current settings
     */
    private fun filterAndSortServers(servers: List<VpnGateServer>): List<VpnGateServer> {
        val filtered = if (_uiState.value.selectedRegion == ServerRegion.ALL) {
            servers
        } else {
            servers.filter { server ->
                _uiState.value.selectedRegion.countryCodes.contains(server.countryShort)
            }
        }
        
        return when (_uiState.value.sortOption) {
            ServerSortOption.SPEED -> filtered.sortedByDescending { it.speed }
            ServerSortOption.PING -> filtered.sortedBy { it.ping }
            ServerSortOption.SCORE -> filtered.sortedByDescending { it.qualityScore }
            ServerSortOption.SESSIONS -> filtered.sortedBy { it.numVpnSessions }
            ServerSortOption.COUNTRY -> filtered.sortedBy { it.countryLong }
        }
    }

    /**
     * Select a server
     */
    fun selectServer(server: VpnGateServer) {
        _uiState.value = _uiState.value.copy(selectedServer = server)
        vpnConnectionManager.selectServer(server)
    }

    /**
     * Auto-select best server
     */
    fun autoSelectBestServer() {
        viewModelScope.launch {
            val bestServer = vpnGateRepository.getBestServer()
            bestServer?.let { selectServer(it) }
        }
    }

    /**
     * Set region filter
     */
    fun setRegion(region: ServerRegion) {
        _uiState.value = _uiState.value.copy(selectedRegion = region)
        reapplyFilters()
    }

    /**
     * Set sort option
     */
    fun setSortOption(option: ServerSortOption) {
        _uiState.value = _uiState.value.copy(sortOption = option)
        reapplyFilters()
    }

    /**
     * Reapply filters and sorting
     */
    private fun reapplyFilters() {
        viewModelScope.launch {
            val allServers = vpnGateRepository.fetchServers().getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(
                servers = filterAndSortServers(allServers)
            )
        }
    }

    /**
     * Get best server for country
     */
    fun selectBestServerForCountry(countryCode: String) {
        viewModelScope.launch {
            val server = vpnGateRepository.getBestServerForCountry(countryCode)
            server?.let { selectServer(it) }
        }
    }

    /**
     * Clear error
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
