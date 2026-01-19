# 🌍 FreedomVPN

A cross-platform VPN application for **Android** and **Windows** that provides secure, free VPN access using WireGuard protocol and VPN Gate public servers.

## 🏗️ Project Structure

```
FreedomVPN/
├── android/                    # Android VPN Client
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/freedomvpn/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── vpn/
│   │   │   │   │   ├── FreedomVpnService.kt
│   │   │   │   │   └── VpnConnection.kt
│   │   │   │   ├── wireguard/
│   │   │   │   │   └── WireGuardTunnel.kt
│   │   │   │   ├── vpngate/
│   │   │   │   │   ├── VpnGateRepository.kt
│   │   │   │   │   └── ServerModel.kt
│   │   │   │   └── ui/
│   │   │   │       ├── ServerListFragment.kt
│   │   │   │       └── ConnectionFragment.kt
│   │   │   └── AndroidManifest.xml
│   │   └── build.gradle.kts
│   ├── build.gradle.kts
│   └── settings.gradle.kts
│
├── windows/                    # Windows UWP VPN Plugin
│   ├── FreedomVPN.Uwp/
│   │   ├── VpnPlugin/
│   │   │   ├── VpnBackgroundTask.cs
│   │   │   └── VpnPluginProcessor.cs
│   │   ├── Services/
│   │   │   ├── WireGuardService.cs
│   │   │   └── VpnGateService.cs
│   │   ├── ViewModels/
│   │   │   └── MainViewModel.cs
│   │   ├── Views/
│   │   │   └── MainPage.xaml
│   │   ├── App.xaml
│   │   ├── Package.appxmanifest
│   │   └── FreedomVPN.Uwp.csproj
│   └── FreedomVPN.sln
│
├── shared/                     # Shared Components (reference implementations)
│   ├── vpngate/
│   │   ├── parser.py           # VPN Gate CSV parser (reference)
│   │   └── server_selector.py  # Best server selection algorithm
│   └── wireguard/
│       └── config_generator.py # WireGuard config generator
│
└── docs/
    ├── ANDROID_SETUP.md
    ├── WINDOWS_SETUP.md
    └── VPNGATE_INTEGRATION.md
```

## 🚀 Features

- **WireGuard Protocol**: Fast, modern, and secure VPN protocol
- **VPN Gate Integration**: Access to thousands of free global servers
- **Smart Server Selection**: Automatic ping testing and load balancing
- **Cross-Platform**: Native apps for Android and Windows
- **User-Friendly UI**: Simple one-tap connection

## 📋 Prerequisites

### Android Development
- Android Studio Arctic Fox or newer
- JDK 17+
- Android SDK 26+ (minimum)
- Kotlin 1.9+

### Windows Development
- Visual Studio 2022 with UWP workload
- Windows 10 SDK (10.0.19041.0 or higher)
- .NET 6.0+

## 🔧 Quick Start

### Android
```bash
cd android
./gradlew assembleDebug
```

### Windows
```bash
cd windows
dotnet build FreedomVPN.sln
```

## ⚠️ Important Notes

1. **VPN Gate Servers**: These are volunteer-run servers with variable performance
2. **Logging**: Some VPN Gate operators may log activity - use with awareness
3. **Testing**: Always test with your own server first before relying on public servers

## 📄 License

MIT License - See LICENSE file for details
