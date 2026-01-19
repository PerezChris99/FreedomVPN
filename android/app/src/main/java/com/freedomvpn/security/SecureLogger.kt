package com.freedomvpn.security

import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*

/**
 * Secure logging system that prevents sensitive data exposure
 * 
 * Features:
 * - Automatic PII/credential filtering
 * - Log level controls
 * - In-memory log buffer (no persistent storage of sensitive logs)
 * - Secure log export
 * - Debug vs Release behavior
 */
@Singleton
class SecureLogger @Inject constructor() {
    
    companion object {
        private const val TAG = "FreedomVPN"
        private const val MAX_LOG_ENTRIES = 1000
        private const val REDACTED = "[REDACTED]"
        
        // Patterns that indicate sensitive data
        private val SENSITIVE_PATTERNS = listOf(
            Regex("password[\"']?\\s*[:=]\\s*[\"']?([^\"'\\s,}]+)", RegexOption.IGNORE_CASE),
            Regex("token[\"']?\\s*[:=]\\s*[\"']?([^\"'\\s,}]+)", RegexOption.IGNORE_CASE),
            Regex("secret[\"']?\\s*[:=]\\s*[\"']?([^\"'\\s,}]+)", RegexOption.IGNORE_CASE),
            Regex("key[\"']?\\s*[:=]\\s*[\"']?([A-Za-z0-9+/=]{20,})", RegexOption.IGNORE_CASE),
            Regex("bearer\\s+([A-Za-z0-9._-]+)", RegexOption.IGNORE_CASE),
            Regex("api[_-]?key[\"']?\\s*[:=]\\s*[\"']?([^\"'\\s,}]+)", RegexOption.IGNORE_CASE),
            Regex("auth[\"']?\\s*[:=]\\s*[\"']?([^\"'\\s,}]+)", RegexOption.IGNORE_CASE),
            Regex("credential[s]?[\"']?\\s*[:=]\\s*[\"']?([^\"'\\s,}]+)", RegexOption.IGNORE_CASE),
            // WireGuard private key pattern (Base64, 44 chars)
            Regex("([A-Za-z0-9+/]{43}=)", RegexOption.IGNORE_CASE),
            // IP addresses (partial redaction)
            Regex("(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\.(\\d{1,3})"),
            // Email addresses
            Regex("([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})"),
        )
        
        // Headers to redact
        private val SENSITIVE_HEADERS = setOf(
            "Authorization",
            "Cookie",
            "Set-Cookie",
            "X-Auth-Token",
            "X-API-Key"
        )
    }
    
    enum class Level {
        VERBOSE, DEBUG, INFO, WARN, ERROR, NONE
    }
    
    private var minLevel = Level.DEBUG
    private var isDebugBuild = false
    private val logBuffer = ConcurrentLinkedQueue<LogEntry>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /**
     * Configure the logger
     */
    fun configure(debugBuild: Boolean, minLevel: Level = Level.DEBUG) {
        this.isDebugBuild = debugBuild
        this.minLevel = if (debugBuild) minLevel else Level.INFO
    }
    
    /**
     * Log verbose message
     */
    fun v(tag: String, message: String) {
        log(Level.VERBOSE, tag, message)
    }
    
    /**
     * Log debug message
     */
    fun d(tag: String, message: String) {
        log(Level.DEBUG, tag, message)
    }
    
    /**
     * Log info message
     */
    fun i(tag: String, message: String) {
        log(Level.INFO, tag, message)
    }
    
    /**
     * Log warning message
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.WARN, tag, message, throwable)
    }
    
    /**
     * Log error message
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.ERROR, tag, message, throwable)
    }
    
    /**
     * Core logging function
     */
    private fun log(level: Level, tag: String, message: String, throwable: Throwable? = null) {
        if (level.ordinal < minLevel.ordinal) return
        
        val sanitizedMessage = sanitize(message)
        val fullTag = "$TAG/$tag"
        
        // Log to Android log in debug builds
        if (isDebugBuild) {
            when (level) {
                Level.VERBOSE -> Log.v(fullTag, sanitizedMessage, throwable)
                Level.DEBUG -> Log.d(fullTag, sanitizedMessage, throwable)
                Level.INFO -> Log.i(fullTag, sanitizedMessage, throwable)
                Level.WARN -> Log.w(fullTag, sanitizedMessage, throwable)
                Level.ERROR -> Log.e(fullTag, sanitizedMessage, throwable)
                Level.NONE -> { /* No logging */ }
            }
        }
        
        // Add to in-memory buffer
        addToBuffer(LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = sanitizedMessage,
            throwable = throwable?.let { sanitizeThrowable(it) }
        ))
    }
    
    /**
     * Sanitize message by removing sensitive data
     */
    private fun sanitize(message: String): String {
        var sanitized = message
        
        for (pattern in SENSITIVE_PATTERNS) {
            sanitized = pattern.replace(sanitized) { match ->
                // Keep the key/label but redact the value
                val fullMatch = match.value
                val value = match.groupValues.getOrNull(1) ?: match.value
                fullMatch.replace(value, REDACTED)
            }
        }
        
        return sanitized
    }
    
    /**
     * Sanitize throwable stack trace
     */
    private fun sanitizeThrowable(throwable: Throwable): String {
        val stackTrace = throwable.stackTraceToString()
        return sanitize(stackTrace).take(500) // Limit length
    }
    
    /**
     * Add entry to buffer, removing old entries if needed
     */
    private fun addToBuffer(entry: LogEntry) {
        logBuffer.add(entry)
        while (logBuffer.size > MAX_LOG_ENTRIES) {
            logBuffer.poll()
        }
    }
    
    /**
     * Get recent logs
     */
    fun getRecentLogs(count: Int = 100): List<LogEntry> {
        return logBuffer.toList().takeLast(count)
    }
    
    /**
     * Get logs filtered by level
     */
    fun getLogsByLevel(level: Level): List<LogEntry> {
        return logBuffer.filter { it.level == level }
    }
    
    /**
     * Export logs for debugging (sanitized)
     */
    fun exportLogs(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val builder = StringBuilder()
        
        builder.appendLine("=== FreedomVPN Logs ===")
        builder.appendLine("Exported: ${dateFormat.format(Date())}")
        builder.appendLine("Entries: ${logBuffer.size}")
        builder.appendLine()
        
        for (entry in logBuffer) {
            val time = dateFormat.format(Date(entry.timestamp))
            val level = entry.level.name.padEnd(5)
            builder.appendLine("[$time] $level/${entry.tag}: ${entry.message}")
            entry.throwable?.let {
                builder.appendLine("  Exception: $it")
            }
        }
        
        return builder.toString()
    }
    
    /**
     * Clear all logs
     */
    fun clearLogs() {
        logBuffer.clear()
    }
    
    /**
     * Log network request (with header sanitization)
     */
    fun logNetworkRequest(
        method: String,
        url: String,
        headers: Map<String, String>,
        statusCode: Int? = null
    ) {
        val sanitizedUrl = sanitize(url)
        val sanitizedHeaders = headers.mapValues { (key, value) ->
            if (key in SENSITIVE_HEADERS) REDACTED else sanitize(value)
        }
        
        val message = buildString {
            append("$method $sanitizedUrl")
            statusCode?.let { append(" -> $it") }
            if (sanitizedHeaders.isNotEmpty() && isDebugBuild) {
                append(" Headers: $sanitizedHeaders")
            }
        }
        
        d("Network", message)
    }
    
    /**
     * Log VPN event
     */
    fun logVpnEvent(event: String, details: Map<String, Any> = emptyMap()) {
        val sanitizedDetails = details.mapValues { (_, value) ->
            when (value) {
                is String -> sanitize(value)
                else -> value.toString()
            }
        }
        
        i("VPN", "$event ${if (sanitizedDetails.isNotEmpty()) sanitizedDetails else ""}")
    }
    
    fun cleanup() {
        scope.cancel()
        clearLogs()
    }
}

/**
 * Log entry data class
 */
data class LogEntry(
    val timestamp: Long,
    val level: SecureLogger.Level,
    val tag: String,
    val message: String,
    val throwable: String? = null
)
