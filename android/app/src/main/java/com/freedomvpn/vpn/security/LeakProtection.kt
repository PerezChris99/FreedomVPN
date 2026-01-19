package com.freedomvpn.vpn.security

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URL

/**
 * DNS and IPv6 Leak Protection
 * 
 * Prevents DNS queries and IPv6 traffic from leaking outside the VPN tunnel.
 * 
 * DNS Leak:
 * - ISP can see which websites you visit via DNS queries
 * - Can reveal your browsing even with VPN active
 * - Solution: Force DNS through VPN tunnel only
 * 
 * IPv6 Leak:
 * - IPv6 traffic may bypass VPN if not properly configured
 * - Can reveal your real IP address
 * - Solution: Block or tunnel all IPv6 traffic
 * 
 * For Uganda and censored regions:
 * - ISPs may monitor DNS to track user activity
 * - IPv6 may not be properly handled by some VPN configs
 */
object LeakProtection {
    
    private const val TAG = "LeakProtection"
    
    // Secure DNS servers (DoH endpoints)
    val SECURE_DNS_SERVERS = listOf(
        "8.8.8.8" to "Google",
        "8.8.4.4" to "Google Secondary",
        "1.1.1.1" to "Cloudflare",
        "1.0.0.1" to "Cloudflare Secondary",
        "9.9.9.9" to "Quad9",
        "208.67.222.222" to "OpenDNS",
        "208.67.220.220" to "OpenDNS Secondary"
    )
    
    // DNS over HTTPS endpoints
    val DOH_ENDPOINTS = listOf(
        "https://dns.google/dns-query",
        "https://cloudflare-dns.com/dns-query",
        "https://dns.quad9.net/dns-query"
    )
    
    /**
     * DNS leak test result
     */
    data class LeakTestResult(
        val hasDnsLeak: Boolean,
        val hasIpv6Leak: Boolean,
        val dnsServers: List<String>,
        val publicIpv4: String?,
        val publicIpv6: String?,
        val message: String
    )
    
    /**
     * Perform comprehensive leak test
     */
    suspend fun performLeakTest(): LeakTestResult = withContext(Dispatchers.IO) {
        val dnsServers = detectDnsServers()
        val ipv4 = getPublicIpv4()
        val ipv6 = getPublicIpv6()
        
        // Check for DNS leak (if DNS server is not our secure ones)
        val hasDnsLeak = dnsServers.any { dns ->
            SECURE_DNS_SERVERS.none { it.first == dns }
        }
        
        // Check for IPv6 leak (if we have a public IPv6)
        val hasIpv6Leak = ipv6 != null
        
        val message = buildString {
            if (hasDnsLeak) {
                appendLine("⚠️ DNS leak detected! Your DNS queries may be visible.")
            }
            if (hasIpv6Leak) {
                appendLine("⚠️ IPv6 leak detected! Your IPv6 address is visible.")
            }
            if (!hasDnsLeak && !hasIpv6Leak) {
                appendLine("✓ No leaks detected. Your connection is secure.")
            }
        }
        
        LeakTestResult(
            hasDnsLeak = hasDnsLeak,
            hasIpv6Leak = hasIpv6Leak,
            dnsServers = dnsServers,
            publicIpv4 = ipv4,
            publicIpv6 = ipv6,
            message = message.trim()
        )
    }
    
    /**
     * Get public IPv4 address
     */
    suspend fun getPublicIpv4(): String? = withContext(Dispatchers.IO) {
        try {
            val ipCheckUrls = listOf(
                "https://api.ipify.org",
                "https://ipv4.icanhazip.com",
                "https://checkip.amazonaws.com"
            )
            
            for (url in ipCheckUrls) {
                try {
                    val ip = URL(url).readText().trim()
                    if (ip.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
                        return@withContext ip
                    }
                } catch (e: Exception) {
                    continue
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get public IPv4", e)
            null
        }
    }
    
    /**
     * Get public IPv6 address
     */
    suspend fun getPublicIpv6(): String? = withContext(Dispatchers.IO) {
        try {
            val ipCheckUrls = listOf(
                "https://ipv6.icanhazip.com",
                "https://api6.ipify.org"
            )
            
            for (url in ipCheckUrls) {
                try {
                    val ip = URL(url).readText().trim()
                    // Basic IPv6 validation
                    if (ip.contains(":")) {
                        return@withContext ip
                    }
                } catch (e: Exception) {
                    continue
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get public IPv6", e)
            null
        }
    }
    
    /**
     * Detect current DNS servers
     */
    private fun detectDnsServers(): List<String> {
        val dnsServers = mutableListOf<String>()
        
        try {
            // Try to resolve a test domain and see which DNS is used
            // This is a heuristic - actual DNS server detection is limited on Android
            val addresses = InetAddress.getAllByName("dns-test.freedomvpn.app")
            
            // The addresses themselves don't tell us DNS server,
            // but we can check system properties
            val dnsProperty = System.getProperty("net.dns1")
            if (!dnsProperty.isNullOrEmpty()) {
                dnsServers.add(dnsProperty)
            }
            
            val dns2Property = System.getProperty("net.dns2")
            if (!dns2Property.isNullOrEmpty()) {
                dnsServers.add(dns2Property)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to detect DNS servers", e)
        }
        
        return dnsServers
    }
    
    /**
     * Check if an address is IPv6
     */
    fun isIpv6(address: InetAddress): Boolean = address is Inet6Address
    
    /**
     * Check if an address is IPv4
     */
    fun isIpv4(address: InetAddress): Boolean = address is Inet4Address
    
    /**
     * Get VPN builder routes that block IPv6 leaks
     */
    fun getIpv6BlockRoutes(): List<String> {
        // Route all IPv6 to the VPN tunnel (or block it)
        return listOf(
            "::/0"  // All IPv6 traffic
        )
    }
    
    /**
     * Get recommended DNS servers for VPN
     */
    fun getSecureDnsServers(): List<String> {
        return SECURE_DNS_SERVERS.map { it.first }
    }
}
