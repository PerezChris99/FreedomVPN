package com.freedomvpn.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App update manager using GitHub Releases
 * 
 * Features:
 * - Check for updates from GitHub releases
 * - Download APK in background
 * - Progress tracking
 * - Automatic installation prompt
 */
@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "UpdateManager"
        private const val GITHUB_API_URL = "https://api.github.com/repos/PerezChris99/FreedomVPN/releases/latest"
        private const val PREFS_NAME = "update_prefs"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_SKIPPED_VERSION = "skipped_version"
        private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours
    }
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()
    
    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()
    
    /**
     * Check for updates
     */
    suspend fun checkForUpdate(force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        // Skip if checked recently (unless forced)
        if (!force) {
            val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0)
            if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) {
                Log.d(TAG, "Skipping update check - checked recently")
                return@withContext null
            }
        }
        
        _updateState.value = UpdateState.Checking
        
        try {
            val request = Request.Builder()
                .url(GITHUB_API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to check for updates: ${response.code}")
                _updateState.value = UpdateState.Error("Failed to check for updates")
                return@withContext null
            }
            
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            
            val latestVersion = json.getString("tag_name").removePrefix("v")
            val currentVersion = getCurrentVersion()
            
            prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            
            if (isNewerVersion(latestVersion, currentVersion)) {
                // Check if user skipped this version
                val skippedVersion = prefs.getString(KEY_SKIPPED_VERSION, null)
                if (skippedVersion == latestVersion) {
                    Log.d(TAG, "User skipped version $latestVersion")
                    _updateState.value = UpdateState.Idle
                    return@withContext null
                }
                
                val assets = json.getJSONArray("assets")
                var apkUrl: String? = null
                var apkSize: Long = 0
                
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")
                    if (name.endsWith(".apk")) {
                        apkUrl = asset.getString("browser_download_url")
                        apkSize = asset.getLong("size")
                        break
                    }
                }
                
                if (apkUrl != null) {
                    val updateInfo = UpdateInfo(
                        version = latestVersion,
                        releaseNotes = json.optString("body", ""),
                        downloadUrl = apkUrl,
                        fileSize = apkSize,
                        publishedAt = json.getString("published_at")
                    )
                    
                    _updateState.value = UpdateState.Available(updateInfo)
                    return@withContext updateInfo
                }
            }
            
            _updateState.value = UpdateState.UpToDate
            null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            _updateState.value = UpdateState.Error(e.message ?: "Unknown error")
            null
        }
    }
    
    /**
     * Download and install update
     */
    suspend fun downloadAndInstall(updateInfo: UpdateInfo) = withContext(Dispatchers.IO) {
        _updateState.value = UpdateState.Downloading(0f)
        _downloadProgress.value = 0f
        
        try {
            val request = Request.Builder()
                .url(updateInfo.downloadUrl)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                _updateState.value = UpdateState.Error("Download failed: ${response.code}")
                return@withContext
            }
            
            val body = response.body ?: run {
                _updateState.value = UpdateState.Error("Empty response")
                return@withContext
            }
            
            // Download to cache directory
            val file = File(context.cacheDir, "freedomvpn-${updateInfo.version}.apk")
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L
            
            FileOutputStream(file).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        
                        val progress = if (totalBytes > 0) {
                            downloadedBytes.toFloat() / totalBytes
                        } else {
                            0f
                        }
                        
                        _downloadProgress.value = progress
                        _updateState.value = UpdateState.Downloading(progress)
                    }
                }
            }
            
            _updateState.value = UpdateState.ReadyToInstall(file)
            
            // Prompt installation
            installApk(file)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading update", e)
            _updateState.value = UpdateState.Error(e.message ?: "Download failed")
        }
    }
    
    /**
     * Install downloaded APK
     */
    private fun installApk(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK", e)
            _updateState.value = UpdateState.Error("Installation failed: ${e.message}")
        }
    }
    
    /**
     * Skip this version
     */
    fun skipVersion(version: String) {
        prefs.edit().putString(KEY_SKIPPED_VERSION, version).apply()
        _updateState.value = UpdateState.Idle
    }
    
    /**
     * Get current app version
     */
    private fun getCurrentVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
    
    /**
     * Compare version strings
     */
    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        
        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            
            if (l > c) return true
            if (l < c) return false
        }
        
        return false
    }
    
    fun cleanup() {
        scope.cancel()
    }
}

/**
 * Update state
 */
sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    object UpToDate : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    data class Downloading(val progress: Float) : UpdateState()
    data class ReadyToInstall(val file: File) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

/**
 * Update info
 */
data class UpdateInfo(
    val version: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val fileSize: Long,
    val publishedAt: String
) {
    val fileSizeMb: Float
        get() = fileSize / (1024f * 1024f)
}
