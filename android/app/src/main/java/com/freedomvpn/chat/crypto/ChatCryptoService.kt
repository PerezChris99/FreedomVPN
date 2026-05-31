package com.freedomvpn.chat.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encryption service for secure chat
 * 
 * Uses ECDH P-256 for key exchange and AES-256-GCM for message encryption.
 * Private keys are stored in Android Keystore (hardware-backed when available).
 */
class ChatCryptoService {

    companion object {
        private const val KEYSTORE_ALIAS = "FreedomChat_ECDH"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val CURVE = "secp256r1"
    }

    private var keyPair: KeyPair? = null
    private val sharedSecrets = mutableMapOf<String, SecretKey>()
    private val secureRandom = SecureRandom()

    /**
     * Initialize - generate or load ECDH key pair from Android Keystore
     */
    fun initialize() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            // Load existing key pair
            val privateKey = keyStore.getKey(KEYSTORE_ALIAS, null) as? PrivateKey
            val publicKey = keyStore.getCertificate(KEYSTORE_ALIAS)?.publicKey
            if (privateKey != null && publicKey != null) {
                keyPair = KeyPair(publicKey, privateKey)
                return
            }
        }

        // Generate new ECDH key pair in Android Keystore
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE
        )
        keyPairGenerator.initialize(
            KeyGenParameterSpec.Builder(KEYSTORE_ALIAS, KeyProperties.PURPOSE_AGREE_KEY)
                .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE))
                .build()
        )
        keyPair = keyPairGenerator.generateKeyPair()
    }

    /**
     * Get our public key as Base64 string for sharing
     */
    fun getPublicKeyBase64(): String {
        val pub = keyPair?.public ?: throw IllegalStateException("Not initialized")
        return Base64.encodeToString(pub.encoded, Base64.NO_WRAP)
    }

    /**
     * Get Freedom ID - first 16 hex chars of SHA-256 of public key
     */
    fun getFreedomId(): String {
        val pub = keyPair?.public ?: throw IllegalStateException("Not initialized")
        val digest = MessageDigest.getInstance("SHA-256").digest(pub.encoded)
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    /**
     * Derive shared secret from contact's public key using ECDH
     */
    fun addContact(contactPublicKeyBase64: String) {
        val contactKeyBytes = Base64.decode(contactPublicKeyBase64, Base64.NO_WRAP)
        val keyFactory = KeyFactory.getInstance("EC")
        val contactPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(contactKeyBytes))

        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(keyPair?.private)
        keyAgreement.doPhase(contactPublicKey, true)

        val sharedSecret = keyAgreement.generateSecret()
        
        // Derive AES-256 key from shared secret via SHA-256
        val derivedKey = MessageDigest.getInstance("SHA-256").digest(sharedSecret)
        val secretKey = SecretKeySpec(derivedKey, "AES")
        
        sharedSecrets[contactPublicKeyBase64] = secretKey
    }

    /**
     * Encrypt a message for a specific contact
     * Returns Base64 encoded: IV + Ciphertext + GCM Tag
     */
    fun encryptMessage(contactPublicKeyBase64: String, plaintext: String): String {
        val secretKey = sharedSecrets[contactPublicKeyBase64]
            ?: throw IllegalArgumentException("Contact not added. Call addContact first.")

        val iv = ByteArray(GCM_IV_LENGTH)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))

        // Pad message to fixed block size to prevent length analysis
        val padded = padMessage(plaintext.toByteArray(Charsets.UTF_8))
        val ciphertext = cipher.doFinal(padded)

        // IV + ciphertext (includes GCM tag)
        val combined = iv + ciphertext
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypt a message from a specific contact
     */
    fun decryptMessage(contactPublicKeyBase64: String, encryptedBase64: String): String {
        val secretKey = sharedSecrets[contactPublicKeyBase64]
            ?: throw IllegalArgumentException("Contact not added. Call addContact first.")

        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)

        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))

        val padded = cipher.doFinal(ciphertext)
        return unpadMessage(padded).toString(Charsets.UTF_8)
    }

    /**
     * Pad message to fixed 256-byte blocks to prevent traffic analysis
     */
    private fun padMessage(data: ByteArray): ByteArray {
        val blockSize = 256
        val paddedLen = ((data.size / blockSize) + 1) * blockSize
        val padded = ByteArray(paddedLen)
        System.arraycopy(data, 0, padded, 0, data.size)
        // Last byte stores padding length
        padded[padded.size - 1] = (paddedLen - data.size).toByte()
        return padded
    }

    private fun unpadMessage(data: ByteArray): ByteArray {
        val paddingLen = data[data.size - 1].toInt() and 0xFF
        return data.copyOfRange(0, data.size - paddingLen)
    }

    /**
     * Wipe all keys and secrets from memory
     */
    fun wipeAll() {
        sharedSecrets.clear()
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            keyStore.deleteEntry(KEYSTORE_ALIAS)
        }
        keyPair = null
    }
}
