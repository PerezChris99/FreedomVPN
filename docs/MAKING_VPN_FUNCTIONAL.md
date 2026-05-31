# Making FreedomVPN Fully Functional - Complete Guide

## Current FreedomVPN Behavior - What to Expect

### 🌐 **Web App** (React/Vite)

**On First Load:**
1. **Permission Prompt** - Browser will ask for **location access** (GPS/Network)
2. **IP Detection** - Detects your real IP address using multiple APIs (ipify, ipwho.is, ipapi.co)
3. **Server Fetching** - Attempts to fetch **real VPN servers** from VPNGate API (~100+ servers)
4. **Auto-Selection** - If location granted, auto-selects the **nearest server** to you

**Connection Behavior:**
- Clicking "Connect" simulates a VPN connection (1.5-2.5 second delay)
- Shows the **VPN server's IP** as your new masked address
- Runs **privacy checks** every 60 seconds (WebRTC leak detection)
- Tracks simulated data usage and "money saved" stats

**What You'll See:**
- Real server list with flags, ping times, speeds from VPNGate
- Your detected city/country from geolocation
- Privacy score (0-100) based on leak detection
- Server regions: Africa, Europe, Americas, Asia

**⚠️ Limitation:** The web version **cannot actually tunnel traffic** - browsers don't have VPN capabilities. It's a UI demonstration only.

---

### 🖥️ **Windows Desktop App** (Electron)

**On First Launch:**
1. **Fetches real IP** from multiple detection APIs
2. **Gets location** via IP geolocation (no native GPS on desktop)
3. **Fetches VPNGate servers** from the API
4. **Loads settings** from electron-store (kill switch, stealth mode, etc.)

**Connection Behavior:**
- Attempts to configure **system proxy settings**
- Uses **WireGuard-style** tunnel configuration
- Enables **DNS leak protection** (routes DNS to secure servers)
- Enables **IPv6 leak protection** (disables IPv6 to prevent leaks)
- Supports **Multi-Hop** routing through multiple servers

**What You'll See:**
- System tray icon showing connection status
- Server list with African servers prioritized
- Multi-hop chain visualization
- Real-time privacy status updates

**⚠️ Requirements:** 
- Needs `node-fetch` and `electron-store` npm packages
- WireGuard tunneling requires actual WireGuard client installed on system
- Admin rights needed for full system tunnel

---

### 🧩 **Browser Extension** (Chrome/Firefox)

**On Install:**
1. **Requests permissions**: proxy, storage, webRequest, geolocation
2. **Detects real IP** using external APIs
3. **Gets location** via browser geolocation API (will prompt)
4. **Fetches VPNGate servers** in background

**Connection Behavior:**
- Configures **browser proxy** to route through selected server
- Only affects **browser traffic** (not system-wide)
- **WebRTC protection** via webRequest blocking
- **Auto-failover** if a server gets blocked

**What You'll See:**
- Popup with connect button and server selector
- Status showing current IP (real or masked)
- Health indicator (latency, connection quality)
- African servers listed first

**⚠️ Actual Functionality:**
- Proxy configuration WORKS - browser traffic will route through proxy
- But the proxy servers listed are **placeholder IPs** - they won't actually work as VPN endpoints
- To make it functional, you'd need real proxy/VPN server infrastructure

---

### 📱 **Android App** (Kotlin/Jetpack Compose)

**On First Launch:**
1. **Permission Dialog** appears asking for:
   - 📍 Location (Fine + Coarse)
   - 📁 Storage access
   - 🔔 Notifications
2. If granted, uses **FusedLocationProviderClient** for GPS accuracy
3. Falls back to IP-based location if denied

**Connection Behavior:**
- Uses Android **VpnService** API for system-wide tunneling
- Creates actual TUN interface for traffic routing
- Supports **Quick Settings Tile** for fast toggle
- Background service keeps VPN alive

**What You'll See:**
- Material 3 UI with dark theme
- Permission request screens
- Server list with distance-based sorting
- Connection statistics

**⚠️ Requirements:**
- VPN permission prompt from Android system
- Battery optimization exclusion recommended
- Actual VPN infrastructure needed for real tunneling

---

### 📊 **Summary Table**

| Feature | Web | Desktop | Extension | Android |
|---------|-----|---------|-----------|---------|
| **Real VPN Tunnel** | ❌ No | ⚠️ Partial | ⚠️ Browser only | ✅ Yes |
| **Location Permission** | ✅ Prompt | ❌ IP only | ✅ Prompt | ✅ Prompt |
| **Real Servers (VPNGate)** | ✅ Fetches | ✅ Fetches | ✅ Fetches | ⚠️ Needs integration |
| **Privacy Checks** | ✅ WebRTC | ✅ DNS/IPv6 | ✅ WebRTC | ✅ Full |
| **Works Offline** | ⚠️ Static servers | ⚠️ Static servers | ⚠️ Static servers | ⚠️ Static servers |

---

### 🚨 **Critical Reality Check**

The apps are **well-structured** but to actually bypass censorship in Uganda:

1. **You need real VPN servers** - The VPNGate servers are real, but they're public and often blocked
2. **You need your own infrastructure** - The placeholder IPs (197.232.x.x, etc.) don't actually run VPN services
3. **Android is closest to working** - It has the actual VpnService implementation
4. **Desktop needs WireGuard installed** - The tunneling code is configuration-only

The apps will **look functional** and provide a good UI experience, but actual traffic tunneling requires deploying real VPN server infrastructure.

---

## 🏗️ **Current Architecture vs Required Architecture**

```
CURRENT STATE (Simulation):
┌─────────────┐      ┌──────────────┐
│  Client App │ ───▶ │ Fake/No      │ ───▶ ❌ No actual tunneling
│  (UI only)  │      │ Server       │
└─────────────┘      └──────────────┘

REQUIRED STATE (Real VPN):
┌─────────────┐      ┌──────────────┐      ┌──────────────┐
│  Client App │ ═══▶ │ VPN Server   │ ═══▶ │ Internet     │
│  (Tunnel)   │      │ (Your infra) │      │ (Unblocked)  │
└─────────────┘      └──────────────┘      └──────────────┘
     ↑                     ↑
   Encrypted           Decrypted &
   Traffic             Forwarded
```

---

## 🖥️ **PART 1: Setting Up VPN Server Infrastructure**

### Option A: WireGuard Server (Recommended)

**Why WireGuard:**
- Fastest VPN protocol (uses ChaCha20-Poly1305)
- Simple configuration
- Small codebase (less attack surface)
- Works great on mobile

**Step 1: Get a VPS Outside Uganda**

Recommended locations:
| Provider | Location | Price | Best For |
|----------|----------|-------|----------|
| Vultr | Nairobi, Kenya | $5/mo | Lowest latency |
| DigitalOcean | Amsterdam | $6/mo | Privacy laws |
| Linode | Frankfurt | $5/mo | Speed |
| Hetzner | Finland | €4/mo | Cheapest |

**Step 2: Install WireGuard on Server**

```bash
# SSH into your VPS
ssh root@YOUR_SERVER_IP

# Ubuntu/Debian
apt update && apt install wireguard -y

# Generate server keys
wg genkey | tee /etc/wireguard/server_private.key | wg pubkey > /etc/wireguard/server_public.key
chmod 600 /etc/wireguard/server_private.key

# Get the keys
cat /etc/wireguard/server_private.key  # SERVER_PRIVATE_KEY
cat /etc/wireguard/server_public.key   # SERVER_PUBLIC_KEY
```

**Step 3: Configure WireGuard Server**

Create `/etc/wireguard/wg0.conf`:

```ini
[Interface]
Address = 10.0.0.1/24
ListenPort = 51820
PrivateKey = SERVER_PRIVATE_KEY
PostUp = iptables -A FORWARD -i wg0 -j ACCEPT; iptables -t nat -A POSTROUTING -o eth0 -j MASQUERADE
PostDown = iptables -D FORWARD -i wg0 -j ACCEPT; iptables -t nat -D POSTROUTING -o eth0 -j MASQUERADE
SaveConfig = true

# Client 1 (will be added dynamically)
# [Peer]
# PublicKey = CLIENT_PUBLIC_KEY
# AllowedIPs = 10.0.0.2/32
```

**Step 4: Enable IP Forwarding**

```bash
echo "net.ipv4.ip_forward=1" >> /etc/sysctl.conf
echo "net.ipv6.conf.all.forwarding=1" >> /etc/sysctl.conf
sysctl -p

# Start WireGuard
systemctl enable wg-quick@wg0
systemctl start wg-quick@wg0
```

**Step 5: Open Firewall**

```bash
ufw allow 51820/udp
ufw allow OpenSSH
ufw enable
```

---

### Option B: OpenVPN Server (More Compatible)

Better for restrictive networks that block WireGuard's UDP.

```bash
# Quick install script
wget https://git.io/vpn -O openvpn-install.sh
chmod +x openvpn-install.sh
./openvpn-install.sh

# Follow prompts, generates .ovpn client file
```

---

### Option C: Shadowsocks + V2Ray (For Heavy Censorship)

Best for evading DPI (Deep Packet Inspection).

```bash
# Install Shadowsocks
apt install shadowsocks-libev -y

# Configure /etc/shadowsocks-libev/config.json
{
    "server": "0.0.0.0",
    "server_port": 443,
    "password": "YOUR_STRONG_PASSWORD",
    "timeout": 300,
    "method": "chacha20-ietf-poly1305",
    "plugin": "v2ray-plugin",
    "plugin_opts": "server;tls;host=YOUR_DOMAIN.com"
}
```

---

## 📱 **PART 2: Making Android App Functional**

### Step 1: Add WireGuard Library

In `android/app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.wireguard.android:tunnel:1.0.20230706")
}
```

### Step 2: Create WireGuard Tunnel Service

Create `android/app/src/main/java/com/freedomvpn/tunnel/WireGuardTunnelService.kt`:

```kotlin
package com.freedomvpn.tunnel

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.wireguard.android.backend.GoBackend
import com.wireguard.config.Config
import com.wireguard.config.InetNetwork
import com.wireguard.config.Peer

class WireGuardTunnelService : VpnService() {
    
    private var tunnel: ParcelFileDescriptor? = null
    private lateinit var backend: GoBackend
    
    override fun onCreate() {
        super.onCreate()
        backend = GoBackend(this)
    }
    
    fun connect(serverConfig: ServerConfig): Boolean {
        // Build WireGuard config
        val config = Config.Builder()
            .setInterface(
                Interface.Builder()
                    .setPrivateKey(Key.fromBase64(serverConfig.clientPrivateKey))
                    .addAddress(InetNetwork.parse("10.0.0.2/32"))
                    .addDnsServer(InetAddress.getByName("1.1.1.1"))
                    .build()
            )
            .addPeer(
                Peer.Builder()
                    .setPublicKey(Key.fromBase64(serverConfig.serverPublicKey))
                    .setEndpoint(InetEndpoint.parse("${serverConfig.host}:51820"))
                    .addAllowedIp(InetNetwork.parse("0.0.0.0/0"))
                    .setPersistentKeepalive(25)
                    .build()
            )
            .build()
        
        // Establish tunnel
        val vpnInterface = Builder()
            .setSession("FreedomVPN")
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .setMtu(1280)
            .establish()
        
        tunnel = vpnInterface
        backend.setState(tunnel, config, "freedom-tunnel")
        
        return tunnel != null
    }
    
    fun disconnect() {
        tunnel?.close()
        tunnel = null
        backend.setState(null, null, null)
    }
}
```

### Step 3: Server Configuration API

Create an API endpoint on your server to provide connection configs:

```kotlin
// In your app, fetch config from your server
data class ServerConfig(
    val host: String,
    val port: Int = 51820,
    val serverPublicKey: String,
    val clientPrivateKey: String,  // Generated on client
    val clientPublicKey: String,   // Sent to server to register
    val assignedIP: String         // Server assigns this
)

suspend fun getServerConfig(serverId: String): ServerConfig {
    val clientKeyPair = generateWireGuardKeyPair()
    
    // Register client with server
    val response = api.registerClient(
        serverId = serverId,
        clientPublicKey = clientKeyPair.publicKey
    )
    
    return ServerConfig(
        host = response.serverHost,
        serverPublicKey = response.serverPublicKey,
        clientPrivateKey = clientKeyPair.privateKey,
        clientPublicKey = clientKeyPair.publicKey,
        assignedIP = response.assignedIP  // e.g., "10.0.0.5/32"
    )
}
```

---

## 🖥️ **PART 3: Making Windows Desktop Functional**

### Step 1: Install WireGuard for Windows

The app should check for and prompt WireGuard installation:

```javascript
// In windows/main.js
const { exec } = require('child_process');
const path = require('path');

async function checkWireGuardInstalled() {
  return new Promise((resolve) => {
    exec('where wireguard', (error) => {
      resolve(!error);
    });
  });
}

async function installWireGuard() {
  // Download WireGuard MSI installer
  const installerUrl = 'https://download.wireguard.com/windows-client/wireguard-installer.exe';
  // ... download and run installer
}
```

### Step 2: Generate and Apply WireGuard Config

```javascript
const fs = require('fs');
const { exec } = require('child_process');

async function connectToServer(serverConfig) {
  // Generate config file
  const configContent = `
[Interface]
PrivateKey = ${serverConfig.clientPrivateKey}
Address = ${serverConfig.assignedIP}
DNS = 1.1.1.1

[Peer]
PublicKey = ${serverConfig.serverPublicKey}
Endpoint = ${serverConfig.host}:51820
AllowedIPs = 0.0.0.0/0, ::/0
PersistentKeepalive = 25
`;

  // Write config
  const configPath = path.join(app.getPath('userData'), 'freedom.conf');
  fs.writeFileSync(configPath, configContent);
  
  // Apply config using WireGuard CLI
  return new Promise((resolve, reject) => {
    exec(`wireguard /installtunnelservice "${configPath}"`, (error) => {
      if (error) reject(error);
      else resolve(true);
    });
  });
}

async function disconnect() {
  return new Promise((resolve) => {
    exec('wireguard /uninstalltunnelservice freedom', () => resolve(true));
  });
}
```

---

## 🧩 **PART 4: Making Browser Extension Functional**

The extension **can only route browser traffic** via proxy. For real functionality:

### Step 1: Set Up HTTPS Proxy Server

On your VPS, install Squid or 3proxy:

```bash
# Install Squid
apt install squid -y

# Configure /etc/squid/squid.conf
http_port 3128
acl localnet src 0.0.0.0/0
http_access allow localnet
auth_param basic program /usr/lib/squid/basic_ncsa_auth /etc/squid/passwords
auth_param basic children 5
auth_param basic realm FreedomVPN
acl authenticated proxy_auth REQUIRED
http_access allow authenticated

# Create user
htpasswd -c /etc/squid/passwords vpnuser
```

### Step 2: Update Extension to Use Real Proxy

```javascript
// In extension/background.js
const REAL_PROXY_SERVERS = {
  'ke-nrb': {
    host: 'YOUR_KENYA_SERVER_IP',
    port: 3128,
    username: 'vpnuser',
    password: 'YOUR_PASSWORD',
    // ...
  }
};

async function setProxy(serverId) {
  const server = REAL_PROXY_SERVERS[serverId];
  
  const config = {
    mode: 'fixed_servers',
    rules: {
      singleProxy: {
        scheme: 'http',
        host: server.host,
        port: server.port
      },
      bypassList: ['localhost', '127.0.0.1']
    }
  };
  
  // Set proxy authentication
  chrome.webRequest.onAuthRequired.addListener(
    (details, callback) => {
      callback({
        authCredentials: {
          username: server.username,
          password: server.password
        }
      });
    },
    { urls: ['<all_urls>'] },
    ['asyncBlocking']
  );
  
  await chrome.proxy.settings.set({ value: config, scope: 'regular' });
}
```

---

## 🔐 **PART 5: Server-Side API for Client Management**

Create a simple API to manage client connections:

### Node.js Server API

```javascript
// server/api.js
const express = require('express');
const { exec } = require('child_process');
const crypto = require('crypto');

const app = express();
app.use(express.json());

const clients = new Map();
let nextClientIP = 2;

app.post('/api/register', (req, res) => {
  const { clientPublicKey } = req.body;
  
  // Assign IP to client
  const clientIP = `10.0.0.${nextClientIP++}/32`;
  
  // Add peer to WireGuard
  exec(`wg set wg0 peer ${clientPublicKey} allowed-ips ${clientIP}`, (error) => {
    if (error) {
      return res.status(500).json({ error: 'Failed to add peer' });
    }
    
    // Save config
    exec('wg-quick save wg0');
    
    clients.set(clientPublicKey, { ip: clientIP, connected: Date.now() });
    
    res.json({
      serverHost: 'YOUR_SERVER_IP',
      serverPublicKey: process.env.SERVER_PUBLIC_KEY,
      assignedIP: clientIP
    });
  });
});

app.post('/api/disconnect', (req, res) => {
  const { clientPublicKey } = req.body;
  
  exec(`wg set wg0 peer ${clientPublicKey} remove`, () => {
    clients.delete(clientPublicKey);
    res.json({ success: true });
  });
});

app.listen(8080);
```

---

## 🛡️ **PART 6: Anti-Censorship Techniques**

### For Uganda's UCC Blocks:

**1. Domain Fronting (Already in Code)**
```
Request goes to: cdnjs.cloudflare.com (allowed)
Host header says: your-vpn.pages.dev (your server)
```

**2. TLS Obfuscation (obfs4)**
```bash
# On server
apt install obfs4proxy
# Configure to wrap WireGuard/OpenVPN in obfs4
```

**3. Port 443 (HTTPS Port)**
```bash
# Run WireGuard on 443 instead of 51820
# Looks like normal HTTPS traffic
```

**4. WebSocket Tunnel**
```bash
# Wrap VPN traffic in WebSocket
# Install wstunnel on server
wstunnel --server wss://0.0.0.0:443
```

---

## 📋 **PART 7: Deployment Checklist**

### Server Setup
- [ ] VPS in Kenya/Rwanda/Netherlands (outside Uganda)
- [ ] WireGuard installed and configured
- [ ] Firewall configured (port 51820 or 443)
- [ ] IP forwarding enabled
- [ ] API server running for client registration

### Android App
- [ ] WireGuard library added
- [ ] VpnService implemented
- [ ] Key generation working
- [ ] API integration for server configs

### Windows App  
- [ ] WireGuard installation check
- [ ] Config file generation
- [ ] Tunnel service management
- [ ] Admin rights handling

### Browser Extension
- [ ] Real proxy server IPs configured
- [ ] Authentication handling
- [ ] Proxy settings applying correctly

### Testing
- [ ] Connection establishes
- [ ] Traffic routes through VPN (check IP)
- [ ] DNS queries not leaking
- [ ] WebRTC not leaking
- [ ] Works on blocked sites (Twitter, etc.)

---

## 💰 **Cost Estimate**

| Item | Monthly Cost |
|------|-------------|
| VPS (Kenya) | $5-10 |
| VPS (Europe backup) | $5 |
| Domain (optional) | $1 |
| **Total** | **$11-16/month** |

---

## 🚀 **Quick Start - Minimum Viable VPN**

If you want the fastest path to a working VPN:

1. **Get a $5 Vultr VPS in Nairobi**
2. **Run the WireGuard install script**
3. **Generate client config**
4. **Update Android app** with real server details
5. **Test on a blocked site**

---

## 📚 **Additional Resources**

- [WireGuard Official Docs](https://www.wireguard.com/quickstart/)
- [Android VpnService Guide](https://developer.android.com/guide/topics/connectivity/vpn)
- [Electron Security Best Practices](https://www.electronjs.org/docs/latest/tutorial/security)
- [Chrome Extension Proxy API](https://developer.chrome.com/docs/extensions/reference/proxy/)
