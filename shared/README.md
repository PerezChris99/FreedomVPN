# FreedomVPN Shared Components

This directory contains reference implementations for common VPN functionality
that can be used for testing and as guides for the platform-specific implementations.

## Components

### VPN Gate Parser (`vpngate/parser.py`)
Fetches and parses the VPN Gate public server list.

```python
from vpngate.parser import VpnGateParser

parser = VpnGateParser()
servers = parser.fetch_servers()

for server in servers[:5]:
    print(f"{server.country_flag} {server.country_long}: {server.formatted_speed}")
```

### Server Selector (`vpngate/server_selector.py`)
Intelligent server selection with ping testing and filtering.

```python
from vpngate.server_selector import ServerSelector, Region

selector = ServerSelector()

# Get best overall server
best = selector.select_best()

# Get fastest server in Europe
fastest = selector.select_fastest(region=Region.EUROPE)

# Get server with lowest latency (with actual ping tests)
lowest_ping = selector.select_lowest_latency(test_ping=True)
```

### WireGuard Config Generator (`wireguard/config_generator.py`)
Generate WireGuard configuration files.

```python
from wireguard.config_generator import WireGuardConfigGenerator

generator = WireGuardConfigGenerator()
config = generator.generate_client_config(
    server_public_key="...",
    server_endpoint="vpn.example.com:51820"
)
config.save("wg0.conf")
```

## Requirements

```
requests>=2.28.0
```

## Notes

- These are reference implementations in Python
- The Android (Kotlin) and Windows (C#) apps have their own implementations
- Use these for testing and understanding the logic
