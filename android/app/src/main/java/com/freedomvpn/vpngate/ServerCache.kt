package com.freedomvpn.vpngate

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Server List Cache Manager
 * 
 * Provides fast offline access to VPN Gate server list:
 * - Caches server list locally
 * - Expires cache after configurable duration
 * - Falls back to cached data if API fails
 * - Stores last successful servers for quick reconnect
 * 
 * For Uganda and censored regions:
 * - Works offline when internet is restricted
 * - Fast app startup without waiting for API
 * - Saves bandwidth on limited data plans
 */
class ServerCache(private val context: Context) {

    companion object {
        private const val TAG = "ServerCache"
        private const val PREFS_NAME = "vpn_server_cache"
        private const val KEY_SERVERS = "cached_servers"
        private const val KEY_CACHE_TIME = "cache_timestamp"
        private const val KEY_LAST_SERVER = "last_connected_server"
        private const val KEY_FAVORITE_SERVERS = "favorite_servers"
        private const val KEY_RECENT_SERVERS = "recent_servers"
        
        // Cache duration (5 minutes default, configurable)
        private const val DEFAULT_CACHE_DURATION_MS = 5 * 60 * 1000L
        
        // Maximum recent servers to keep
        private const val MAX_RECENT_SERVERS = 10
        
        // Maximum favorite servers
        private const val MAX_FAVORITE_SERVERS = 20
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private val gson = Gson()
    private val mutex = Mutex()
    private var cacheDurationMs = DEFAULT_CACHE_DURATION_MS

    /**
     * Set cache duration
     */
    fun setCacheDuration(durationMs: Long) {
        cacheDurationMs = durationMs
    }

    /**
     * Save server list to cache
     */
    suspend fun saveServers(servers: List<VpnGateServer>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val json = gson.toJson(servers)
                prefs.edit()
                    .putString(KEY_SERVERS, json)
                    .putLong(KEY_CACHE_TIME, System.currentTimeMillis())
                    .apply()
                Log.d(TAG, "Cached ${servers.size} servers")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cache servers", e)
            }
        }
    }

    /**
     * Get cached server list
     * Returns null if cache is expired or empty
     */
    suspend fun getServers(ignoreExpiry: Boolean = false): List<VpnGateServer>? = 
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    // Check cache expiry
                    if (!ignoreExpiry && isCacheExpired()) {
                        Log.d(TAG, "Cache expired")
                        return@withContext null
                    }
                    
                    val json = prefs.getString(KEY_SERVERS, null) ?: return@withContext null
                    val type = object : TypeToken<List<VpnGateServer>>() {}.type
                    val servers: List<VpnGateServer> = gson.fromJson(json, type)
                    
                    Log.d(TAG, "Loaded ${servers.size} servers from cache")
                    servers
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load cached servers", e)
                    null
                }
            }
        }

    /**
     * Check if cache is expired
     */
    fun isCacheExpired(): Boolean {
        val cacheTime = prefs.getLong(KEY_CACHE_TIME, 0)
        return System.currentTimeMillis() - cacheTime > cacheDurationMs
    }

    /**
     * Get cache age in milliseconds
     */
    fun getCacheAge(): Long {
        val cacheTime = prefs.getLong(KEY_CACHE_TIME, 0)
        return System.currentTimeMillis() - cacheTime
    }

    /**
     * Clear the cache
     */
    fun clearCache() {
        prefs.edit()
            .remove(KEY_SERVERS)
            .remove(KEY_CACHE_TIME)
            .apply()
        Log.d(TAG, "Cache cleared")
    }

    /**
     * Save last connected server (for quick reconnect)
     */
    fun saveLastServer(server: VpnGateServer) {
        try {
            val json = gson.toJson(server)
            prefs.edit()
                .putString(KEY_LAST_SERVER, json)
                .apply()
            
            // Also add to recent servers
            addRecentServer(server)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save last server", e)
        }
    }

    /**
     * Get last connected server
     */
    fun getLastServer(): VpnGateServer? {
        return try {
            val json = prefs.getString(KEY_LAST_SERVER, null) ?: return null
            gson.fromJson(json, VpnGateServer::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load last server", e)
            null
        }
    }

    /**
     * Add server to recent list
     */
    fun addRecentServer(server: VpnGateServer) {
        try {
            val recent = getRecentServers().toMutableList()
            
            // Remove if already exists (will re-add at top)
            recent.removeAll { it.ip == server.ip }
            
            // Add at the beginning
            recent.add(0, server)
            
            // Trim to max size
            while (recent.size > MAX_RECENT_SERVERS) {
                recent.removeAt(recent.size - 1)
            }
            
            val json = gson.toJson(recent)
            prefs.edit()
                .putString(KEY_RECENT_SERVERS, json)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add recent server", e)
        }
    }

    /**
     * Get recent servers
     */
    fun getRecentServers(): List<VpnGateServer> {
        return try {
            val json = prefs.getString(KEY_RECENT_SERVERS, null) ?: return emptyList()
            val type = object : TypeToken<List<VpnGateServer>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load recent servers", e)
            emptyList()
        }
    }

    /**
     * Add server to favorites
     */
    fun addFavorite(server: VpnGateServer): Boolean {
        return try {
            val favorites = getFavorites().toMutableList()
            
            // Check if already a favorite
            if (favorites.any { it.ip == server.ip }) {
                return false
            }
            
            // Check max limit
            if (favorites.size >= MAX_FAVORITE_SERVERS) {
                return false
            }
            
            favorites.add(server)
            
            val json = gson.toJson(favorites)
            prefs.edit()
                .putString(KEY_FAVORITE_SERVERS, json)
                .apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add favorite", e)
            false
        }
    }

    /**
     * Remove server from favorites
     */
    fun removeFavorite(server: VpnGateServer): Boolean {
        return try {
            val favorites = getFavorites().toMutableList()
            val removed = favorites.removeAll { it.ip == server.ip }
            
            if (removed) {
                val json = gson.toJson(favorites)
                prefs.edit()
                    .putString(KEY_FAVORITE_SERVERS, json)
                    .apply()
            }
            removed
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove favorite", e)
            false
        }
    }

    /**
     * Check if server is a favorite
     */
    fun isFavorite(server: VpnGateServer): Boolean {
        return getFavorites().any { it.ip == server.ip }
    }

    /**
     * Get favorite servers
     */
    fun getFavorites(): List<VpnGateServer> {
        return try {
            val json = prefs.getString(KEY_FAVORITE_SERVERS, null) ?: return emptyList()
            val type = object : TypeToken<List<VpnGateServer>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load favorites", e)
            emptyList()
        }
    }

    /**
     * Export cache to file (for backup)
     */
    suspend fun exportToFile(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val servers = getServers(ignoreExpiry = true) ?: return@withContext false
            val json = gson.toJson(servers)
            file.writeText(json)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export cache", e)
            false
        }
    }

    /**
     * Import cache from file
     */
    suspend fun importFromFile(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext false
            
            val json = file.readText()
            val type = object : TypeToken<List<VpnGateServer>>() {}.type
            val servers: List<VpnGateServer> = gson.fromJson(json, type)
            
            saveServers(servers)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import cache", e)
            false
        }
    }
}
