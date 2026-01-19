package com.freedomvpn.security

import android.util.Log
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.*

/**
 * Certificate pinning manager for secure server connections
 * 
 * Features:
 * - SHA-256 certificate pinning
 * - Multiple pin support per host
 * - Backup pins for key rotation
 * - Certificate chain validation
 * - MITM attack prevention
 */
@Singleton
class CertificatePinningManager @Inject constructor() {
    
    companion object {
        private const val TAG = "CertPinning"
        
        // VPN Gate API pins (example - replace with actual pins in production)
        private val VPN_GATE_PINS = listOf(
            "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", // Primary
            "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="  // Backup
        )
        
        // Known trusted hosts and their pins
        private val PINNED_HOSTS = mapOf(
            "www.vpngate.net" to VPN_GATE_PINS,
            "vpngate.net" to VPN_GATE_PINS
        )
    }
    
    /**
     * Create OkHttpClient with certificate pinning
     */
    fun createPinnedClient(additionalPins: Map<String, List<String>> = emptyMap()): OkHttpClient {
        val pinnerBuilder = CertificatePinner.Builder()
        
        // Add default pins
        for ((host, pins) in PINNED_HOSTS) {
            for (pin in pins) {
                pinnerBuilder.add(host, pin)
            }
        }
        
        // Add additional pins
        for ((host, pins) in additionalPins) {
            for (pin in pins) {
                pinnerBuilder.add(host, pin)
            }
        }
        
        return OkHttpClient.Builder()
            .certificatePinner(pinnerBuilder.build())
            .build()
    }
    
    /**
     * Verify a certificate against pinned values
     */
    fun verifyCertificate(host: String, certificate: X509Certificate): VerificationResult {
        val pins = PINNED_HOSTS[host]
        if (pins == null) {
            Log.d(TAG, "No pins configured for host: $host")
            return VerificationResult(
                isValid = true,
                reason = "No pins configured, allowing connection"
            )
        }
        
        val certPin = getCertificatePin(certificate)
        
        for (pin in pins) {
            val pinHash = pin.removePrefix("sha256/")
            if (certPin == pinHash) {
                Log.d(TAG, "Certificate pin match for $host")
                return VerificationResult(
                    isValid = true,
                    reason = "Certificate matches pinned value"
                )
            }
        }
        
        Log.e(TAG, "Certificate pin mismatch for $host! Expected one of $pins, got $certPin")
        return VerificationResult(
            isValid = false,
            reason = "Certificate does not match any pinned value (possible MITM)"
        )
    }
    
    /**
     * Get SHA-256 pin for a certificate
     */
    fun getCertificatePin(certificate: X509Certificate): String {
        val publicKey = certificate.publicKey.encoded
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(publicKey)
        return android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP)
    }
    
    /**
     * Create a TrustManager with certificate pinning
     */
    fun createPinningTrustManager(host: String): X509TrustManager {
        return object : X509TrustManager {
            private val defaultTrustManager: X509TrustManager
            
            init {
                val trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm()
                )
                trustManagerFactory.init(null as java.security.KeyStore?)
                defaultTrustManager = trustManagerFactory.trustManagers
                    .filterIsInstance<X509TrustManager>()
                    .first()
            }
            
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                defaultTrustManager.checkClientTrusted(chain, authType)
            }
            
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                // First, do normal validation
                defaultTrustManager.checkServerTrusted(chain, authType)
                
                // Then check pins
                if (chain != null && chain.isNotEmpty()) {
                    val result = verifyCertificate(host, chain[0])
                    if (!result.isValid) {
                        throw SSLException("Certificate pinning failed: ${result.reason}")
                    }
                }
            }
            
            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return defaultTrustManager.acceptedIssuers
            }
        }
    }
    
    /**
     * Create SSL context with pinning
     */
    fun createPinnedSslContext(host: String): SSLContext {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(
            null,
            arrayOf<TrustManager>(createPinningTrustManager(host)),
            java.security.SecureRandom()
        )
        return sslContext
    }
    
    /**
     * Validate certificate chain
     */
    fun validateCertificateChain(chain: Array<Certificate>): ChainValidationResult {
        if (chain.isEmpty()) {
            return ChainValidationResult(
                isValid = false,
                errors = listOf("Empty certificate chain")
            )
        }
        
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        for (i in chain.indices) {
            val cert = chain[i] as? X509Certificate ?: continue
            
            // Check expiration
            try {
                cert.checkValidity()
            } catch (e: Exception) {
                errors.add("Certificate ${i + 1} is expired or not yet valid: ${e.message}")
            }
            
            // Check key usage
            val keyUsage = cert.keyUsage
            if (keyUsage != null && i == 0) {
                // Leaf certificate should have digital signature or key encipherment
                if (!keyUsage[0] && !keyUsage[2]) {
                    warnings.add("Leaf certificate has unusual key usage")
                }
            }
            
            // Check basic constraints for intermediate certs
            if (i > 0) {
                val basicConstraints = cert.basicConstraints
                if (basicConstraints < 0) {
                    warnings.add("Intermediate certificate ${i + 1} is not a CA")
                }
            }
        }
        
        return ChainValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            chainLength = chain.size
        )
    }
    
    /**
     * Extract and log certificate details (for debugging)
     */
    fun logCertificateDetails(certificate: X509Certificate) {
        Log.d(TAG, "=== Certificate Details ===")
        Log.d(TAG, "Subject: ${certificate.subjectDN}")
        Log.d(TAG, "Issuer: ${certificate.issuerDN}")
        Log.d(TAG, "Serial: ${certificate.serialNumber}")
        Log.d(TAG, "Valid from: ${certificate.notBefore}")
        Log.d(TAG, "Valid until: ${certificate.notAfter}")
        Log.d(TAG, "Public key algorithm: ${certificate.publicKey.algorithm}")
        Log.d(TAG, "Signature algorithm: ${certificate.sigAlgName}")
        Log.d(TAG, "SHA-256 Pin: ${getCertificatePin(certificate)}")
        Log.d(TAG, "===========================")
    }
}

/**
 * Result of certificate verification
 */
data class VerificationResult(
    val isValid: Boolean,
    val reason: String
)

/**
 * Result of chain validation
 */
data class ChainValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val chainLength: Int = 0
)
