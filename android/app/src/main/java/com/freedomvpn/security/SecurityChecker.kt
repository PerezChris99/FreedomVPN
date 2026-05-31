package com.freedomvpn.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Security checker for detecting potentially unsafe environments
 * 
 * Detects:
 * - Root access
 * - Debug mode
 * - Emulator
 * - Tampering/modification
 * - Hooking frameworks (Xposed, Frida, etc.)
 * - Debugger attachment
 */
@Singleton
class SecurityChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "SecurityChecker"
        
        // Known root paths
        private val ROOT_PATHS = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su",
            "/su/bin",
            "/system/xbin/daemonsu",
            "/system/etc/.has_su_daemon",
            "/system/etc/.installed_su_daemon",
            "/dev/com.koushikdutta.superuser.daemon/",
            "/system/app/Kinguser.apk"
        )
        
        // Packages associated with root
        private val ROOT_PACKAGES = arrayOf(
            "com.topjohnwu.magisk",
            "com.koushikdutta.superuser",
            "eu.chainfire.supersu",
            "com.noshufou.android.su",
            "com.thirdparty.superuser",
            "com.yellowes.su",
            "com.koushikdutta.rommanager",
            "com.dimonvideo.luckypatcher",
            "com.chelpus.lackypatch",
            "com.ramdroid.appquarantine"
        )
        
        // Hooking framework packages
        private val HOOK_PACKAGES = arrayOf(
            "de.robv.android.xposed",
            "de.robv.android.xposed.installer",
            "com.saurik.substrate",
            "de.robv.android.xposed.mods.redux",
            "io.va.exposed",
            "org.meowcat.edxposed.manager",
            "com.lspd.manager"
        )
        
        // Dangerous apps that might intercept traffic
        private val DANGEROUS_PACKAGES = arrayOf(
            "com.android.vending.billing.InAppBillingService.LUCK",
            "com.chelpus.luckypatcher",
            "com.dimonvideo.luckypatcher",
            "com.android.vendingg",
            "com.android.vending.billing.InAppBillingService.COIN",
            "com.android.protips"
        )
        
        // Emulator indicators
        private val EMULATOR_FILES = arrayOf(
            "/dev/socket/qemud",
            "/dev/qemu_pipe",
            "/system/lib/libc_malloc_debug_qemu.so",
            "/sys/qemu_trace",
            "/system/bin/qemu-props"
        )
    }
    
    /**
     * Perform full security check
     */
    fun performSecurityCheck(): SecurityStatus {
        val checks = mutableListOf<SecurityIssue>()
        
        // Root detection
        if (isDeviceRooted()) {
            checks.add(SecurityIssue(
                type = IssueType.ROOT_DETECTED,
                severity = Severity.HIGH,
                message = "Device appears to be rooted"
            ))
        }
        
        // Debug detection
        if (isDebuggable()) {
            checks.add(SecurityIssue(
                type = IssueType.DEBUG_MODE,
                severity = Severity.MEDIUM,
                message = "App is running in debug mode"
            ))
        }
        
        // Emulator detection
        if (isEmulator()) {
            checks.add(SecurityIssue(
                type = IssueType.EMULATOR,
                severity = Severity.LOW,
                message = "Running on emulator"
            ))
        }
        
        // Hooking framework detection
        if (hasHookingFramework()) {
            checks.add(SecurityIssue(
                type = IssueType.HOOK_DETECTED,
                severity = Severity.HIGH,
                message = "Hooking framework detected"
            ))
        }
        
        // Debugger detection
        if (isDebuggerConnected()) {
            checks.add(SecurityIssue(
                type = IssueType.DEBUGGER_ATTACHED,
                severity = Severity.HIGH,
                message = "Debugger is attached"
            ))
        }
        
        // Signature verification
        if (!verifySignature()) {
            checks.add(SecurityIssue(
                type = IssueType.SIGNATURE_MISMATCH,
                severity = Severity.CRITICAL,
                message = "App signature verification failed"
            ))
        }
        
        // USB debugging
        if (isUsbDebuggingEnabled()) {
            checks.add(SecurityIssue(
                type = IssueType.USB_DEBUG_ENABLED,
                severity = Severity.LOW,
                message = "USB debugging is enabled"
            ))
        }
        
        // Dangerous apps
        val dangerousApps = checkDangerousApps()
        if (dangerousApps.isNotEmpty()) {
            checks.add(SecurityIssue(
                type = IssueType.DANGEROUS_APP,
                severity = Severity.MEDIUM,
                message = "Potentially dangerous apps installed: ${dangerousApps.joinToString()}"
            ))
        }
        
        val overallSeverity = checks.maxOfOrNull { it.severity } ?: Severity.NONE
        
        return SecurityStatus(
            isSecure = checks.none { it.severity >= Severity.HIGH },
            issues = checks,
            overallSeverity = overallSeverity
        )
    }
    
    /**
     * Check if device is rooted
     */
    fun isDeviceRooted(): Boolean {
        return checkRootFiles() || checkRootPackages() || checkSuBinary() || checkRootCloaking()
    }
    
    private fun checkRootFiles(): Boolean {
        for (path in ROOT_PATHS) {
            if (File(path).exists()) {
                Log.d(TAG, "Root file found: $path")
                return true
            }
        }
        return false
    }
    
    private fun checkRootPackages(): Boolean {
        val pm = context.packageManager
        for (pkg in ROOT_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
                Log.d(TAG, "Root package found: $pkg")
                return true
            } catch (e: PackageManager.NameNotFoundException) {
                // Not found, continue
            }
        }
        return false
    }
    
    private fun checkSuBinary(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readLine()
            process.destroy()
            result != null
        } catch (e: Exception) {
            false
        }
    }
    
    private fun checkRootCloaking(): Boolean {
        // Check for MagiskHide and similar
        val props = System.getProperty("ro.build.selinux")
        return props == "0" || props == "false"
    }
    
    /**
     * Check if app is debuggable
     */
    fun isDebuggable(): Boolean {
        return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    
    /**
     * Check if running on emulator
     */
    fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
                Build.PRODUCT == "google_sdk" ||
                Build.PRODUCT == "sdk" ||
                Build.PRODUCT == "sdk_x86" ||
                Build.PRODUCT == "sdk_google_phone_x86" ||
                Build.HARDWARE.contains("goldfish") ||
                Build.HARDWARE.contains("ranchu") ||
                checkEmulatorFiles()
    }
    
    private fun checkEmulatorFiles(): Boolean {
        for (path in EMULATOR_FILES) {
            if (File(path).exists()) {
                return true
            }
        }
        return false
    }
    
    /**
     * Check for hooking frameworks
     */
    fun hasHookingFramework(): Boolean {
        val pm = context.packageManager
        for (pkg in HOOK_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
                Log.d(TAG, "Hook package found: $pkg")
                return true
            } catch (e: PackageManager.NameNotFoundException) {
                // Not found, continue
            }
        }
        
        // Check for Frida
        return checkFrida()
    }
    
    private fun checkFrida(): Boolean {
        // Check for Frida server port
        return try {
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress("127.0.0.1", 27042), 100)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if debugger is connected
     */
    fun isDebuggerConnected(): Boolean {
        return android.os.Debug.isDebuggerConnected()
    }
    
    /**
     * Verify app signature
     */
    fun verifySignature(): Boolean {
        return try {
            @Suppress("DEPRECATION")
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                ).signingInfo
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                ).signatures
            }
            
            // Verify we have signatures
            signatures != null && signatures.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Signature verification failed", e)
            false
        }
    }
    
    /**
     * Check if USB debugging is enabled
     */
    fun isUsbDebuggingEnabled(): Boolean {
        return Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ADB_ENABLED,
            0
        ) == 1
    }
    
    /**
     * Check for dangerous apps
     */
    fun checkDangerousApps(): List<String> {
        val found = mutableListOf<String>()
        val pm = context.packageManager
        
        for (pkg in DANGEROUS_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
                found.add(pkg)
            } catch (e: PackageManager.NameNotFoundException) {
                // Not found
            }
        }
        
        return found
    }
    
    /**
     * Quick security check - only critical issues
     */
    fun quickCheck(): Boolean {
        return !isDebuggerConnected() && verifySignature()
    }
}

/**
 * Security status result
 */
data class SecurityStatus(
    val isSecure: Boolean,
    val issues: List<SecurityIssue>,
    val overallSeverity: Severity
)

/**
 * Security issue
 */
data class SecurityIssue(
    val type: IssueType,
    val severity: Severity,
    val message: String
)

/**
 * Issue types
 */
enum class IssueType {
    ROOT_DETECTED,
    DEBUG_MODE,
    EMULATOR,
    HOOK_DETECTED,
    DEBUGGER_ATTACHED,
    SIGNATURE_MISMATCH,
    USB_DEBUG_ENABLED,
    DANGEROUS_APP
}

/**
 * Severity levels
 */
enum class Severity {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
