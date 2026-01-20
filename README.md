# 🌍 FreedomVPN - Anti-Censorship Edition v2.0

<p align="center">
  <img src="docs/assets/logo.png" alt="FreedomVPN Logo" width="200"/>
</p>

<p align="center">
  <strong>Unblockable • Fast • Secure • Untraceable</strong>
</p>

<p align="center">
  A cross-platform VPN designed to defeat internet censorship in Uganda and Africa.<br/>
  Built with domain fronting, TLS obfuscation, and automatic failover.
</p>

---

## 🚀 What's New in v2.0 (Anti-Censorship Edition)

### 🛡️ Advanced Anti-Censorship
- **Domain Fronting** through Cloudflare, Google, Azure, Amazon CDN
- **TLS Camouflage** - VPN traffic looks like normal HTTPS
- **WebSocket Tunneling** - Alternative transport layer
- **Traffic Morphing** - Defeats Deep Packet Inspection (DPI)
- **Automatic Failover** - Instantly switches servers when blocked

### 🔒 Enhanced Privacy
- **WebRTC Leak Protection** - Blocks IP leaks via browsers
- **DNS-over-HTTPS** - Encrypted DNS (Cloudflare, Google, Quad9)
- **IPv6 Protection** - Prevents IPv6 leaks
- **Kill Switch** - Blocks all traffic if VPN disconnects

### 📊 Real-Time Statistics
- Live bandwidth monitoring
- Latency and connection quality
- Data saved by compression
- Money saved in UGX (Ugandan Shillings)
- Blocks evaded counter

### 🌍 24 Global Servers
- **7 African** (Kenya, Rwanda, Tanzania, South Africa, Egypt, Nigeria, Ghana)
- **5 European** (Netherlands, Germany, UK, France, Switzerland)
- **4 Americas** (US NYC/LAX, Brazil, Canada)
- **4 Asia** (Singapore, Japan, UAE, India)
- **4 CDN Fallback** (Nearly unblockable)

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
| **Phase 8** | **Anti-Censorship Hardening (UCC Uganda)** | **✅ Complete** | **100%** |

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

### 🔀 Multi-Hop Server Bouncing (NEW!)
- **Server Chaining** - Bounce traffic through multiple VPN servers
- **4 Anonymity Presets** - Fast (2 hops), Balanced (2 diverse), Maximum (3), Paranoid (4)
- **Geographic Diversity** - Ensures hops are in different regions
- **Instant Activation** - One-click toggle with minimal speed loss
- **Auto Chain Rotation** - Periodic switching for enhanced anonymity
- **Speed Retention** - ~90% for 2 hops, ~70% for 4 hops

---

## 🧪 Test Results & Verification

| Metric | Result |
|--------|--------|
| **Total Tests** | 92 |
| **Passed** | 92 ✅ |
| **Failed** | 0 |
| **Pass Rate** | 100% |
| **Anonymity Rating** | MAXIMUM |

### Test Suites
- ✅ **VPN Gate Integration** - 8 tests (API, parsing, server selection)
- ✅ **Platform Compatibility** - 24 tests (Web, Windows, Android, Extension)
- ✅ **Anonymity Verification** - 40 tests (leaks, encryption, privacy)
- ✅ **Multi-Hop Server Bouncing** - 28 tests (all platforms)

📋 **[View Complete Test Results →](docs/TEST_RESULTS.md)**

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
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                   PROTECTION LAYERS                          ││
│  │  ┌─────────────────────────────────────────────────────────┐││
│  │  │ Layer 1: Kill Switch (Blocks internet if VPN drops)    │││
│  │  └─────────────────────────────────────────────────────────┘││
│  │  ┌─────────────────────────────────────────────────────────┐││
│  │  │ Layer 2: DNS-over-HTTPS (Encrypted DNS via Cloudflare) │││
│  │  └─────────────────────────────────────────────────────────┘││
│  │  ┌─────────────────────────────────────────────────────────┐││
│  │  │ Layer 3: WebRTC/IPv6 Leak Protection (No IP leaks)     │││
│  │  └─────────────────────────────────────────────────────────┘││
│  │  ┌─────────────────────────────────────────────────────────┐││
│  │  │ Layer 4: TLS Camouflage (Looks like normal HTTPS)      │││
│  │  └─────────────────────────────────────────────────────────┘││
│  │  ┌─────────────────────────────────────────────────────────┐││
│  │  │ Layer 5: Domain Fronting (Routes via CDNs)             │││
│  │  └─────────────────────────────────────────────────────────┘││
│  │  ┌─────────────────────────────────────────────────────────┐││
│  │  │ Layer 6: WireGuard Encryption (Military-grade)         │││
│  │  └─────────────────────────────────────────────────────────┘││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼ (Invisible to government/ISP)
┌─────────────────────────────────────────────────────────────────┐
│                     VPN SERVER (Outside Uganda)                  │
│              🇰🇪 🇷🇼 🇿🇦 🇳🇱 🇩🇪 🇬🇧 🇨🇭 🇺🇸 🇸🇬 🇯🇵                     │
│              + CDN Fallbacks: ☁️ Cloudflare, Google, Azure       │
│                     Your real IP is hidden here                  │
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

## 📱 Installation Guide

### Chrome Extension
```
1. Download or clone the repository
2. Open Chrome → chrome://extensions
3. Enable "Developer mode"
4. Click "Load unpacked" → Select extension/ folder
5. Click FreedomVPN icon → Connect!
```

### Android
```
1. Download FreedomVPN.apk from GitHub Releases
2. Enable "Install from Unknown Sources" in Settings
3. Open the APK and install
4. Open app → Tap "Connect" → Done!
```

### Windows
```
1. Install Node.js and npm
2. cd windows && npm install
3. npm start (for development)
4. npm run build:win (for installer)
```

---

## 🏗️ Project Structure

```
FreedomVPN/
├── extension/                  # Chrome Extension v2.0.0
│   ├── background.js          # (828 lines) Enhanced proxy + anti-censorship
│   ├── popup.js               # Dynamic stats UI
│   ├── popup.html             # CDN tab, quality indicators
│   ├── popup.css              # Dark theme styling
│   ├── manifest.json          # v2.0.0 with privacy permissions
│   └── icons/                 # Extension icons
│
├── windows/                    # Windows Electron App v2.0.0
│   ├── main.js                # (618 lines) Full anti-censorship
│   ├── renderer.js            # UI logic
│   ├── index.html             # Custom titlebar, stats
│   ├── styles.css             # Dark theme
│   ├── preload.js             # Secure IPC bridge
│   └── package.json           # Electron 28, electron-builder
│
├── app/src/main/java/.../vpn/ # Android App (Kotlin)
│   ├── anticensorship/        # CensorshipBypassEngine.kt (532 lines)
│   │                          # LeakProtection.kt
│   └── stats/                 # DynamicStatsEngine.kt
│
├── shared/                     # Cross-Platform JavaScript Modules
│   ├── anticensorship/        # CensorshipBypassEngine.js (778 lines)
│   │                          # LeakProtection.js (403 lines)
│   ├── stats/                 # DynamicStatsEngine.js (457 lines)
│   ├── vpngate/               # Server parser
│   └── wireguard/             # Config tools
│
├── web/                        # Demo website
├── docs/                       # Documentation
│   └── SYSTEM_VERIFICATION.md # Full system verification report
└── android/                    # Android project files
```

---

## 🔧 For Developers

### Prerequisites

**Chrome Extension:**
- Chrome/Chromium browser
- Developer mode enabled

**Android:**
- Android Studio Hedgehog+
- JDK 17+
- Android SDK 34+

**Windows:**
- Node.js 18+
- Electron 28

### Build Commands

```bash
# Chrome Extension - Load unpacked in chrome://extensions

# Android Debug Build
cd android
./gradlew assembleDebug

# Windows Development
cd windows
npm install
npm start

# Windows Build Installer
cd windows
npm run build:win
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

<p align="center">
  ⚡ Developed by <a href="https://perezchris.netlify.app"><strong>Nemesis</strong></a>
</p>
