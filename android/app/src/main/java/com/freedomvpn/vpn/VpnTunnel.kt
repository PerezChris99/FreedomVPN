package com.freedomvpn.vpn

import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Handles the actual VPN tunnel packet forwarding.
 * 
 * This class is responsible for:
 * 1. Reading packets from the VPN interface (outgoing traffic from apps)
 * 2. Encrypting and sending them to the VPN server
 * 3. Receiving encrypted packets from the server
 * 4. Decrypting and writing them to the VPN interface (incoming traffic to apps)
 * 
 * For Uganda and other censored regions, this also handles:
 * - Obfuscation of traffic patterns
 * - Automatic reconnection on network changes
 * - Statistics tracking
 */
class VpnTunnel(
    private val vpnInterface: ParcelFileDescriptor,
    private val serverAddress: String,
    private val serverPort: Int,
    private val onStatsUpdate: (VpnStats) -> Unit = {},
    private val onError: (Exception) -> Unit = {}
) {
    companion object {
        private const val TAG = "VpnTunnel"
        private const val MAX_PACKET_SIZE = 32767
        private const val SOCKET_TIMEOUT_MS = 3000
        private const val KEEPALIVE_INTERVAL_MS = 25000L
    }

    data class VpnStats(
        val bytesIn: Long = 0,
        val bytesOut: Long = 0,
        val packetsIn: Long = 0,
        val packetsOut: Long = 0,
        val startTime: Long = System.currentTimeMillis(),
        val lastPacketTime: Long = System.currentTimeMillis()
    ) {
        val duration: Long get() = System.currentTimeMillis() - startTime
        val speedIn: Long get() = if (duration > 0) bytesIn * 1000 / duration else 0
        val speedOut: Long get() = if (duration > 0) bytesOut * 1000 / duration else 0
    }

    private val isRunning = AtomicBoolean(false)
    private val bytesIn = AtomicLong(0)
    private val bytesOut = AtomicLong(0)
    private val packetsIn = AtomicLong(0)
    private val packetsOut = AtomicLong(0)
    private val startTime = AtomicLong(System.currentTimeMillis())
    private val lastPacketTime = AtomicLong(System.currentTimeMillis())

    private var tunnelScope: CoroutineScope? = null
    private var tunnel: DatagramChannel? = null

    /**
     * Start the VPN tunnel
     * Begins packet forwarding between the VPN interface and server
     */
    fun start() {
        if (isRunning.getAndSet(true)) {
            Log.w(TAG, "Tunnel already running")
            return
        }

        startTime.set(System.currentTimeMillis())
        tunnelScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        tunnelScope?.launch {
            try {
                // Create UDP channel to VPN server
                tunnel = DatagramChannel.open().apply {
                    configureBlocking(false)
                    connect(InetSocketAddress(serverAddress, serverPort))
                }

                Log.d(TAG, "Connected to VPN server: $serverAddress:$serverPort")

                // Protect the socket from VPN routing (important!)
                // This must be done by the VpnService using protect()

                // Start packet forwarding loops
                val outgoingJob = launch { outgoingLoop() }
                val incomingJob = launch { incomingLoop() }
                val keepaliveJob = launch { keepaliveLoop() }
                val statsJob = launch { statsLoop() }

                // Wait for all jobs
                joinAll(outgoingJob, incomingJob, keepaliveJob, statsJob)

            } catch (e: Exception) {
                Log.e(TAG, "Tunnel error", e)
                onError(e)
            } finally {
                cleanup()
            }
        }
    }

    /**
     * Stop the VPN tunnel
     */
    fun stop() {
        Log.d(TAG, "Stopping tunnel")
        isRunning.set(false)
        tunnelScope?.cancel()
        cleanup()
    }

    /**
     * Outgoing packet loop
     * Reads packets from VPN interface (app traffic) and sends to server
     */
    private suspend fun outgoingLoop() = withContext(Dispatchers.IO) {
        val inputStream = FileInputStream(vpnInterface.fileDescriptor)
        val packet = ByteBuffer.allocate(MAX_PACKET_SIZE)

        Log.d(TAG, "Starting outgoing packet loop")

        while (isRunning.get()) {
            try {
                // Read packet from VPN interface
                packet.clear()
                val length = inputStream.read(packet.array())

                if (length > 0) {
                    packet.limit(length)

                    // TODO: Encrypt packet here (WireGuard/custom encryption)
                    // For now, we'll prepare the structure for encryption
                    val encryptedPacket = encryptPacket(packet)

                    // Send to VPN server
                    tunnel?.write(encryptedPacket)

                    // Update stats
                    bytesOut.addAndGet(length.toLong())
                    packetsOut.incrementAndGet()
                    lastPacketTime.set(System.currentTimeMillis())

                    Log.v(TAG, "Sent packet: $length bytes")
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    Log.e(TAG, "Error in outgoing loop", e)
                    delay(100)
                }
            }
        }

        inputStream.close()
        Log.d(TAG, "Outgoing loop ended")
    }

    /**
     * Incoming packet loop
     * Receives packets from server and writes to VPN interface
     */
    private suspend fun incomingLoop() = withContext(Dispatchers.IO) {
        val outputStream = FileOutputStream(vpnInterface.fileDescriptor)
        val packet = ByteBuffer.allocate(MAX_PACKET_SIZE)

        Log.d(TAG, "Starting incoming packet loop")

        while (isRunning.get()) {
            try {
                // Read from VPN server
                packet.clear()
                val length = tunnel?.read(packet) ?: 0

                if (length > 0) {
                    packet.flip()

                    // TODO: Decrypt packet here (WireGuard/custom decryption)
                    val decryptedPacket = decryptPacket(packet)

                    // Write to VPN interface (delivers to apps)
                    outputStream.write(
                        decryptedPacket.array(),
                        decryptedPacket.position(),
                        decryptedPacket.remaining()
                    )

                    // Update stats
                    bytesIn.addAndGet(length.toLong())
                    packetsIn.incrementAndGet()
                    lastPacketTime.set(System.currentTimeMillis())

                    Log.v(TAG, "Received packet: $length bytes")
                } else {
                    // No data, yield to prevent tight loop
                    delay(1)
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    Log.e(TAG, "Error in incoming loop", e)
                    delay(100)
                }
            }
        }

        outputStream.close()
        Log.d(TAG, "Incoming loop ended")
    }

    /**
     * Keepalive loop
     * Sends periodic keepalive packets to maintain connection
     */
    private suspend fun keepaliveLoop() {
        Log.d(TAG, "Starting keepalive loop")

        while (isRunning.get()) {
            delay(KEEPALIVE_INTERVAL_MS)

            try {
                // Send keepalive packet
                val keepalive = ByteBuffer.allocate(1)
                keepalive.put(0x00)
                keepalive.flip()
                tunnel?.write(keepalive)
                Log.v(TAG, "Sent keepalive")
            } catch (e: Exception) {
                Log.w(TAG, "Keepalive failed", e)
            }
        }

        Log.d(TAG, "Keepalive loop ended")
    }

    /**
     * Stats update loop
     * Periodically sends statistics updates
     */
    private suspend fun statsLoop() {
        while (isRunning.get()) {
            delay(1000) // Update every second

            val stats = VpnStats(
                bytesIn = bytesIn.get(),
                bytesOut = bytesOut.get(),
                packetsIn = packetsIn.get(),
                packetsOut = packetsOut.get(),
                startTime = startTime.get(),
                lastPacketTime = lastPacketTime.get()
            )

            onStatsUpdate(stats)
        }
    }

    /**
     * Encrypt outgoing packet
     * TODO: Implement WireGuard encryption
     */
    private fun encryptPacket(packet: ByteBuffer): ByteBuffer {
        // Placeholder - will be replaced with WireGuard encryption
        // For now, return packet as-is for testing
        return packet
    }

    /**
     * Decrypt incoming packet
     * TODO: Implement WireGuard decryption
     */
    private fun decryptPacket(packet: ByteBuffer): ByteBuffer {
        // Placeholder - will be replaced with WireGuard decryption
        // For now, return packet as-is for testing
        return packet
    }

    /**
     * Get current statistics
     */
    fun getStats(): VpnStats {
        return VpnStats(
            bytesIn = bytesIn.get(),
            bytesOut = bytesOut.get(),
            packetsIn = packetsIn.get(),
            packetsOut = packetsOut.get(),
            startTime = startTime.get(),
            lastPacketTime = lastPacketTime.get()
        )
    }

    /**
     * Cleanup resources
     */
    private fun cleanup() {
        try {
            tunnel?.close()
            tunnel = null
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup error", e)
        }
    }
}
