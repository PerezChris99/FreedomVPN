# FreedomVPN Feature Roadmap

Planned features grouped by impact. These are designed specifically for users in censored/shutdown environments.

---

## High Impact — Censorship Resistance

### 1. Decentralized Mesh Relay Network
Users can opt-in as relay nodes, creating a Tor-like onion routing layer. No central server to block. Traffic bounces through 3+ peers before exiting.

### 2. Domain Fronting / CDN Camouflage
Route VPN traffic through major CDN domains (Cloudflare, AWS CloudFront) so it looks like normal HTTPS to Google/Amazon. Nearly impossible to block without breaking the internet.

### 3. Steganographic Traffic
Hide VPN data inside normal-looking traffic: WebSocket streams disguised as video calls, HTTP/2 streams that look like browsing, or even embed data in image uploads.

### 4. Offline Mesh Chat (Bluetooth / Wi-Fi Direct)
When the internet is completely shut down, let nearby users chat over Bluetooth or Wi-Fi Direct P2P. Critical during full blackouts like Uganda Jan 2026.

### 5. Decoy Mode
When device is seized, show a fake "calculator app" or "weather app" UI. Real VPN/chat only accessible via secret gesture or PIN.

---

## Medium Impact — Privacy & Security

### 6. Encrypted File Sharing
Send E2EE files through the chat system. Auto-expire, auto-delete, no cloud storage.

### 7. Dead Man's Switch
If the user doesn't check in within X hours, auto-wipe all keys, messages, and VPN configs. For journalists/activists at risk.

### 8. Canary / Warrant Canary Page
A built-in transparency page showing "We have NOT received any government data requests" — updated cryptographically.

### 9. Plausible Deniability Storage
Hidden encrypted volume with a decoy password that reveals innocent data, and a real password for sensitive data (VeraCrypt-style).

### 10. Anonymous Crash Reports
Zero-knowledge telemetry using differential privacy so bugs get reported without identifying users.

---

## Medium Impact — Usability

### 11. Smart Protocol Switching
Automatically detect censorship type (DPI, DNS poisoning, IP blocking) and switch protocols in real-time (WireGuard → obfuscated TCP → domain fronting).

### 12. Built-in Tor Bridge
One-tap Tor access with pluggable transports (obfs4, Snowflake) for when VPN alone isn't enough.

### 13. Censorship Map / Status Dashboard
Crowd-sourced real-time map showing where internet is blocked/throttled across Africa and globally. Users anonymously report connectivity status.

### 14. Group Encrypted Chat / Channels
Extend the 1:1 chat to group messaging with Sender Keys (Signal Protocol style) for coordinating during shutdowns.

### 15. Voice Notes (Encrypted)
Record and send encrypted voice messages through the chat — useful where typing is impractical.

---

## Lower Priority — Polish

### 16. QR Code Contact Exchange
Scan QR codes in person to exchange Freedom IDs instead of copy-pasting public keys.

### 17. Multi-Device Sync
Link web + Android + desktop to same identity using a secure device-linking protocol.

### 18. Speed Test Built-in
Test VPN speed vs raw speed to help users pick optimal servers.

### 19. Custom DNS over HTTPS
Built-in encrypted DNS that bypasses DNS poisoning even without the VPN connected.

### 20. Panic Shake
Shake the phone 3 times to instantly disconnect VPN + wipe chat + show decoy screen.

---

## Priority Picks

The highest-value additions are **#1 (mesh relay)**, **#4 (offline Bluetooth chat)**, **#5 (decoy mode)**, and **#14 (group chat)** — these directly serve people in internet shutdown scenarios and don't exist in mainstream VPNs.
