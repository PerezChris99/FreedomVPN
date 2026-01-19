"""
VPN Gate Server List Parser

This module fetches and parses the VPN Gate server list.
Use this as a reference implementation for parsing the CSV format.

VPN Gate API: https://www.vpngate.net/api/iphone/

CSV Format:
HostName,IP,Score,Ping,Speed,CountryLong,CountryShort,NumVpnSessions,
Uptime,TotalUsers,TotalTraffic,LogType,Operator,Message,OpenVPN_ConfigData_Base64
"""

import csv
import base64
import requests
from dataclasses import dataclass
from typing import List, Optional
from io import StringIO


@dataclass
class VpnGateServer:
    """Represents a VPN Gate server"""
    hostname: str
    ip: str
    score: int
    ping: int  # milliseconds
    speed: int  # bits per second
    country_long: str
    country_short: str
    num_vpn_sessions: int
    uptime: int  # milliseconds
    total_users: int
    total_traffic: int
    log_type: str
    operator: str
    message: str
    openvpn_config_base64: str

    @property
    def speed_mbps(self) -> float:
        """Get speed in Mbps"""
        return self.speed / 1_000_000

    @property
    def formatted_speed(self) -> str:
        """Get formatted speed string"""
        if self.speed >= 1_000_000_000:
            return f"{self.speed / 1_000_000_000:.1f} Gbps"
        elif self.speed >= 1_000_000:
            return f"{self.speed / 1_000_000:.1f} Mbps"
        elif self.speed >= 1_000:
            return f"{self.speed / 1_000:.1f} Kbps"
        else:
            return f"{self.speed} bps"

    @property
    def uptime_hours(self) -> float:
        """Get uptime in hours"""
        return self.uptime / 1000 / 60 / 60

    @property
    def quality_score(self) -> float:
        """
        Calculate a quality score based on ping, speed, and sessions.
        Higher is better.
        """
        ping_score = 100.0 / self.ping if self.ping > 0 else 0
        speed_score = self.speed_mbps
        load_score = 100.0 / self.num_vpn_sessions if self.num_vpn_sessions > 0 else 100
        return ping_score * 0.3 + speed_score * 0.5 + load_score * 0.2

    @property
    def has_openvpn_config(self) -> bool:
        """Check if this server has a valid OpenVPN config"""
        return bool(self.openvpn_config_base64)

    @property
    def country_flag(self) -> str:
        """Get country flag emoji"""
        if len(self.country_short) != 2:
            return "🌐"
        first = ord(self.country_short[0]) - ord('A') + 0x1F1E6
        second = ord(self.country_short[1]) - ord('A') + 0x1F1E6
        return chr(first) + chr(second)

    def get_openvpn_config(self) -> Optional[str]:
        """Decode and return the OpenVPN configuration"""
        if not self.openvpn_config_base64:
            return None
        try:
            return base64.b64decode(self.openvpn_config_base64).decode('utf-8')
        except Exception:
            return None


class VpnGateParser:
    """Parser for VPN Gate server list"""
    
    API_URL = "https://www.vpngate.net/api/iphone/"
    MIRROR_URL = "http://www.vpngate.net/api/iphone/"
    
    def __init__(self):
        self._cached_servers: List[VpnGateServer] = []
        self._cache_time = None
        self._cache_duration = 300  # 5 minutes
    
    def fetch_servers(self, force_refresh: bool = False) -> List[VpnGateServer]:
        """
        Fetch and parse the VPN Gate server list.
        
        Args:
            force_refresh: Force refresh even if cache is valid
            
        Returns:
            List of VpnGateServer objects
        """
        import time
        
        # Check cache
        if not force_refresh and self._cached_servers:
            if self._cache_time and time.time() - self._cache_time < self._cache_duration:
                return self._cached_servers
        
        # Fetch from API
        try:
            response = requests.get(self.API_URL, timeout=30)
            response.raise_for_status()
            csv_data = response.text
        except Exception:
            # Try mirror
            response = requests.get(self.MIRROR_URL, timeout=30)
            response.raise_for_status()
            csv_data = response.text
        
        # Parse CSV
        servers = self._parse_csv(csv_data)
        
        # Filter and sort
        servers = [
            s for s in servers 
            if s.has_openvpn_config and 0 < s.ping < 1000
        ]
        servers.sort(key=lambda s: s.quality_score, reverse=True)
        
        # Cache
        self._cached_servers = servers
        self._cache_time = time.time()
        
        return servers
    
    def _parse_csv(self, csv_data: str) -> List[VpnGateServer]:
        """Parse CSV data into server list"""
        servers = []
        
        # Remove header lines (start with * or #)
        lines = []
        for line in csv_data.split('\n'):
            if not line.startswith('*') and not line.startswith('#') and line.strip():
                lines.append(line)
        
        # Parse CSV
        reader = csv.reader(StringIO('\n'.join(lines)))
        
        for row in reader:
            if len(row) < 15:
                continue
            
            try:
                server = VpnGateServer(
                    hostname=row[0],
                    ip=row[1],
                    score=int(row[2]) if row[2] else 0,
                    ping=int(row[3]) if row[3] else 999,
                    speed=int(row[4]) if row[4] else 0,
                    country_long=row[5],
                    country_short=row[6],
                    num_vpn_sessions=int(row[7]) if row[7] else 0,
                    uptime=int(row[8]) if row[8] else 0,
                    total_users=int(row[9]) if row[9] else 0,
                    total_traffic=int(row[10]) if row[10] else 0,
                    log_type=row[11],
                    operator=row[12],
                    message=row[13],
                    openvpn_config_base64=row[14] if len(row) > 14 else ""
                )
                servers.append(server)
            except (ValueError, IndexError):
                continue
        
        return servers
    
    def get_best_server(self) -> Optional[VpnGateServer]:
        """Get the best server based on quality score"""
        servers = self.fetch_servers()
        return servers[0] if servers else None
    
    def get_servers_by_country(self, country_code: str) -> List[VpnGateServer]:
        """Get servers filtered by country code"""
        servers = self.fetch_servers()
        return [s for s in servers if s.country_short.upper() == country_code.upper()]
    
    def get_available_countries(self) -> List[tuple]:
        """Get list of available countries"""
        servers = self.fetch_servers()
        countries = set((s.country_short, s.country_long) for s in servers)
        return sorted(countries, key=lambda x: x[1])


# Example usage
if __name__ == "__main__":
    parser = VpnGateParser()
    
    print("Fetching VPN Gate servers...")
    servers = parser.fetch_servers()
    
    print(f"\nFound {len(servers)} servers\n")
    
    print("Top 10 servers by quality:")
    print("-" * 80)
    
    for i, server in enumerate(servers[:10], 1):
        print(f"{i}. {server.country_flag} {server.country_long}")
        print(f"   Host: {server.hostname}")
        print(f"   IP: {server.ip}")
        print(f"   Speed: {server.formatted_speed}")
        print(f"   Ping: {server.ping}ms")
        print(f"   Sessions: {server.num_vpn_sessions}")
        print(f"   Quality Score: {server.quality_score:.2f}")
        print()
    
    print("\nAvailable countries:")
    for code, name in parser.get_available_countries():
        count = len(parser.get_servers_by_country(code))
        print(f"  {code}: {name} ({count} servers)")
