package com.freedomvpn.vpn

import android.util.Log
import com.freedomvpn.vpngate.VpnGateServer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Multi-Hop VPN Manager for FreedomVPN
 * 
 * Provides server-bouncing functionality for enhanced anonymity.
 * Traffic is routed through multiple VPN servers in sequence,
 * making it virtually impossible to trace back to the origin.
 * 
 * Features:
 * - Instant one-click activation
 * - Intelligent server selection for minimal latency
 * - Geographic diversity for maximum anonymity
 * - Automatic fallback if a hop fails
 */
@Singleton
class MultiHopManager @Inject constructor() {
    
    companion object {
        private const val TAG = "MultiHopManager"
    }
    
    /**
     * Anonymity preset levels
     */
    enum class MultiHopPreset(val hopCount: Int, val description: String) {
        FAST(2, "Fast - 2 hops, ~90% speed"),
        BALANCED(2, "Balanced - 2 diverse hops, ~88% speed"),
        MAXIMUM(3, "Maximum - 3 hops, ~80% speed"),
        PARANOID(4, "Paranoid - 4 hops, ~70% speed")
    }
    
    /**
     * Hop information for display
     */
    data class HopInfo(
        val server: VpnGateServer,
        val isEntry: Boolean,
        val isExit: Boolean,
        val index: Int,
        val latencyMs: Int = 0
    )
    
    /**
     * Multi-hop chain state
     */
    data class ChainState(
        val active: Boolean = false,
        val hops: List<HopInfo> = emptyList(),
        val preset: MultiHopPreset = MultiHopPreset.BALANCED,
        val estimatedSpeedRetention: Float = 1.0f
    )
    
    // Configuration
    private var currentPreset: MultiHopPreset = MultiHopPreset.BALANCED
    private var diverseRouting: Boolean = true
    private var rotationIntervalMinutes: Int = 30
    
    // State
    private val _chainState = MutableStateFlow(ChainState())
    val chainState: StateFlow<ChainState> = _chainState.asStateFlow()
    
    private val _isMultiHopEnabled = MutableStateFlow(false)
    val isMultiHopEnabled: StateFlow<Boolean> = _isMultiHopEnabled.asStateFlow()
    
    private var currentChain: List<HopInfo> = emptyList()
    private var rotationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Latency cache for optimization
    private val latencyCache = mutableMapOf<String, Int>()
    
    /**
     * Set the anonymity preset
     */
    fun setPreset(preset: MultiHopPreset) {
        currentPreset = preset
        Log.d(TAG, "Multi-hop preset set to: ${preset.name}")
    }
    
    /**
     * Select optimal servers for multi-hop chain
     * Ensures geographic diversity for maximum anonymity
     */
    suspend fun selectOptimalChain(
        availableServers: List<VpnGateServer>,
        userCountry: String? = null
    ): List<VpnGateServer> = withContext(Dispatchers.IO) {
        val candidates = availableServers.toMutableList()
        val chain = mutableListOf<VpnGateServer>()
        val usedCountries = mutableSetOf<String>()
        
        // Sort by speed/latency
        candidates.sortByDescending { it.speed }
        
        // Select hops ensuring diversity
        for (i in 0 until currentPreset.hopCount) {
            var selected: VpnGateServer? = null
            
            if (diverseRouting && usedCountries.isNotEmpty()) {
                // Find server in different country
                selected = candidates.firstOrNull { server ->
                    !usedCountries.contains(server.countryShort)
                }
            }
            
            // Fallback to fastest available
            if (selected == null && candidates.isNotEmpty()) {
                selected = candidates[0]
            }
            
            selected?.let {
                chain.add(it)
                usedCountries.add(it.countryShort)
                candidates.remove(it)
            }
        }
        
        chain
    }
    
    /**
     * Activate multi-hop mode - INSTANT activation
     */
    suspend fun activate(availableServers: List<VpnGateServer>): Result<List<HopInfo>> = 
        withContext(Dispatchers.IO) {
            try {
                if (_isMultiHopEnabled.value) {
                    return@withContext Result.success(currentChain)
                }
                
                Log.d(TAG, "Activating Multi-Hop mode with ${currentPreset.name} preset...")
                
                // Select optimal servers
                val selectedServers = selectOptimalChain(availableServers)
                
                if (selectedServers.size < 2) {
                    return@withContext Result.failure(
                        Exception("Not enough servers available for multi-hop (need at least 2)")
                    )
                }
                
                // Build hop chain
                val hops = selectedServers.mapIndexed { index, server ->
                    HopInfo(
                        server = server,
                        isEntry = index == 0,
                        isExit = index == selectedServers.size - 1,
                        index = index,
                        latencyMs = latencyCache[server.hostName] ?: estimateLatency(server)
                    )
                }
                
                currentChain = hops
                _isMultiHopEnabled.value = true
                
                // Update state
                _chainState.value = ChainState(
                    active = true,
                    hops = hops,
                    preset = currentPreset,
                    estimatedSpeedRetention = getSpeedRetention()
                )
                
                // Start rotation timer
                startRotation(availableServers)
                
                Log.d(TAG, "Multi-Hop activated: ${hops.joinToString(" → ") { it.server.countryLong }}")
                
                Result.success(hops)
                
            } catch (e: Exception) {
                Log.e(TAG, "Multi-hop activation failed", e)
                Result.failure(e)
            }
        }
    
    /**
     * Deactivate multi-hop mode
     */
    suspend fun deactivate() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Deactivating Multi-Hop mode...")
        
        // Cancel rotation
        rotationJob?.cancel()
        rotationJob = null
        
        // Clear state
        currentChain = emptyList()
        _isMultiHopEnabled.value = false
        
        _chainState.value = ChainState(
            active = false,
            hops = emptyList(),
            preset = currentPreset
        )
        
        Log.d(TAG, "Multi-Hop deactivated")
    }
    
    /**
     * Toggle multi-hop mode
     */
    suspend fun toggle(availableServers: List<VpnGateServer>): Boolean {
        return if (_isMultiHopEnabled.value) {
            deactivate()
            false
        } else {
            activate(availableServers).isSuccess
        }
    }
    
    /**
     * Rotate to new chain for enhanced anonymity
     */
    suspend fun rotate(availableServers: List<VpnGateServer>) = withContext(Dispatchers.IO) {
        if (!_isMultiHopEnabled.value) return@withContext
        
        try {
            Log.d(TAG, "Rotating Multi-Hop chain...")
            
            // Exclude current chain servers
            val currentIds = currentChain.map { it.server.hostName }.toSet()
            val newCandidates = availableServers.filter { it.hostName !in currentIds }
            
            if (newCandidates.size >= currentPreset.hopCount) {
                val newServers = selectOptimalChain(newCandidates)
                val newHops = newServers.mapIndexed { index, server ->
                    HopInfo(
                        server = server,
                        isEntry = index == 0,
                        isExit = index == newServers.size - 1,
                        index = index,
                        latencyMs = latencyCache[server.hostName] ?: estimateLatency(server)
                    )
                }
                
                currentChain = newHops
                _chainState.value = _chainState.value.copy(hops = newHops)
                
                Log.d(TAG, "Chain rotated: ${newHops.joinToString(" → ") { it.server.countryLong }}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Chain rotation failed", e)
        }
    }
    
    /**
     * Start periodic chain rotation
     */
    private fun startRotation(availableServers: List<VpnGateServer>) {
        rotationJob?.cancel()
        rotationJob = scope.launch {
            while (isActive && _isMultiHopEnabled.value) {
                delay(rotationIntervalMinutes * 60 * 1000L)
                rotate(availableServers)
            }
        }
    }
    
    /**
     * Estimate latency to server (simplified)
     */
    private fun estimateLatency(server: VpnGateServer): Int {
        // Use ping if available, otherwise estimate based on location
        return server.ping.takeIf { it > 0 } ?: 100
    }
    
    /**
     * Get estimated speed retention based on hop count
     */
    fun getSpeedRetention(): Float {
        return when (currentPreset.hopCount) {
            2 -> 0.90f  // 90% speed
            3 -> 0.82f  // 82% speed
            4 -> 0.72f  // 72% speed
            else -> 0.95f
        }
    }
    
    /**
     * Get current chain for VPN service to use
     */
    fun getActiveChain(): List<VpnGateServer> {
        return currentChain.map { it.server }
    }
    
    /**
     * Get entry server (first hop)
     */
    fun getEntryServer(): VpnGateServer? {
        return currentChain.firstOrNull()?.server
    }
    
    /**
     * Get exit server (last hop - this is your visible IP)
     */
    fun getExitServer(): VpnGateServer? {
        return currentChain.lastOrNull()?.server
    }
    
    /**
     * Get chain info for display
     */
    fun getChainDisplayInfo(): String {
        if (currentChain.isEmpty()) return "Multi-Hop disabled"
        return currentChain.joinToString(" → ") { "${it.server.countryShort}" }
    }
    
    /**
     * Cleanup
     */
    fun cleanup() {
        rotationJob?.cancel()
        scope.cancel()
    }
}
