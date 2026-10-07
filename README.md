# FreedomVPN

FreedomVPN is a cross-platform, privacy-focused VPN and censorship-resilience project focused on reliable access to the open internet on restricted or unreliable networks.

> Project status: active engineering / pre-production. Client and server software are being hardened for real-world deployment. Infrastructure, server fleet capacity, distribution, and live network measurements are deployment-dependent.

## What FreedomVPN is

The repository contains:

- Android client — Kotlin, Jetpack Compose, Android VpnService, WireGuard integration, leak-protection and resilience components.
- Windows client — Electron/JavaScript desktop client with system-wide WireGuard integration, connection health monitoring, and privacy controls.
- Browser extension — Chrome/Chromium Manifest V3 extension for browser-level proxy and server-selection functionality.
- Server/API — Node.js/Express peer-management API for WireGuard public-key registration, address allocation, peer lifecycle, and health metadata.
- Shared modules — server discovery, censorship-resilience helpers, statistics and WireGuard utilities.
- Tests and documentation — automated regression tests and engineering documentation.

## Engineering goals

### Privacy and security

- WireGuard-based encrypted tunnels where supported.
- Client private keys remain client-side.
- Protected key storage on supported desktop paths.
- Android certificate pinning for the configured production API.
- DNS, IPv6, WebRTC and kill-switch protections where implemented by the selected client.
- Fail-closed connection state: configuration or process startup alone is never treated as proof of VPN protection.

### Censorship resilience

The codebase contains mechanisms and research-oriented components for:

- transport and port fallback;
- TLS/traffic-obfuscation concepts;
- alternate proxy/transport paths;
- server discovery and failover;
- low-bandwidth connection optimization;
- geographically diverse server selection.

The presence of a mechanism in source code is not proof that it defeats every ISP, DPI system, blocklist, or national shutdown. Effectiveness must be validated against the target network and deployment.

### Server management

The server API can:

- register WireGuard public keys;
- allocate client addresses from a configured pool;
- add/remove peers from the live WireGuard interface;
- expose server health;
- expire inactive peers;
- maintain privacy-reduced security audit events.

The current server implementation is a control-plane component. Large-scale deployment requires additional fleet management, database, capacity, observability, redundancy and network infrastructure.

## What is not claimed

FreedomVPN does not claim to provide:

- guaranteed anonymity or untraceability;
- guaranteed censorship bypass;
- guaranteed availability during an internet shutdown;
- a fixed global server fleet merely because locations appear in client code;
- national-scale capacity without corresponding infrastructure;
- protection against every endpoint, browser, operating-system or application-level privacy leak;
- proof of security based solely on static or structural tests.

VPN technology can reduce exposure to local networks and conceal a user's public IP from destinations, but it cannot make a user universally invisible or remove all endpoint and operational risks.

## Architecture

~~~
FreedomVPN clients
  Android / Windows / Browser
             |
             v
       Control plane
  Peer registration / health
             |
             v
       WireGuard data plane
       VPN gateway / relay
             |
             v
          Internet
~~~

The control plane should not sit in the packet path. At scale, VPN gateways should be independently deployable and capable of carrying traffic without depending on the API for every packet.

## Repository layout

~~~
FreedomVPN/
├── android/       # Android client
├── windows/       # Windows desktop client
├── extension/     # Browser extension
├── server/        # VPN peer-management API
├── shared/        # Shared JavaScript components
├── tests/         # Automated regression suites
├── docs/          # Engineering documentation
└── web/           # Web-facing assets
~~~

## Building

### Android

Requirements:

- Android Studio
- JDK 17
- Android SDK 34
- Gradle wrapper included in the repository

Debug builds use a development endpoint by default.

Production/release builds intentionally require deployment configuration:

~~~
cd android
./gradlew assembleRelease -PserverBaseUrl=https://vpn.example.com -PserverCertPins=sha256/<leaf-pin>,sha256/<backup-pin>
~~~

Do not commit production private keys, signing credentials, API secrets or deployment-only certificate material.

### Windows

Requirements:

- Node.js 18+
- npm
- Windows with the required VPN/WireGuard components for real system-wide tunneling

~~~
cd windows
npm ci
npm start
~~~

The Windows client must not report a protected connection unless the tunnel has actually been established and locally verified.

### Server API

~~~
cd server/api
npm ci
npm test
~~~

The API requires deployment configuration such as ADMIN_TOKEN, WG_IFACE, SERVER_PUBLIC_KEY and SERVER_ENDPOINT.

## Testing

The repository contains multiple classes of tests. Structural/static tests are useful for regression prevention, but they are not equivalent to live VPN verification.

Production validation should include:

1. deterministic unit/API tests;
2. Android compile and instrumentation validation;
3. Windows tunnel establishment on a real Windows host;
4. real DNS/IPv6/WebRTC leak checks;
5. real WireGuard handshake and traffic tests;
6. reconnect/failover testing;
7. censorship/transport measurements on target networks;
8. capacity and load testing;
9. security scanning and dependency review.

A green structural test suite must not be interpreted as proof that a VPN tunnel works on every network.

## Production readiness

Remaining deployment-dependent work includes:

- production VPN gateway fleet and geographic/provider diversity;
- server provisioning and orchestration;
- production DNS, domains and certificates;
- Android release signing;
- Windows code signing and installer distribution;
- resilient update and distribution channels;
- centralized observability and alerting;
- real-world ISP/DPI testing;
- capacity/load testing;
- backup, disaster recovery and incident response;
- privacy/legal review and operational policies.

These are infrastructure and operational requirements, not reasons to fabricate functionality in client code.

## Security model

FreedomVPN follows defense in depth:

- fail-closed connection state;
- least-privilege interfaces where practical;
- private-key protection;
- HTTPS and certificate pinning for production Android API communication;
- server-side validation and rate limiting;
- peer lifecycle management;
- privacy-reduced audit logging;
- explicit distinction between configured, attempted and verified connectivity.

Security-sensitive changes should include regression tests and must not rely on UI state as evidence that network protection exists.

## Contributing

Contributions are welcome through the project's normal development process.

Please:

1. Fork or create an authorized development branch.
2. Make a focused change.
3. Add or update regression tests.
4. Document security or deployment implications.
5. Use clear, descriptive commit messages.
6. Open a pull request for review.

For security-sensitive issues, use the designated security-reporting process rather than publishing an exploitable vulnerability in a public issue.

## Copyright and project rights

**© 2026 Kweezi Perez Christopher. All rights reserved.**

FreedomVPN, its original source code, documentation, branding, artwork and associated original materials are protected by applicable copyright law.

Contributions are welcome through the project's contribution and review process. Unless a separate written agreement or project license states otherwise, submitting a contribution does not by itself grant permission to redistribute the project outside the terms established by the copyright holder.

Licensing can vary by component and package metadata. The project name, branding, documentation and original materials remain protected by copyright; review any component-specific license before redistribution or commercial use. Do not assume unrestricted relicensing rights for the repository as a whole.

Third-party components remain subject to their respective licenses and terms.

## Responsible use

FreedomVPN is intended for legitimate privacy, security, research, accessibility and censorship-resilience use cases. Users are responsible for complying with applicable laws and with the terms of networks and services they use.

## Roadmap

Engineering priorities include:

- complete fail-closed behavior across all clients;
- production-grade server configuration and lifecycle management;
- verified multi-hop relays rather than simulated hop state;
- stronger CI/CD and release validation;
- real network/censorship measurements;
- scalable gateway orchestration;
- resilient distribution and update mechanisms;
- observability without unnecessary browsing-history collection.

---

FreedomVPN — engineering for resilient, private access to the open internet.
