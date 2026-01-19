package com.freedomvpn.stealth

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stealth & Panic Features Manager
 * 
 * Protects users in dangerous situations:
 * - Panic button: Instantly disconnect and hide evidence
 * - App disguise: Make app look like calculator/notes
 * - Notification hiding: Silent/hidden notifications
 * - Quick exit: Volume button trigger
 * - History clearing: Remove usage traces
 * 
 * Critical for journalists, activists, and users in
 * countries where VPN use may be criminalized.
 */
@Singleton
class StealthManager @Inject constructor(
    private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "stealth_prefs", Context.MODE_PRIVATE
    )

    private val _stealthModeEnabled = MutableStateFlow(false)
    val stealthModeEnabled: StateFlow<Boolean> = _stealthModeEnabled

    private val _disguiseMode = MutableStateFlow(DisguiseMode.NONE)
    val disguiseMode: StateFlow<DisguiseMode> = _disguiseMode

    private val _panicTriggered = MutableStateFlow(false)
    val panicTriggered: StateFlow<Boolean> = _panicTriggered

    // Callback for VPN disconnect (set by VPN service)
    var onPanicDisconnect: (() -> Unit)? = null
    
    // Callback for clearing logs (set by SecureLogger)
    var onClearLogs: (() -> Unit)? = null

    enum class DisguiseMode(
        val displayName: String,
        val aliasActivityName: String,
        val iconDescription: String
    ) {
        NONE("FreedomVPN", ".MainActivity", "VPN App"),
        CALCULATOR("Calculator Pro", ".CalculatorActivity", "Calculator"),
        NOTES("Quick Notes", ".NotesActivity", "Notes App"),
        WEATHER("Weather", ".WeatherActivity", "Weather App"),
        FLASHLIGHT("Flashlight", ".FlashlightActivity", "Flashlight")
    }

    // Alias activities for app disguise
    private val aliasComponents = mapOf(
        DisguiseMode.NONE to ComponentName(context.packageName, "${context.packageName}.MainActivity"),
        DisguiseMode.CALCULATOR to ComponentName(context.packageName, "${context.packageName}.CalculatorAlias"),
        DisguiseMode.NOTES to ComponentName(context.packageName, "${context.packageName}.NotesAlias"),
        DisguiseMode.WEATHER to ComponentName(context.packageName, "${context.packageName}.WeatherAlias"),
        DisguiseMode.FLASHLIGHT to ComponentName(context.packageName, "${context.packageName}.FlashlightAlias")
    )

    // ==================== PANIC BUTTON ====================

    /**
     * PANIC! Immediately:
     * 1. Disconnect VPN
     * 2. Clear notifications
     * 3. Clear recent app thumbnail
     * 4. Clear logs
     * 5. Optionally close app
     */
    fun triggerPanic(closeApp: Boolean = true) {
        scope.launch {
            _panicTriggered.value = true
            
            // Haptic feedback - double vibration
            triggerPanicVibration()
            
            // 1. Disconnect VPN immediately
            onPanicDisconnect?.invoke()
            
            // 2. Clear all notifications
            clearNotifications()
            
            // 3. Clear recent apps thumbnail (show blank)
            // This is handled by FLAG_SECURE in activities
            
            // 4. Clear logs
            onClearLogs?.invoke()
            clearLocalData()
            
            // 5. Optionally close/hide app
            if (closeApp) {
                delay(100)  // Brief delay for cleanup
                minimizeApp()
            }
            
            _panicTriggered.value = false
        }
    }

    /**
     * Quick panic - triggered by volume buttons or shake
     */
    fun quickPanic() {
        triggerPanic(closeApp = true)
    }

    private fun triggerPanicVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 100, 100), -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 100, 100), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 100, 100, 100), -1)
                }
            }
        } catch (e: Exception) {
            // Vibration not available
        }
    }

    private fun clearNotifications() {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancelAll()
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun clearLocalData() {
        // Clear shared preferences (except essential settings)
        val essentialKeys = setOf("selected_language", "disguise_mode")
        val allPrefs = prefs.all
        val editor = prefs.edit()
        
        allPrefs.keys.forEach { key ->
            if (key !in essentialKeys) {
                editor.remove(key)
            }
        }
        editor.apply()
        
        // Clear app cache
        try {
            context.cacheDir.deleteRecursively()
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun minimizeApp() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    // ==================== APP DISGUISE ====================

    /**
     * Change app appearance to look like different app
     * Uses activity-alias in manifest
     */
    fun setDisguiseMode(mode: DisguiseMode) {
        _disguiseMode.value = mode
        prefs.edit().putString(KEY_DISGUISE_MODE, mode.name).apply()
        
        // Enable the selected alias, disable others
        val pm = context.packageManager
        
        for ((disguise, component) in aliasComponents) {
            val newState = if (disguise == mode) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            
            try {
                pm.setComponentEnabledSetting(
                    component,
                    newState,
                    PackageManager.DONT_KILL_APP
                )
            } catch (e: Exception) {
                // May not have permission or alias doesn't exist
            }
        }
    }

    fun getDisguiseMode(): DisguiseMode {
        val savedMode = prefs.getString(KEY_DISGUISE_MODE, null)
        return savedMode?.let { 
            try { DisguiseMode.valueOf(it) } catch (e: Exception) { DisguiseMode.NONE }
        } ?: DisguiseMode.NONE
    }

    // ==================== STEALTH MODE ====================

    /**
     * Enable comprehensive stealth mode:
     * - Hide from recent apps
     * - Silent notifications
     * - No app icon (optional)
     */
    fun setStealthMode(enabled: Boolean) {
        _stealthModeEnabled.value = enabled
        prefs.edit().putBoolean(KEY_STEALTH_MODE, enabled).apply()
        
        if (enabled) {
            // Configure for stealth
            configureStealthNotifications()
        } else {
            // Restore normal settings
            restoreNormalNotifications()
        }
    }

    private fun configureStealthNotifications() {
        // In a real app, would configure notification channels for silent operation
        // This is a placeholder for the concept
    }

    private fun restoreNormalNotifications() {
        // Restore normal notification settings
    }

    // ==================== QUICK EXIT TRIGGERS ====================

    data class PanicTriggerConfig(
        val volumeButtonEnabled: Boolean = true,
        val volumeButtonPattern: String = "down-down-down",  // 3x volume down
        val shakeEnabled: Boolean = false,
        val shakeThreshold: Float = 15f,
        val powerButtonEnabled: Boolean = false,  // 5x power button
        val notificationButtonEnabled: Boolean = true
    )

    private var panicTriggerConfig = PanicTriggerConfig()
    
    fun configurePanicTriggers(config: PanicTriggerConfig) {
        panicTriggerConfig = config
        prefs.edit()
            .putBoolean(KEY_VOLUME_TRIGGER, config.volumeButtonEnabled)
            .putBoolean(KEY_SHAKE_TRIGGER, config.shakeEnabled)
            .putBoolean(KEY_NOTIFICATION_TRIGGER, config.notificationButtonEnabled)
            .apply()
    }

    // Volume button tracking
    private var volumeDownPresses = mutableListOf<Long>()
    private val volumePatternTimeout = 1000L  // 1 second to complete pattern

    fun onVolumeDown(): Boolean {
        if (!panicTriggerConfig.volumeButtonEnabled) return false
        
        val now = System.currentTimeMillis()
        
        // Remove old presses
        volumeDownPresses.removeAll { now - it > volumePatternTimeout }
        volumeDownPresses.add(now)
        
        // Check for pattern (3 presses within timeout)
        if (volumeDownPresses.size >= 3) {
            volumeDownPresses.clear()
            quickPanic()
            return true  // Consumed the event
        }
        
        return false
    }

    // ==================== DECOY MODE ====================

    /**
     * Show fake/decoy content when app is opened under duress
     * User can set a "duress PIN" that shows fake empty state
     */
    private var normalPin: String? = null
    private var duressPin: String? = null

    fun setNormalPin(pin: String) {
        normalPin = pin
        prefs.edit().putString(KEY_NORMAL_PIN, pin).apply()
    }

    fun setDuressPin(pin: String) {
        duressPin = pin
        prefs.edit().putString(KEY_DURESS_PIN, pin).apply()
    }

    fun verifyPin(enteredPin: String): PinResult {
        return when (enteredPin) {
            normalPin -> PinResult.NORMAL_ACCESS
            duressPin -> PinResult.DURESS_ACCESS  // Show fake/empty state
            else -> PinResult.INVALID
        }
    }

    enum class PinResult {
        NORMAL_ACCESS,  // Show real app
        DURESS_ACCESS,  // Show decoy/fake empty app
        INVALID         // Wrong PIN
    }

    // ==================== INITIALIZATION ====================

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _stealthModeEnabled.value = prefs.getBoolean(KEY_STEALTH_MODE, false)
        _disguiseMode.value = getDisguiseMode()
        normalPin = prefs.getString(KEY_NORMAL_PIN, null)
        duressPin = prefs.getString(KEY_DURESS_PIN, null)
        
        panicTriggerConfig = PanicTriggerConfig(
            volumeButtonEnabled = prefs.getBoolean(KEY_VOLUME_TRIGGER, true),
            shakeEnabled = prefs.getBoolean(KEY_SHAKE_TRIGGER, false),
            notificationButtonEnabled = prefs.getBoolean(KEY_NOTIFICATION_TRIGGER, true)
        )
    }

    // ==================== STATUS ====================

    fun getStealthStatus(): String {
        return buildString {
            append("Stealth Mode: ${if (_stealthModeEnabled.value) "ON" else "OFF"}\n")
            append("Disguise: ${_disguiseMode.value.displayName}\n")
            append("Panic Triggers:\n")
            append("  - Volume Button: ${if (panicTriggerConfig.volumeButtonEnabled) "ON" else "OFF"}\n")
            append("  - Shake: ${if (panicTriggerConfig.shakeEnabled) "ON" else "OFF"}\n")
            append("  - Notification: ${if (panicTriggerConfig.notificationButtonEnabled) "ON" else "OFF"}\n")
            if (duressPin != null) {
                append("  - Duress PIN: Configured\n")
            }
        }
    }

    fun destroy() {
        scope.cancel()
    }

    companion object {
        private const val KEY_STEALTH_MODE = "stealth_mode_enabled"
        private const val KEY_DISGUISE_MODE = "disguise_mode"
        private const val KEY_VOLUME_TRIGGER = "volume_trigger_enabled"
        private const val KEY_SHAKE_TRIGGER = "shake_trigger_enabled"
        private const val KEY_NOTIFICATION_TRIGGER = "notification_trigger_enabled"
        private const val KEY_NORMAL_PIN = "normal_pin"
        private const val KEY_DURESS_PIN = "duress_pin"
    }
}
