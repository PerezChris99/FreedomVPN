# Android VPN Development Setup Guide

This guide will help you set up the Android development environment for FreedomVPN.

## Prerequisites

- **Android Studio** Arctic Fox (2020.3.1) or newer
- **JDK 17** or higher
- **Android SDK** with API level 26 (Android 8.0) minimum
- **Kotlin** 1.9+

## Project Setup

### 1. Open Project in Android Studio

```bash
cd FreedomVPN/android
```

Open this folder in Android Studio as an existing project.

### 2. Sync Gradle

Android Studio should automatically prompt you to sync Gradle. If not:
- File → Sync Project with Gradle Files

### 3. Install Dependencies

The project uses the following key dependencies:

| Dependency | Purpose |
|------------|---------|
| `com.wireguard.android:tunnel` | WireGuard protocol implementation |
| `com.google.dagger:hilt-android` | Dependency injection |
| `com.squareup.retrofit2:retrofit` | HTTP client for VPN Gate API |
| `androidx.compose.*` | Modern UI toolkit |

## Understanding the VPN Architecture

### VpnService (Core Component)

The `FreedomVpnService` extends Android's `VpnService` class:

```kotlin
class FreedomVpnService : VpnService() {
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> startVpnConnection()
            ACTION_DISCONNECT -> stopVpnConnection()
        }
        return START_STICKY
    }
    
    private suspend fun establishVpnInterface() {
        val builder = Builder()
            .setSession("FreedomVPN")
            .setMtu(1280)
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)  // Route all traffic
            .addDnsServer("8.8.8.8")
        
        vpnInterface = builder.establish()
    }
}
```

### Key Concepts

1. **VpnService.Builder**: Creates the virtual network interface
2. **ParcelFileDescriptor**: The file descriptor for the VPN tunnel
3. **Foreground Service**: VPN must run as a foreground service with notification
4. **User Consent**: Android requires explicit user permission for VPN

### Permissions Required

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

<service
    android:name=".vpn.FreedomVpnService"
    android:permission="android.permission.BIND_VPN_SERVICE">
    <intent-filter>
        <action android:name="android.net.VpnService" />
    </intent-filter>
</service>
```

## VPN Gate Integration

### Fetching Servers

```kotlin
class VpnGateRepository {
    suspend fun fetchServers(): Result<List<VpnGateServer>> {
        val response = httpClient.newCall(
            Request.Builder()
                .url("https://www.vpngate.net/api/iphone/")
                .build()
        ).execute()
        
        return Result.success(parseServerList(response.body?.string()))
    }
}
```

### CSV Format

```
HostName,IP,Score,Ping,Speed,CountryLong,CountryShort,NumVpnSessions,...
```

## WireGuard Integration

### Adding WireGuard Library

The WireGuard Android library is included via Gradle:

```kotlin
implementation("com.wireguard.android:tunnel:1.0.20230706")
```

### Using WireGuard

```kotlin
// Create WireGuard backend
val backend = GoBackend(context)

// Create tunnel
val tunnel = object : Tunnel {
    override fun getName() = "freedom_tunnel"
    override fun onStateChange(state: Tunnel.State) { }
}

// Start tunnel
backend.setState(tunnel, Tunnel.State.UP, config)
```

## Building & Testing

### Debug Build

```bash
./gradlew assembleDebug
```

### Install on Device

```bash
./gradlew installDebug
```

### Run Tests

```bash
./gradlew test
```

## Common Issues

### 1. VPN Permission Denied

Make sure to call `VpnService.prepare()` before connecting:

```kotlin
val intent = VpnService.prepare(context)
if (intent != null) {
    startActivityForResult(intent, VPN_REQUEST_CODE)
} else {
    // Already have permission
    connect()
}
```

### 2. Service Killed by System

Use `START_STICKY` and run as foreground service:

```kotlin
override fun onStartCommand(...): Int {
    startForeground(NOTIFICATION_ID, createNotification())
    return START_STICKY
}
```

### 3. No Internet After Connect

Ensure routes are configured correctly:

```kotlin
builder.addRoute("0.0.0.0", 0)  // All IPv4
builder.addRoute("::", 0)       // All IPv6
```

## Next Steps

1. Implement actual WireGuard tunnel using the established VPN interface
2. Add connection statistics tracking
3. Implement server ping testing for better selection
4. Add split-tunneling support for app bypass
5. Implement auto-reconnect on network changes

## Resources

- [Android VpnService Documentation](https://developer.android.com/reference/android/net/VpnService)
- [WireGuard Android Library](https://github.com/WireGuard/wireguard-android)
- [VPN Gate Project](https://www.vpngate.net/)
