# 🌍 FreedomVPN - Break Free From Internet Censorship

<p align="center">
  <img src="docs/assets/logo.png" alt="FreedomVPN Logo" width="200"/>
</p>

<p align="center">
  <strong>Fast • Free • Untraceable • One-Tap Connection</strong>
</p>

<p align="center">
  A cross-platform VPN designed for people living under internet censorship.<br/>
  Built for Uganda 🇺🇬 and anywhere freedom is restricted.
</p>

---

## 🎯 Mission

FreedomVPN exists to provide **free, fast, and untraceable** internet access to people living under oppressive regimes that block websites, throttle connections, and monitor online activity.

**No logs. No tracking. No compromises.**

---

## 📊 Current Development Status

| Phase | Description | Status | Progress |
|-------|-------------|--------|----------|
| Phase 1 | Core VPN Functionality | ✅ Complete | 100% |
| Phase 2 | Server Discovery & Smart Selection | ✅ Complete | 100% |
| Phase 3 | Censorship Bypass Features | ✅ Complete | 100% |
| Phase 4 | User Interface & Experience | ✅ Complete | 100% |
| Phase 5 | Performance Optimization | ✅ Complete | 100% |
| Phase 6 | Security Hardening | ✅ Complete | 100% |
| Phase 7 | Distribution & Polish | ✅ Complete | 100% |

---

## ✨ Features

### 🔒 Core VPN
- **WireGuard Protocol** - Fast, modern, and secure
- **VPN Gate Integration** - Access 100+ free servers worldwide
- **Auto-Reconnect** - Never lose connection
- **Real-time Statistics** - Speed, data usage, and connection time

### 🛡️ Censorship Bypass
- **Traffic Obfuscation** - XOR, padding, and TLS camouflage
- **Port Fallback** - Automatically try ports 443, 80, 53 if standard ports blocked
- **DNS Leak Protection** - Secure DNS servers prevent tracking
- **IPv6 Leak Protection** - Block IPv6 to prevent exposure
- **Kill Switch** - Block all traffic if VPN disconnects

### ⚡ Performance
- **Parallel Connection** - Test multiple servers simultaneously
- **Connection Pooling** - Quick reconnects
- **MTU Discovery** - Optimal packet sizes
- **Battery Optimization** - Adaptive power modes
- **Split Tunneling** - Route specific apps through VPN

### 🔐 Security
- **Root Detection** - Warns on compromised devices
- **Encrypted Storage** - Credentials protected by Android Keystore
- **Certificate Pinning** - Prevents MITM attacks
- **Secure Logging** - No sensitive data logged
- **ProGuard Obfuscation** - Code protection

### 📱 User Experience
- **Modern Dark UI** - Sleek design optimized for VPN apps
- **One-Tap Connect** - Quick connect to fastest server
- **Country Selection** - Choose servers by country with flags
- **Favorites & Recents** - Quick access to preferred servers
- **Speed Graph** - Real-time network visualization

---

## 📥 Installation

### Android
1. Download the latest APK from [Releases](https://github.com/PerezChris99/FreedomVPN/releases)
2. Enable "Install from Unknown Sources" in Settings
3. Install the APK
4. Open FreedomVPN and tap Connect!

### Windows
1. Download the latest MSIX from [Releases](https://github.com/PerezChris99/FreedomVPN/releases)
2. Right-click and select "Install"
3. Launch FreedomVPN from Start Menu

---

## 🛠️ Building from Source

### Prerequisites
- Android Studio Hedgehog or newer
- JDK 17
- Android SDK 34
- Git

### Android Build
```bash
# Clone the repository
git clone https://github.com/PerezChris99/FreedomVPN.git
cd FreedomVPN/android

# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing key)
./gradlew assembleRelease
```

### Windows Build
```powershell
cd FreedomVPN/windows
dotnet build -c Release
```

---

## 📖 Usage Guide

### Quick Connect
1. Open the app
2. Tap the power button
3. FreedomVPN automatically selects the fastest available server

### Manual Server Selection
1. Tap "Select Server" below the power button
2. Browse servers by country
3. Tap a server to connect
4. Add servers to favorites with the ❤️ button

### Settings
- **Kill Switch** - Enable to block all traffic if VPN disconnects
- **Obfuscation** - Set to "High" for censored networks
- **Split Tunneling** - Choose which apps use VPN

---

## 🏗️ Architecture

```
FreedomVPN/
├── android/                    # Android app (Kotlin)
│   ├── app/src/main/java/com/freedomvpn/
│   │   ├── vpn/               # Core VPN service
│   │   │   ├── obfuscation/   # Traffic obfuscation
│   │   │   ├── optimization/  # Performance optimization
│   │   │   └── security/      # Kill switch, leak protection
│   │   ├── security/          # App security (encryption, detection)
│   │   ├── ui/                # Jetpack Compose UI
│   │   │   ├── screens/       # Main screens
│   │   │   ├── components/    # Reusable components
│   │   │   └── theme/         # Dark theme
│   │   ├── viewmodel/         # MVVM ViewModels
│   │   ├── data/              # Data models and repositories
│   │   └── update/            # Auto-update system
│   └── build.gradle.kts
├── windows/                    # Windows app (C#/WinUI 3)
└── docs/                       # Documentation
```

---

## 🤝 Contributing

We welcome contributions! Especially:
- 🌍 Translations for different languages
- 🐛 Bug reports and fixes
- ✨ New obfuscation techniques
- 📝 Documentation improvements

### Development Setup
1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Commit changes: `git commit -m 'Add amazing feature'`
4. Push: `git push origin feature/amazing-feature`
5. Open a Pull Request

---

## ⚠️ Disclaimer

FreedomVPN is designed for legitimate privacy use cases:
- Protecting privacy on public WiFi
- Bypassing censorship in oppressive regions
- Secure communication for journalists and activists

**Do not use for illegal activities.** Users are responsible for compliance with local laws.

---

## 📜 License

This project is licensed under the MIT License - see [LICENSE](LICENSE) for details.

---

## 🙏 Acknowledgments

- [VPN Gate](https://www.vpngate.net/) - Free VPN relay servers
- [WireGuard](https://www.wireguard.com/) - Modern VPN protocol
- Android and Windows open-source communities

---

<p align="center">
  Made with ❤️ for <b>Freedom</b>
</p>
<p align="center">
  🇺🇬 Stand with Uganda 🇺🇬
</p>
| 1.4 | Test connection to VPN Gate servers | ⏳ Pending |
| 1.5 | Implement auto-reconnect on connection drop | ⏳ Pending |
| 1.6 | Add connection statistics (speed, data used) | ⏳ Pending |

**Deliverable:** Working VPN that can connect and route traffic

---

### 📍 PHASE 2: Anti-Censorship Features
**Goal:** Make the VPN work in heavily censored environments

| Task | Description | Status |
|------|-------------|--------|
| 2.1 | **Traffic Obfuscation** - Disguise VPN traffic as normal HTTPS | ⏳ Pending |
| 2.2 | **Domain Fronting** - Hide server destinations using CDN | ⏳ Pending |
| 2.3 | **Shadowsocks Integration** - Alternative protocol for China/Iran-style blocks | ⏳ Pending |
| 2.4 | **Bridge/Relay Servers** - Fallback when direct connection fails | ⏳ Pending |
| 2.5 | **DNS-over-HTTPS** - Prevent DNS-based blocking | ⏳ Pending |
| 2.6 | **Protocol Randomization** - Avoid fingerprinting | ⏳ Pending |

**Deliverable:** VPN that works even when government tries to block it

---

### 📍 PHASE 3: Speed & Performance Optimization
**Goal:** Instant connection, fast browsing

| Task | Description | Status |
|------|-------------|--------|
| 3.1 | **Server Pre-ping** - Test servers in background | ⏳ Pending |
| 3.2 | **Smart Server Selection** - Auto-pick fastest server | ⏳ Pending |
| 3.3 | **Connection Caching** - Remember best servers | ⏳ Pending |
| 3.4 | **Split Tunneling** - Only route blocked sites through VPN | ⏳ Pending |
| 3.5 | **UDP Optimization** - Faster packet handling | ⏳ Pending |
| 3.6 | **Lazy Loading** - App starts instantly | ⏳ Pending |

**Deliverable:** Sub-second connection, minimal speed loss

---

### 📍 PHASE 4: Privacy & Untraceability
**Goal:** Zero logs, zero traces, complete anonymity

| Task | Description | Status |
|------|-------------|--------|
| 4.1 | **No-Log Architecture** - App stores nothing sensitive | ⏳ Pending |
| 4.2 | **RAM-Only Operation** - No disk writes for sensitive data | ⏳ Pending |
| 4.3 | **Kill Switch** - Block internet if VPN drops | ⏳ Pending |
| 4.4 | **DNS Leak Protection** - Prevent identity leaks | ⏳ Pending |
| 4.5 | **IPv6 Leak Protection** - Block IPv6 when on VPN | ⏳ Pending |
| 4.6 | **App Disguise** - Make app look like calculator/notes app | ⏳ Pending |
| 4.7 | **Panic Button** - Quick disconnect + clear all data | ⏳ Pending |

**Deliverable:** Completely untraceable VPN usage

---

### 📍 PHASE 5: Distribution & Installation
**Goal:** Easy installation without Play Store

| Task | Description | Status |
|------|-------------|--------|
| 5.1 | **Signed APK Build** - Release-ready Android app | ⏳ Pending |
| 5.2 | **APK Size Optimization** - Small download (<15MB) | ⏳ Pending |
| 5.3 | **GitHub Releases** - Download from GitHub | ⏳ Pending |
| 5.4 | **Direct Download Website** - Simple landing page | ⏳ Pending |
| 5.5 | **QR Code Sharing** - Share app via QR | ⏳ Pending |
| 5.6 | **Bluetooth/WiFi Share** - Offline app sharing | ⏳ Pending |
| 5.7 | **Windows Installer** - MSIX or standalone .exe | ⏳ Pending |
| 5.8 | **Auto-Update System** - Check for updates in-app | ⏳ Pending |

**Deliverable:** Users can download and install easily

---

### 📍 PHASE 6: User Experience Polish
**Goal:** Simple enough for anyone to use

| Task | Description | Status |
|------|-------------|--------|
| 6.1 | **One-Tap Connect** - Single button to connect | ⏳ Pending |
| 6.2 | **Status Widget** - Home screen connection status | ⏳ Pending |
| 6.3 | **Quick Settings Tile** - Toggle from notification shade | ✅ Complete |
| 6.4 | **Connection Notifications** - Know when protected | ⏳ Pending |
| 6.5 | **Multi-Language Support** - Luganda, Swahili, English | ⏳ Pending |
| 6.6 | **Offline Mode** - App works without internet to fetch servers | ⏳ Pending |
| 6.7 | **Battery Optimization** - Minimal battery drain | ⏳ Pending |

**Deliverable:** Beautiful, intuitive app anyone can use

---

### 📍 PHASE 7: Server Infrastructure (Optional - For Maximum Reliability)
**Goal:** Our own servers for guaranteed access

| Task | Description | Status |
|------|-------------|--------|
| 7.1 | Deploy WireGuard servers in multiple countries | ⏳ Pending |
| 7.2 | Set up load balancing | ⏳ Pending |
| 7.3 | Implement server health monitoring | ⏳ Pending |
| 7.4 | Create server rotation system | ⏳ Pending |
| 7.5 | Set up donation system for server costs | ⏳ Pending |

**Deliverable:** Dedicated fast servers (if funding available)

---

## 🛡️ Security Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         YOUR DEVICE                              │
│  ┌─────────────┐                                                │
│  │ FreedomVPN  │                                                │
│  │    App      │                                                │
│  └──────┬──────┘                                                │
│         │                                                        │
│         ▼                                                        │
│  ┌─────────────────────────────────────────────┐                │
│  │           PROTECTION LAYERS                  │                │
│  │  ┌─────────────────────────────────────┐    │                │
│  │  │ Layer 1: Kill Switch               │    │                │
│  │  │ (Blocks internet if VPN drops)     │    │                │
│  │  └─────────────────────────────────────┘    │                │
│  │  ┌─────────────────────────────────────┐    │                │
│  │  │ Layer 2: DNS Leak Protection       │    │                │
│  │  │ (All DNS through encrypted tunnel) │    │                │
│  │  └─────────────────────────────────────┘    │                │
│  │  ┌─────────────────────────────────────┐    │                │
│  │  │ Layer 3: Traffic Obfuscation       │    │                │
│  │  │ (Looks like normal HTTPS traffic)  │    │                │
│  │  └─────────────────────────────────────┘    │                │
│  │  ┌─────────────────────────────────────┐    │                │
│  │  │ Layer 4: WireGuard Encryption      │    │                │
│  │  │ (Military-grade encryption)        │    │                │
│  │  └─────────────────────────────────────┘    │                │
│  └─────────────────────────────────────────────┘                │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │   ENCRYPTED     │
                    │    TUNNEL       │
                    │  (Invisible to  │
                    │   government)   │
                    └────────┬────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                     VPN SERVER (Outside Uganda)                  │
│                     🇯🇵 🇩🇪 🇳🇱 🇸🇬 🇺🇸 🇬🇧                            │
│                                                                  │
│              Your real IP is hidden here                         │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │   FREE INTERNET │
                    │   Twitter       │
                    │   Facebook      │
                    │   News Sites    │
                    │   Everything    │
                    └─────────────────┘
```

---

## 📱 Installation Guide (Coming Soon)

### Android

```
1. Download FreedomVPN.apk from GitHub Releases
2. Enable "Install from Unknown Sources" in Settings
3. Open the APK and install
4. Open app → Tap "Connect" → Done!
```

### Windows

```
1. Download FreedomVPN-Setup.exe
2. Run installer
3. Open FreedomVPN → Click "Connect" → Done!
```

---

## 🏗️ Project Structure

```
FreedomVPN/
├── android/                    # Android App
│   ├── app/src/main/
│   │   ├── java/com/freedomvpn/
│   │   │   ├── vpn/           # VPN Service
│   │   │   ├── vpngate/       # Server fetching
│   │   │   └── ui/            # User interface
│   │   └── res/               # Resources
│   └── build.gradle.kts
│
├── windows/                    # Windows App
│   └── FreedomVPN.Uwp/
│       ├── Services/          # VPN logic
│       ├── ViewModels/        # UI logic
│       └── VpnPlugin/         # Windows VPN plugin
│
├── shared/                     # Shared tools
│   ├── vpngate/               # Server parser
│   └── wireguard/             # Config tools
│
└── docs/                       # Documentation
```

---

## 🔧 For Developers

### Prerequisites

**Android:**
- Android Studio Arctic Fox+
- JDK 17+
- Android SDK 26+

**Windows:**
- Visual Studio 2022
- Windows 10 SDK
- .NET 6.0+

### Build Commands

```bash
# Android Debug Build
cd android
./gradlew assembleDebug

# Windows Build
cd windows
dotnet build FreedomVPN.sln

# Test VPN Gate Parser
cd shared/vpngate
python parser.py
```

---

## 🤝 Contributing

We welcome contributions! Here's how to help:

1. **Code:** Pick a task from the roadmap above
2. **Test:** Try the app and report bugs
3. **Translate:** Help translate to local languages
4. **Share:** Tell others who need internet freedom
5. **Donate:** Help pay for server costs (coming soon)

```bash
# Fork & Clone
git clone https://github.com/PerezChris99/FreedomVPN.git
cd FreedomVPN
git checkout perez

# Make changes, then
git add .
git commit -m "Your changes"
git push origin perez
```

---

## ⚠️ Important Disclaimers

1. **Use Responsibly:** This tool is for accessing legitimate information
2. **No Guarantees:** We cannot guarantee 100% undetectability
3. **VPN Gate Servers:** Volunteer-run, variable quality
4. **Your Safety First:** Always assess your personal risk

---

## 📞 Support

- **Issues:** [GitHub Issues](https://github.com/PerezChris99/FreedomVPN/issues)
- **Email:** Coming soon
- **Telegram:** Coming soon (for secure communication)

---

## 📄 License

MIT License - Free to use, modify, and distribute.

---

<p align="center">
  <strong>🕊️ Internet Freedom is a Human Right 🕊️</strong>
</p>

<p align="center">
  Made with ❤️ for Uganda and the world
</p>
