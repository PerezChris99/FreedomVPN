# VPN Gate Integration Guide

This document explains how FreedomVPN integrates with VPN Gate, a volunteer-run
public VPN relay server project.

## What is VPN Gate?

VPN Gate is an academic research project at the University of Tsukuba, Japan.
It provides a list of free VPN servers operated by volunteers worldwide.

- **Website**: https://www.vpngate.net/
- **Project Type**: Academic/Research
- **Cost**: Free
- **Servers**: Thousands worldwide (volunteer-operated)

## API Endpoint

### Primary URL
```
https://www.vpngate.net/api/iphone/
```

### Mirror URL (HTTP fallback)
```
http://www.vpngate.net/api/iphone/
```

## Response Format

The API returns a CSV file with the following structure:

### Header Lines
Lines starting with `*` or `#` are comments/headers and should be skipped.

### Data Columns

| Index | Column Name | Description | Example |
|-------|-------------|-------------|---------|
| 0 | HostName | Server hostname | vpn123456789 |
| 1 | IP | Server IP address | 203.0.113.50 |
| 2 | Score | Popularity score | 587234 |
| 3 | Ping | Latency in ms | 25 |
| 4 | Speed | Speed in bps | 45678901 |
| 5 | CountryLong | Full country name | Japan |
| 6 | CountryShort | ISO country code | JP |
| 7 | NumVpnSessions | Active sessions | 15 |
| 8 | Uptime | Uptime in ms | 123456789 |
| 9 | TotalUsers | Total historical users | 5000 |
| 10 | TotalTraffic | Total traffic (bytes) | 123456789012 |
| 11 | LogType | Logging policy | 2 week log |
| 12 | Operator | Operator name | VPN Gate User |
| 13 | Message | Operator message | Welcome! |
| 14 | OpenVPN_ConfigData_Base64 | Base64 OpenVPN config | ... |

## Parsing Example

### Python
```python
import requests
import csv
from io import StringIO

def fetch_servers():
    response = requests.get("https://www.vpngate.net/api/iphone/")
    lines = [l for l in response.text.split('\n') 
             if not l.startswith('*') and not l.startswith('#') and l.strip()]
    
    servers = []
    for row in csv.reader(StringIO('\n'.join(lines))):
        if len(row) >= 15:
            servers.append({
                'hostname': row[0],
                'ip': row[1],
                'speed': int(row[4]) if row[4] else 0,
                'country': row[5],
                'ping': int(row[3]) if row[3] else 999,
            })
    return servers
```

### Kotlin (Android)
```kotlin
suspend fun fetchServers(): List<VpnGateServer> {
    val response = httpClient.get("https://www.vpngate.net/api/iphone/")
    
    return response.body?.string()?.lines()
        ?.filter { !it.startsWith("*") && !it.startsWith("#") && it.isNotBlank() }
        ?.mapNotNull { VpnGateServer.fromCsvLine(it) }
        ?: emptyList()
}
```

### C# (Windows)
```csharp
public async Task<List<VpnGateServer>> FetchServersAsync()
{
    var response = await _httpClient.GetStringAsync(API_URL);
    
    return response.Split('\n')
        .Where(l => !l.StartsWith("*") && !l.StartsWith("#") && !string.IsNullOrWhiteSpace(l))
        .Select(VpnGateServer.FromCsvLine)
        .Where(s => s != null)
        .ToList();
}
```

## OpenVPN Configuration

The `OpenVPN_ConfigData_Base64` field contains a complete OpenVPN configuration
file encoded in Base64.

### Decoding
```python
import base64

config = base64.b64decode(server['openvpn_config_base64']).decode('utf-8')
```

### Sample Decoded Config
```
###############################################################################
# VPN Gate Public VPN Relay Server Config
###############################################################################

client
dev tun
proto udp
remote xxx.xxx.xxx.xxx 1194
cipher AES-128-CBC
auth SHA1
resolv-retry infinite
nobind
persist-key
persist-tun
remote-cert-tls server

<ca>
-----BEGIN CERTIFICATE-----
...
-----END CERTIFICATE-----
</ca>

<cert>
-----BEGIN CERTIFICATE-----
...
-----END CERTIFICATE-----
</cert>

<key>
-----BEGIN RSA PRIVATE KEY-----
...
-----END RSA PRIVATE KEY-----
</key>
```

## Connection Credentials

VPN Gate uses universal credentials for OpenVPN connections:

- **Username**: `vpn`
- **Password**: `vpn`

## Server Selection Algorithm

### Quality Score Calculation

We calculate a quality score to rank servers:

```python
def quality_score(server):
    # Lower ping is better (weight: 30%)
    ping_score = 100.0 / server.ping if server.ping > 0 else 0
    
    # Higher speed is better (weight: 50%)
    speed_score = server.speed / 1_000_000  # Convert to Mbps
    
    # Fewer sessions is better (weight: 20%)
    load_score = 100.0 / server.num_sessions if server.num_sessions > 0 else 100
    
    return ping_score * 0.3 + speed_score * 0.5 + load_score * 0.2
```

### Filtering Criteria

Before scoring, filter out unreliable servers:

```python
valid_servers = [s for s in servers if 
    s.has_openvpn_config and
    0 < s.ping < 1000 and      # Has valid ping
    s.speed > 1_000_000 and    # At least 1 Mbps
    s.uptime > 3600000         # At least 1 hour uptime
]
```

## Important Considerations

### 1. Variable Performance

VPN Gate servers are run by volunteers with varying:
- Internet connection speeds
- Hardware capabilities  
- Geographic locations
- Availability schedules

**Recommendation**: Implement server health checking and automatic failover.

### 2. No Guarantees

Servers can:
- Disappear without warning
- Have reduced capacity during peak hours
- Be temporarily offline

**Recommendation**: Cache the server list and refresh periodically (every 5 minutes).

### 3. Logging Policies

The `LogType` field indicates the operator's logging policy:
- Some operators may keep connection logs
- Logging policies are self-reported and not verified

**Recommendation**: Display logging information to users so they can make informed choices.

### 4. Protocol Support

VPN Gate primarily supports:
- OpenVPN (UDP/TCP)
- L2TP/IPsec
- SSTP
- SoftEther VPN

WireGuard is **not** natively supported. For WireGuard:
- Use your own WireGuard servers
- Use a commercial WireGuard VPN service
- Implement protocol translation (complex)

## Rate Limiting

The VPN Gate API may rate limit aggressive requests:
- Cache responses for at least 5 minutes
- Use exponential backoff on errors
- Include a proper User-Agent header

```python
headers = {
    'User-Agent': 'FreedomVPN/1.0 (Android)'
}
```

## Error Handling

### Network Errors
```python
try:
    response = requests.get(PRIMARY_URL, timeout=30)
except Exception:
    # Try mirror
    response = requests.get(MIRROR_URL, timeout=30)
```

### Parse Errors
```python
try:
    server = VpnGateServer.from_csv_line(line)
except (ValueError, IndexError):
    # Skip malformed lines
    continue
```

## Caching Strategy

```python
class VpnGateCache:
    def __init__(self, ttl_seconds=300):
        self.servers = []
        self.last_fetch = 0
        self.ttl = ttl_seconds
    
    def get_servers(self, force_refresh=False):
        if force_refresh or self._is_stale():
            self.servers = self._fetch_from_api()
            self.last_fetch = time.time()
        return self.servers
    
    def _is_stale(self):
        return time.time() - self.last_fetch > self.ttl
```

## Alternative to VPN Gate

If you need more reliable servers or WireGuard support:

1. **Self-hosted servers**: Deploy your own VPN servers on cloud providers
2. **Commercial APIs**: Partner with VPN providers that offer API access
3. **Hybrid approach**: Use VPN Gate as fallback, with premium servers as primary

## Legal Considerations

- VPN Gate is legal in most countries, but VPN usage may be restricted in some regions
- Users are responsible for compliance with local laws
- The service is provided "as-is" without warranties
- Respect the terms of service of VPN Gate and individual server operators
