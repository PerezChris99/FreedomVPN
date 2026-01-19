package com.freedomvpn

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

/**
 * Main Application class for FreedomVPN
 * Initializes Hilt dependency injection and notification channels
 */
@HiltAndroidApp
class FreedomVpnApplication : Application() {

    companion object {
        const val VPN_NOTIFICATION_CHANNEL_ID = "vpn_channel"
        const val VPN_NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    /**
     * Creates notification channels required for Android O and above
     * The VPN service runs as a foreground service and requires a notification
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vpnChannel = NotificationChannel(
                VPN_NOTIFICATION_CHANNEL_ID,
                getString(R.string.vpn_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows VPN connection status"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(vpnChannel)
        }
    }
}
