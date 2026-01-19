package com.freedomvpn.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure storage manager using Android Keystore and EncryptedSharedPreferences
 * 
 * Features:
 * - Hardware-backed key storage (when available)
 * - Encrypted SharedPreferences for sensitive data
 * - Secure key generation
 * - Certificate storage
 * - Token management
 */
@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "SecureStorage"
        private const val KEYSTORE_ALIAS = "FreedomVPNKey"
        private const val ENCRYPTED_PREFS_NAME = "freedom_vpn_secure_prefs"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
        
        // Keys for stored values
        private const val KEY_VPN_CREDENTIALS = "vpn_credentials"
        private const val KEY_PRIVATE_KEY = "private_key"
        private const val KEY_PUBLIC_KEY = "public_key"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_SERVER_CERTS = "server_certs"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_SETTINGS = "settings"
    }
    
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }
    
    private val encryptedPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }
    }
    
    init {
        ensureKeyExists()
    }
    
    /**
     * Ensure encryption key exists in keystore
     */
    private fun ensureKeyExists() {
        try {
            if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
                generateKey()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking/creating key", e)
        }
    }
    
    /**
     * Generate a new AES key in the Android Keystore
     */
    private fun generateKey() {
        try {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            
            val keySpec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false) // Can set true for extra security
                .build()
            
            keyGenerator.init(keySpec)
            keyGenerator.generateKey()
            
            Log.d(TAG, "Generated new encryption key")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate key", e)
        }
    }
    
    /**
     * Get the secret key from keystore
     */
    private fun getSecretKey(): SecretKey? {
        return try {
            val entry = keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
            entry?.secretKey
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get secret key", e)
            null
        }
    }
    
    /**
     * Encrypt data using the keystore key
     */
    fun encrypt(data: ByteArray): EncryptedData? {
        return try {
            val secretKey = getSecretKey() ?: return null
            
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(data)
            
            EncryptedData(iv, encryptedBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed", e)
            null
        }
    }
    
    /**
     * Decrypt data using the keystore key
     */
    fun decrypt(encryptedData: EncryptedData): ByteArray? {
        return try {
            val secretKey = getSecretKey() ?: return null
            
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, encryptedData.iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            
            cipher.doFinal(encryptedData.data)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed", e)
            null
        }
    }
    
    /**
     * Store a string securely
     */
    fun putString(key: String, value: String) {
        encryptedPrefs.edit().putString(key, value).apply()
    }
    
    /**
     * Retrieve a string securely
     */
    fun getString(key: String, default: String? = null): String? {
        return encryptedPrefs.getString(key, default)
    }
    
    /**
     * Store VPN credentials
     */
    fun storeVpnCredentials(username: String, password: String) {
        val credentials = "$username:$password"
        putString(KEY_VPN_CREDENTIALS, credentials)
        Log.d(TAG, "VPN credentials stored securely")
    }
    
    /**
     * Retrieve VPN credentials
     */
    fun getVpnCredentials(): Pair<String, String>? {
        val stored = getString(KEY_VPN_CREDENTIALS) ?: return null
        val parts = stored.split(":", limit = 2)
        return if (parts.size == 2) Pair(parts[0], parts[1]) else null
    }
    
    /**
     * Store WireGuard private key
     */
    fun storePrivateKey(privateKey: String) {
        putString(KEY_PRIVATE_KEY, privateKey)
        Log.d(TAG, "Private key stored securely")
    }
    
    /**
     * Retrieve WireGuard private key
     */
    fun getPrivateKey(): String? {
        return getString(KEY_PRIVATE_KEY)
    }
    
    /**
     * Store WireGuard public key
     */
    fun storePublicKey(publicKey: String) {
        putString(KEY_PUBLIC_KEY, publicKey)
    }
    
    /**
     * Retrieve WireGuard public key
     */
    fun getPublicKey(): String? {
        return getString(KEY_PUBLIC_KEY)
    }
    
    /**
     * Store auth token
     */
    fun storeAuthToken(token: String) {
        putString(KEY_AUTH_TOKEN, token)
    }
    
    /**
     * Retrieve auth token
     */
    fun getAuthToken(): String? {
        return getString(KEY_AUTH_TOKEN)
    }
    
    /**
     * Store server certificates (JSON array)
     */
    fun storeServerCerts(certs: String) {
        putString(KEY_SERVER_CERTS, certs)
    }
    
    /**
     * Retrieve server certificates
     */
    fun getServerCerts(): String? {
        return getString(KEY_SERVER_CERTS)
    }
    
    /**
     * Store favorites (JSON)
     */
    fun storeFavorites(favorites: String) {
        putString(KEY_FAVORITES, favorites)
    }
    
    /**
     * Get favorites
     */
    fun getFavorites(): String? {
        return getString(KEY_FAVORITES)
    }
    
    /**
     * Store settings (JSON)
     */
    fun storeSettings(settings: String) {
        putString(KEY_SETTINGS, settings)
    }
    
    /**
     * Get settings
     */
    fun getSettings(): String? {
        return getString(KEY_SETTINGS)
    }
    
    /**
     * Generate a cryptographically secure random key
     */
    fun generateRandomKey(length: Int = 32): ByteArray {
        val random = SecureRandom()
        val key = ByteArray(length)
        random.nextBytes(key)
        return key
    }
    
    /**
     * Generate a WireGuard-compatible private key
     */
    fun generateWireGuardPrivateKey(): String {
        val key = generateRandomKey(32)
        // WireGuard key clamping
        key[0] = (key[0].toInt() and 248).toByte()
        key[31] = (key[31].toInt() and 127).toByte()
        key[31] = (key[31].toInt() or 64).toByte()
        return Base64.encodeToString(key, Base64.NO_WRAP)
    }
    
    /**
     * Clear all stored data (for logout/reset)
     */
    fun clearAll() {
        encryptedPrefs.edit().clear().apply()
        Log.d(TAG, "All secure storage cleared")
    }
    
    /**
     * Check if keystore is hardware-backed
     */
    fun isHardwareBacked(): Boolean {
        return try {
            val key = getSecretKey()
            if (key != null) {
                val keyFactory = javax.crypto.SecretKeyFactory.getInstance(
                    key.algorithm,
                    ANDROID_KEYSTORE
                )
                val keyInfo = keyFactory.getKeySpec(
                    key,
                    android.security.keystore.KeyInfo::class.java
                ) as android.security.keystore.KeyInfo
                keyInfo.isInsideSecureHardware
            } else false
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Encrypted data container
 */
data class EncryptedData(
    val iv: ByteArray,
    val data: ByteArray
) {
    /**
     * Encode to Base64 string for storage
     */
    fun toBase64(): String {
        val combined = ByteArray(iv.size + data.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(data, 0, combined, iv.size, data.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }
    
    companion object {
        private const val IV_LENGTH = 12
        
        /**
         * Decode from Base64 string
         */
        fun fromBase64(encoded: String): EncryptedData? {
            return try {
                val combined = Base64.decode(encoded, Base64.NO_WRAP)
                if (combined.size <= IV_LENGTH) return null
                
                val iv = combined.copyOfRange(0, IV_LENGTH)
                val data = combined.copyOfRange(IV_LENGTH, combined.size)
                EncryptedData(iv, data)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EncryptedData
        if (!iv.contentEquals(other.iv)) return false
        if (!data.contentEquals(other.data)) return false
        return true
    }
    
    override fun hashCode(): Int {
        var result = iv.contentHashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}
