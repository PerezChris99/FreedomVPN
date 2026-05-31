package com.freedomvpn.vpn.optimization

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Split tunneling manager
 * 
 * Allows selective routing of apps through or around the VPN:
 * - Exclude specific apps from VPN (e.g., banking apps)
 * - Include only specific apps (e.g., browser only)
 * - System app filtering
 * - Persistent preferences
 */
@Singleton
class SplitTunnelingManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "SplitTunneling"
        private const val PREFS_NAME = "split_tunneling_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_MODE = "mode"
        private const val KEY_EXCLUDED_APPS = "excluded_apps"
        private const val KEY_INCLUDED_APPS = "included_apps"
        
        // Default apps to exclude (sensitive apps that may not work well with VPN)
        private val DEFAULT_EXCLUDED = setOf(
            "com.android.vending",        // Play Store
            "com.google.android.gms",     // Google Play Services
        )
    }
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val packageManager = context.packageManager
    
    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()
    
    private val _mode = MutableStateFlow(SplitMode.EXCLUDE)
    val mode: StateFlow<SplitMode> = _mode.asStateFlow()
    
    private val _excludedApps = MutableStateFlow<Set<String>>(emptySet())
    val excludedApps: StateFlow<Set<String>> = _excludedApps.asStateFlow()
    
    private val _includedApps = MutableStateFlow<Set<String>>(emptySet())
    val includedApps: StateFlow<Set<String>> = _includedApps.asStateFlow()
    
    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()
    
    init {
        loadSettings()
    }
    
    /**
     * Load saved settings
     */
    private fun loadSettings() {
        _isEnabled.value = prefs.getBoolean(KEY_ENABLED, false)
        _mode.value = SplitMode.valueOf(prefs.getString(KEY_MODE, SplitMode.EXCLUDE.name)!!)
        _excludedApps.value = prefs.getStringSet(KEY_EXCLUDED_APPS, DEFAULT_EXCLUDED) ?: DEFAULT_EXCLUDED
        _includedApps.value = prefs.getStringSet(KEY_INCLUDED_APPS, emptySet()) ?: emptySet()
    }
    
    /**
     * Save current settings
     */
    private fun saveSettings() {
        prefs.edit().apply {
            putBoolean(KEY_ENABLED, _isEnabled.value)
            putString(KEY_MODE, _mode.value.name)
            putStringSet(KEY_EXCLUDED_APPS, _excludedApps.value)
            putStringSet(KEY_INCLUDED_APPS, _includedApps.value)
            apply()
        }
    }
    
    /**
     * Enable/disable split tunneling
     */
    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        saveSettings()
        Log.d(TAG, "Split tunneling ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Set split tunneling mode
     */
    fun setMode(mode: SplitMode) {
        _mode.value = mode
        saveSettings()
        Log.d(TAG, "Split tunneling mode: $mode")
    }
    
    /**
     * Add app to excluded list
     */
    fun excludeApp(packageName: String) {
        _excludedApps.value = _excludedApps.value + packageName
        saveSettings()
        Log.d(TAG, "Excluded app: $packageName")
    }
    
    /**
     * Remove app from excluded list
     */
    fun unexcludeApp(packageName: String) {
        _excludedApps.value = _excludedApps.value - packageName
        saveSettings()
        Log.d(TAG, "Unexcluded app: $packageName")
    }
    
    /**
     * Add app to included list
     */
    fun includeApp(packageName: String) {
        _includedApps.value = _includedApps.value + packageName
        saveSettings()
        Log.d(TAG, "Included app: $packageName")
    }
    
    /**
     * Remove app from included list
     */
    fun unincludeApp(packageName: String) {
        _includedApps.value = _includedApps.value - packageName
        saveSettings()
        Log.d(TAG, "Unincluded app: $packageName")
    }
    
    /**
     * Toggle app in current list (exclude or include based on mode)
     */
    fun toggleApp(packageName: String) {
        when (_mode.value) {
            SplitMode.EXCLUDE -> {
                if (packageName in _excludedApps.value) {
                    unexcludeApp(packageName)
                } else {
                    excludeApp(packageName)
                }
            }
            SplitMode.INCLUDE -> {
                if (packageName in _includedApps.value) {
                    unincludeApp(packageName)
                } else {
                    includeApp(packageName)
                }
            }
        }
    }
    
    /**
     * Check if app is selected (excluded in EXCLUDE mode, included in INCLUDE mode)
     */
    fun isAppSelected(packageName: String): Boolean {
        return when (_mode.value) {
            SplitMode.EXCLUDE -> packageName in _excludedApps.value
            SplitMode.INCLUDE -> packageName in _includedApps.value
        }
    }
    
    /**
     * Get list of apps that should bypass VPN
     */
    fun getDisallowedApps(): Set<String> {
        if (!_isEnabled.value) return emptySet()
        
        return when (_mode.value) {
            SplitMode.EXCLUDE -> _excludedApps.value
            SplitMode.INCLUDE -> {
                // In include mode, disallow all apps except included ones
                _installedApps.value
                    .map { it.packageName }
                    .filter { it !in _includedApps.value }
                    .toSet()
            }
        }
    }
    
    /**
     * Get list of apps that should use VPN
     */
    fun getAllowedApps(): Set<String> {
        if (!_isEnabled.value) return emptySet()
        
        return when (_mode.value) {
            SplitMode.EXCLUDE -> {
                // In exclude mode, allow all apps except excluded ones
                _installedApps.value
                    .map { it.packageName }
                    .filter { it !in _excludedApps.value }
                    .toSet()
            }
            SplitMode.INCLUDE -> _includedApps.value
        }
    }
    
    /**
     * Load list of installed apps
     */
    suspend fun loadInstalledApps(includeSystem: Boolean = false) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Loading installed apps...")
        
        val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { app ->
                // Filter out system apps unless requested
                if (!includeSystem) {
                    (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                } else true
            }
            .filter { app ->
                // Filter out our own app
                app.packageName != context.packageName
            }
            .map { app ->
                AppInfo(
                    packageName = app.packageName,
                    appName = packageManager.getApplicationLabel(app).toString(),
                    isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            .sortedBy { it.appName.lowercase() }
        
        _installedApps.value = apps
        Log.d(TAG, "Loaded ${apps.size} apps")
    }
    
    /**
     * Get app icon
     */
    fun getAppIcon(packageName: String): Drawable? {
        return try {
            packageManager.getApplicationIcon(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
    
    /**
     * Reset to defaults
     */
    fun resetToDefaults() {
        _isEnabled.value = false
        _mode.value = SplitMode.EXCLUDE
        _excludedApps.value = DEFAULT_EXCLUDED
        _includedApps.value = emptySet()
        saveSettings()
        Log.d(TAG, "Reset to defaults")
    }
}

/**
 * Split tunneling mode
 */
enum class SplitMode {
    EXCLUDE,  // All apps use VPN except excluded
    INCLUDE   // Only included apps use VPN
}

/**
 * App info for display
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean
)
