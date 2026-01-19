package com.freedomvpn.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.freedomvpn.data.model.VpnServer
import com.freedomvpn.ui.screens.ServerTab

/**
 * ViewModel for Server List screen
 */
@HiltViewModel
class ServerListViewModel @Inject constructor(
    // TODO: Inject ServerRepository, SmartConnectService
) : ViewModel() {
    
    private val _servers = MutableStateFlow<List<VpnServer>>(emptyList())
    val servers: StateFlow<List<VpnServer>> = _servers.asStateFlow()
    
    private val _favorites = MutableStateFlow<List<VpnServer>>(emptyList())
    val favorites: StateFlow<List<VpnServer>> = _favorites.asStateFlow()
    
    private val _recents = MutableStateFlow<List<VpnServer>>(emptyList())
    val recents: StateFlow<List<VpnServer>> = _recents.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _selectedTab = MutableStateFlow(ServerTab.ALL)
    val selectedTab: StateFlow<ServerTab> = _selectedTab.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _serverPings = MutableStateFlow<Map<String, Int>>(emptyMap())
    val serverPings: StateFlow<Map<String, Int>> = _serverPings.asStateFlow()
    
    private val favoriteIds = mutableSetOf<String>()
    
    init {
        loadServers()
    }
    
    /**
     * Load servers from repository
     */
    private fun loadServers() {
        viewModelScope.launch {
            _isLoading.value = true
            
            // TODO: Load from actual VPN Gate API
            // For now, use demo data
            val demoServers = listOf(
                VpnServer("jp-1", "public-vpn-123", "219.100.37.1", 51820, "JP", "Japan", 150_000_000, 45, 95.0, "VPN Gate"),
                VpnServer("jp-2", "public-vpn-456", "219.100.37.2", 51820, "JP", "Japan", 120_000_000, 52, 92.0, "VPN Gate"),
                VpnServer("kr-1", "public-vpn-789", "203.141.139.1", 51820, "KR", "South Korea", 180_000_000, 38, 96.0, "VPN Gate"),
                VpnServer("us-1", "public-vpn-012", "65.19.125.1", 51820, "US", "United States", 200_000_000, 120, 88.0, "VPN Gate"),
                VpnServer("us-2", "public-vpn-345", "65.19.125.2", 51820, "US", "United States", 180_000_000, 115, 86.0, "VPN Gate"),
                VpnServer("de-1", "public-vpn-678", "85.214.132.1", 51820, "DE", "Germany", 160_000_000, 95, 90.0, "VPN Gate"),
                VpnServer("gb-1", "public-vpn-901", "178.62.26.1", 51820, "GB", "United Kingdom", 140_000_000, 88, 89.0, "VPN Gate"),
                VpnServer("sg-1", "public-vpn-234", "139.180.146.1", 51820, "SG", "Singapore", 190_000_000, 65, 93.0, "VPN Gate"),
                VpnServer("nl-1", "public-vpn-567", "185.107.56.1", 51820, "NL", "Netherlands", 170_000_000, 90, 91.0, "VPN Gate"),
                VpnServer("ca-1", "public-vpn-890", "198.27.81.1", 51820, "CA", "Canada", 130_000_000, 110, 87.0, "VPN Gate"),
            )
            
            _servers.value = demoServers
            _serverPings.value = demoServers.associate { it.id to it.ping }
            _isLoading.value = false
        }
    }
    
    /**
     * Refresh server list
     */
    fun refreshServers() {
        loadServers()
    }
    
    /**
     * Set search query
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        applyFilter()
    }
    
    /**
     * Set selected tab
     */
    fun setSelectedTab(tab: ServerTab) {
        _selectedTab.value = tab
    }
    
    /**
     * Toggle favorite status for a server
     */
    fun toggleFavorite(server: VpnServer) {
        if (favoriteIds.contains(server.id)) {
            favoriteIds.remove(server.id)
            _favorites.value = _favorites.value.filter { it.id != server.id }
        } else {
            favoriteIds.add(server.id)
            _favorites.value = _favorites.value + server
        }
    }
    
    /**
     * Add server to recents
     */
    fun addToRecent(server: VpnServer) {
        val currentRecents = _recents.value.toMutableList()
        currentRecents.removeAll { it.id == server.id }
        currentRecents.add(0, server)
        // Keep only last 10 recents
        _recents.value = currentRecents.take(10)
    }
    
    /**
     * Apply search filter
     */
    private fun applyFilter() {
        // TODO: Implement filtering based on search query
    }
}
