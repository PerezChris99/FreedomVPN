"""
VPN Gate API Testing Script

Tests the VPN Gate server list API and validates:
1. API connectivity
2. CSV parsing
3. Server quality scoring
4. Best server selection

For Uganda and censored regions, this ensures:
- Reliable server discovery
- Optimal server selection for speed and stability
"""

import urllib.request
import csv
import base64
import time
from io import StringIO
from dataclasses import dataclass
from typing import List, Optional

# VPN Gate API URL
VPN_GATE_API_URL = "https://www.vpngate.net/api/iphone/"

# Backup URLs in case primary is blocked
BACKUP_URLS = [
    "https://www.vpngate.net/api/iphone/",
    "http://www.vpngate.net/api/iphone/",
]


@dataclass
class VpnGateServer:
    """VPN Gate server data"""
    hostname: str
    ip: str
    score: int
    ping: int
    speed: int
    country_long: str
    country_short: str
    num_sessions: int
    uptime: int
    total_users: int
    total_traffic: int
    log_type: str
    operator: str
    message: str
    openvpn_config_base64: str
    port: int = 443
    
    @property
    def speed_mbps(self) -> float:
        return self.speed / 1_000_000.0
    
    @property
    def quality_score(self) -> float:
        """Calculate quality score (higher is better)"""
        ping_score = 100.0 / self.ping if self.ping > 0 else 0
        speed_score = self.speed_mbps
        load_score = 100.0 / self.num_sessions if self.num_sessions > 0 else 100
        return ping_score * 0.3 + speed_score * 0.5 + load_score * 0.2
    
    def __str__(self) -> str:
        return f"{self.country_short} | {self.ip} | {self.speed_mbps:.1f} Mbps | {self.ping}ms | Score: {self.quality_score:.2f}"


def fetch_server_list(timeout: int = 30) -> str:
    """Fetch the VPN Gate server list"""
    errors = []
    
    for url in BACKUP_URLS:
        try:
            print(f"Trying {url}...")
            request = urllib.request.Request(
                url,
                headers={
                    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
                }
            )
            with urllib.request.urlopen(request, timeout=timeout) as response:
                data = response.read().decode('utf-8')
                print(f"✓ Successfully fetched from {url}")
                return data
        except Exception as e:
            errors.append(f"{url}: {e}")
            continue
    
    raise Exception(f"Failed to fetch server list. Errors: {errors}")


def parse_server_list(csv_data: str) -> List[VpnGateServer]:
    """Parse the CSV data into VpnGateServer objects"""
    servers = []
    
    # Skip header lines (starts with *)
    lines = csv_data.strip().split('\n')
    data_lines = [line for line in lines if not line.startswith('*')]
    
    # Join back and parse as CSV
    csv_content = '\n'.join(data_lines)
    reader = csv.reader(StringIO(csv_content))
    
    # Skip header row
    header = next(reader, None)
    if header:
        print(f"CSV Headers: {header}")
    
    for row in reader:
        try:
            if len(row) < 15:
                continue
            
            server = VpnGateServer(
                hostname=row[0],
                ip=row[1],
                score=int(row[2]) if row[2] else 0,
                ping=int(row[3]) if row[3] else 999,
                speed=int(row[4]) if row[4] else 0,
                country_long=row[5],
                country_short=row[6],
                num_sessions=int(row[7]) if row[7] else 0,
                uptime=int(row[8]) if row[8] else 0,
                total_users=int(row[9]) if row[9] else 0,
                total_traffic=int(row[10]) if row[10] else 0,
                log_type=row[11],
                operator=row[12],
                message=row[13],
                openvpn_config_base64=row[14] if len(row) > 14 else ""
            )
            servers.append(server)
        except (ValueError, IndexError) as e:
            # Skip malformed rows
            continue
    
    return servers


def select_best_servers(
    servers: List[VpnGateServer],
    count: int = 10,
    country_filter: Optional[str] = None,
    min_speed_mbps: float = 0
) -> List[VpnGateServer]:
    """Select the best servers based on quality score"""
    
    filtered = servers
    
    # Apply country filter
    if country_filter:
        filtered = [s for s in filtered if s.country_short.lower() == country_filter.lower()]
    
    # Apply speed filter
    filtered = [s for s in filtered if s.speed_mbps >= min_speed_mbps]
    
    # Sort by quality score (descending)
    sorted_servers = sorted(filtered, key=lambda s: s.quality_score, reverse=True)
    
    return sorted_servers[:count]


def test_openvpn_config(server: VpnGateServer) -> bool:
    """Validate that the OpenVPN config can be decoded"""
    try:
        config = base64.b64decode(server.openvpn_config_base64).decode('utf-8')
        # Check for essential OpenVPN config elements
        required = ['remote', 'dev', 'proto']
        return all(r in config.lower() for r in required)
    except Exception as e:
        print(f"Config decode error for {server.ip}: {e}")
        return False


def run_tests():
    """Run all VPN Gate tests"""
    print("=" * 60)
    print("VPN Gate API Test Suite")
    print("=" * 60)
    print()
    
    # Test 1: Fetch server list
    print("[Test 1] Fetching VPN Gate server list...")
    start_time = time.time()
    try:
        csv_data = fetch_server_list()
        fetch_time = time.time() - start_time
        print(f"✓ Fetched {len(csv_data)} bytes in {fetch_time:.2f}s")
    except Exception as e:
        print(f"✗ Failed to fetch: {e}")
        return False
    print()
    
    # Test 2: Parse server list
    print("[Test 2] Parsing server list...")
    try:
        servers = parse_server_list(csv_data)
        print(f"✓ Parsed {len(servers)} servers")
    except Exception as e:
        print(f"✗ Parse error: {e}")
        return False
    print()
    
    # Test 3: Server statistics
    print("[Test 3] Server Statistics:")
    countries = set(s.country_short for s in servers)
    avg_speed = sum(s.speed_mbps for s in servers) / len(servers) if servers else 0
    avg_ping = sum(s.ping for s in servers) / len(servers) if servers else 0
    print(f"  - Total servers: {len(servers)}")
    print(f"  - Countries: {len(countries)}")
    print(f"  - Average speed: {avg_speed:.1f} Mbps")
    print(f"  - Average ping: {avg_ping:.0f} ms")
    print()
    
    # Test 4: Best server selection
    print("[Test 4] Best Servers (Top 10):")
    best = select_best_servers(servers, count=10)
    for i, server in enumerate(best, 1):
        print(f"  {i}. {server}")
    print()
    
    # Test 5: Country-specific selection
    print("[Test 5] Best Servers in Japan:")
    jp_servers = select_best_servers(servers, count=5, country_filter='JP')
    for i, server in enumerate(jp_servers, 1):
        print(f"  {i}. {server}")
    print()
    
    # Test 6: High-speed servers
    print("[Test 6] High-Speed Servers (>50 Mbps):")
    fast_servers = select_best_servers(servers, count=5, min_speed_mbps=50)
    for i, server in enumerate(fast_servers, 1):
        print(f"  {i}. {server}")
    print()
    
    # Test 7: OpenVPN config validation
    print("[Test 7] Validating OpenVPN configs...")
    valid_count = sum(1 for s in servers[:20] if test_openvpn_config(s))
    print(f"  - Valid configs: {valid_count}/20 tested")
    print()
    
    # Test 8: Server for Uganda region optimization
    print("[Test 8] Recommended servers for Uganda (low latency priority):")
    # For Uganda, we want servers with good connectivity to Africa
    # European servers often have good African connectivity
    eu_servers = [s for s in servers if s.country_short in ['NL', 'DE', 'GB', 'FR', 'SE']]
    best_for_africa = sorted(eu_servers, key=lambda s: s.ping)[:5]
    for i, server in enumerate(best_for_africa, 1):
        print(f"  {i}. {server}")
    print()
    
    print("=" * 60)
    print("All tests completed!")
    print("=" * 60)
    return True


if __name__ == "__main__":
    success = run_tests()
    exit(0 if success else 1)
