# FreedomVPN Chrome/Edge Extension

A browser extension that actually tunnels your browser traffic through proxy servers, changing your IP address for all browser requests.

## ✅ What This Extension Does

Unlike the web app (which is just a UI demo), this extension:
- **Actually changes your browser's IP** using Chrome's Proxy API
- Routes ALL browser traffic through the selected proxy server
- Works on Chrome, Edge, Brave, and other Chromium browsers

## 🚀 Installation

### Step 1: Open Extensions Page
1. Open Chrome/Edge
2. Go to `chrome://extensions/` (or `edge://extensions/`)
3. Enable **Developer mode** (toggle in top-right corner)

### Step 2: Load the Extension
1. Click **"Load unpacked"**
2. Navigate to: `D:\NEW PROJECTS\FreedomVPN\extension`
3. Select the folder and click "Open"

### Step 3: Pin the Extension
1. Click the puzzle piece icon in Chrome toolbar
2. Find "FreedomVPN" and click the pin icon

## 🎯 How to Use

1. Click the FreedomVPN icon in your browser toolbar
2. Select a server from the list (African servers prioritized)
3. Click the **Connect** button
4. Your IP will change to the selected server's location!

## ✅ Verify It's Working

After connecting:
1. Go to https://whatismyipaddress.com/
2. Or visit https://ipapi.co/
3. You should see the proxy server's IP, not your real IP

## 🌍 Available Servers

### Africa (Priority)
- 🇺🇬 Uganda (Kampala)
- 🇰🇪 Kenya (Nairobi)
- 🇿🇦 South Africa (Johannesburg)
- 🇳🇬 Nigeria (Lagos)
- 🇪🇬 Egypt (Cairo)

### Europe
- 🇳🇱 Netherlands (Amsterdam)
- 🇩🇪 Germany (Frankfurt)
- 🇬🇧 United Kingdom (London)
- 🇫🇷 France (Paris)

### Americas
- 🇺🇸 United States (New York)
- 🇧🇷 Brazil (São Paulo)

### Asia
- 🇸🇬 Singapore
- 🇯🇵 Japan (Tokyo)
- 🇦🇪 UAE (Dubai)

## ⚙️ Features

- **8 Languages**: English, Kiswahili, Luganda, Français, አማርኛ, العربية, Português, Hausa
- **Data Compression**: Save bandwidth
- **Stealth Mode**: Privacy features
- **Auto-Connect**: Connect on browser start
- **Money Saved**: Track UGX savings

## ⚠️ Important Notes

1. **Proxy Servers**: The included servers are for demonstration. For production use, you should:
   - Set up your own proxy servers
   - Use a VPN service with proxy endpoints
   - Configure your own SOCKS5/HTTP proxies

2. **WebRTC Leaks**: For complete protection, also disable WebRTC:
   - Install a WebRTC blocker extension
   - Or go to `chrome://flags/#disable-webrtc` and disable it

3. **DNS Leaks**: The proxy handles HTTP traffic, but DNS might still leak. Consider using:
   - DNS over HTTPS (DoH)
   - A DNS blocker extension

## 🔧 Configuring Your Own Proxy Servers

Edit `background.js` and update the `PROXY_SERVERS` object:

```javascript
const PROXY_SERVERS = {
  'my-server': { 
    host: 'your.proxy.server.com', 
    port: 8080, 
    country: 'Your Country', 
    city: 'Your City', 
    flag: '🏳️' 
  },
  // Add more servers...
};
```

## 🆚 Comparison: Extension vs Web App

| Feature | Web App | Extension |
|---------|---------|-----------|
| UI Demo | ✅ | ✅ |
| Actually tunnels traffic | ❌ | ✅ |
| Changes your IP | ❌ | ✅ |
| Works offline | ❌ | ✅ |
| Access to Proxy API | ❌ | ✅ |

## 📱 Other Platforms

- **Android**: Use the Android APK (full VPN with WireGuard)
- **Windows Desktop**: Coming soon (Electron + WireGuard)
- **Web Demo**: Available at localhost:3000 (UI only)

---

**Built for Uganda 🇺🇬 | Digital Freedom for Africa**
