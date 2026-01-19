"""
Server Selector Module

This module provides algorithms for selecting the best VPN server
based on various criteria like latency, speed, and load.
"""

import asyncio
import socket
import time
from typing import List, Optional, Callable
from dataclasses import dataclass
from enum import Enum

# Import from parser (when used as part of the package)
try:
    from .parser import VpnGateServer, VpnGateParser
except ImportError:
    from parser import VpnGateServer, VpnGateParser


class SortCriteria(Enum):
    """Criteria for sorting servers"""
    SPEED = "speed"
    PING = "ping"
    SCORE = "score"
    LOAD = "load"
    QUALITY = "quality"


class Region(Enum):
    """Geographic regions for filtering"""
    ALL = "all"
    ASIA = "asia"
    EUROPE = "europe"
    NORTH_AMERICA = "north_america"
    SOUTH_AMERICA = "south_america"
    OCEANIA = "oceania"
    AFRICA = "africa"


# Country codes by region
REGION_COUNTRIES = {
    Region.ASIA: ["JP", "KR", "TW", "HK", "SG", "TH", "VN", "ID", "MY", "PH", "IN", "CN"],
    Region.EUROPE: ["DE", "FR", "GB", "NL", "SE", "CH", "IT", "ES", "PL", "CZ", "AT", "BE", "DK", "FI", "NO", "PT", "RU", "UA"],
    Region.NORTH_AMERICA: ["US", "CA", "MX"],
    Region.SOUTH_AMERICA: ["BR", "AR", "CL", "CO", "PE", "VE"],
    Region.OCEANIA: ["AU", "NZ"],
    Region.AFRICA: ["ZA", "EG", "MA", "NG", "KE"],
}


@dataclass
class PingResult:
    """Result of a ping test"""
    server: VpnGateServer
    latency_ms: int
    success: bool


class ServerSelector:
    """
    Intelligent server selector that chooses the best VPN server
    based on multiple criteria.
    """
    
    def __init__(self, parser: Optional[VpnGateParser] = None):
        self.parser = parser or VpnGateParser()
        self._ping_cache: dict[str, PingResult] = {}
    
    def select_best(
        self,
        region: Region = Region.ALL,
        country_code: Optional[str] = None,
        min_speed_mbps: float = 0,
        max_ping_ms: int = 500,
        max_sessions: int = 100,
        test_ping: bool = False
    ) -> Optional[VpnGateServer]:
        """
        Select the best server based on criteria.
        
        Args:
            region: Geographic region filter
            country_code: Specific country code (overrides region)
            min_speed_mbps: Minimum speed in Mbps
            max_ping_ms: Maximum acceptable ping
            max_sessions: Maximum active sessions
            test_ping: Whether to perform actual ping tests
            
        Returns:
            Best matching server or None
        """
        servers = self._filter_servers(
            region=region,
            country_code=country_code,
            min_speed_mbps=min_speed_mbps,
            max_ping_ms=max_ping_ms,
            max_sessions=max_sessions
        )
        
        if not servers:
            return None
        
        if test_ping:
            # Ping top candidates and select best
            candidates = servers[:10]  # Test top 10
            ping_results = self._ping_servers(candidates)
            successful = [r for r in ping_results if r.success]
            if successful:
                successful.sort(key=lambda r: r.latency_ms)
                return successful[0].server
        
        # Return best by quality score
        return servers[0]
    
    def select_fastest(
        self,
        region: Region = Region.ALL,
        country_code: Optional[str] = None
    ) -> Optional[VpnGateServer]:
        """Select the fastest server by reported speed"""
        servers = self._filter_servers(region=region, country_code=country_code)
        if not servers:
            return None
        servers.sort(key=lambda s: s.speed, reverse=True)
        return servers[0]
    
    def select_lowest_latency(
        self,
        region: Region = Region.ALL,
        country_code: Optional[str] = None,
        test_ping: bool = True
    ) -> Optional[VpnGateServer]:
        """Select the server with lowest latency"""
        servers = self._filter_servers(region=region, country_code=country_code)
        if not servers:
            return None
        
        if test_ping:
            candidates = servers[:20]
            ping_results = self._ping_servers(candidates)
            successful = [r for r in ping_results if r.success]
            if successful:
                successful.sort(key=lambda r: r.latency_ms)
                return successful[0].server
        
        # Fall back to reported ping
        servers.sort(key=lambda s: s.ping)
        return servers[0]
    
    def select_least_loaded(
        self,
        region: Region = Region.ALL,
        country_code: Optional[str] = None
    ) -> Optional[VpnGateServer]:
        """Select the server with fewest active sessions"""
        servers = self._filter_servers(region=region, country_code=country_code)
        if not servers:
            return None
        servers.sort(key=lambda s: s.num_vpn_sessions)
        return servers[0]
    
    def get_sorted_servers(
        self,
        sort_by: SortCriteria = SortCriteria.QUALITY,
        region: Region = Region.ALL,
        country_code: Optional[str] = None,
        limit: int = 50
    ) -> List[VpnGateServer]:
        """
        Get servers sorted by specified criteria.
        
        Args:
            sort_by: Sorting criteria
            region: Geographic region filter
            country_code: Specific country code
            limit: Maximum number of servers to return
            
        Returns:
            Sorted list of servers
        """
        servers = self._filter_servers(region=region, country_code=country_code)
        
        if sort_by == SortCriteria.SPEED:
            servers.sort(key=lambda s: s.speed, reverse=True)
        elif sort_by == SortCriteria.PING:
            servers.sort(key=lambda s: s.ping)
        elif sort_by == SortCriteria.SCORE:
            servers.sort(key=lambda s: s.score, reverse=True)
        elif sort_by == SortCriteria.LOAD:
            servers.sort(key=lambda s: s.num_vpn_sessions)
        else:  # QUALITY
            servers.sort(key=lambda s: s.quality_score, reverse=True)
        
        return servers[:limit]
    
    def _filter_servers(
        self,
        region: Region = Region.ALL,
        country_code: Optional[str] = None,
        min_speed_mbps: float = 0,
        max_ping_ms: int = 999,
        max_sessions: int = 999
    ) -> List[VpnGateServer]:
        """Filter servers based on criteria"""
        servers = self.parser.fetch_servers()
        
        # Filter by country or region
        if country_code:
            servers = [s for s in servers if s.country_short.upper() == country_code.upper()]
        elif region != Region.ALL:
            region_codes = REGION_COUNTRIES.get(region, [])
            servers = [s for s in servers if s.country_short.upper() in region_codes]
        
        # Apply additional filters
        servers = [
            s for s in servers
            if s.speed_mbps >= min_speed_mbps
            and s.ping <= max_ping_ms
            and s.num_vpn_sessions <= max_sessions
        ]
        
        return servers
    
    def _ping_servers(self, servers: List[VpnGateServer]) -> List[PingResult]:
        """Ping multiple servers and return results"""
        results = []
        
        for server in servers:
            # Check cache
            if server.ip in self._ping_cache:
                results.append(self._ping_cache[server.ip])
                continue
            
            # Perform ping
            latency = self._ping_host(server.ip)
            result = PingResult(
                server=server,
                latency_ms=latency if latency > 0 else 999,
                success=latency > 0
            )
            
            self._ping_cache[server.ip] = result
            results.append(result)
        
        return results
    
    def _ping_host(self, host: str, port: int = 443, timeout: float = 3.0) -> int:
        """
        Ping a host using TCP connection.
        Returns latency in ms or -1 on failure.
        """
        try:
            start = time.time()
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(timeout)
            sock.connect((host, port))
            sock.close()
            return int((time.time() - start) * 1000)
        except Exception:
            return -1
    
    def clear_ping_cache(self):
        """Clear the ping cache"""
        self._ping_cache.clear()


# Async version for better performance
class AsyncServerSelector(ServerSelector):
    """Async version of ServerSelector for concurrent ping tests"""
    
    async def ping_servers_async(self, servers: List[VpnGateServer]) -> List[PingResult]:
        """Ping multiple servers concurrently"""
        tasks = [self._ping_host_async(s) for s in servers]
        return await asyncio.gather(*tasks)
    
    async def _ping_host_async(self, server: VpnGateServer, port: int = 443, timeout: float = 3.0) -> PingResult:
        """Async ping using asyncio"""
        try:
            start = time.time()
            reader, writer = await asyncio.wait_for(
                asyncio.open_connection(server.ip, port),
                timeout=timeout
            )
            writer.close()
            await writer.wait_closed()
            latency = int((time.time() - start) * 1000)
            return PingResult(server=server, latency_ms=latency, success=True)
        except Exception:
            return PingResult(server=server, latency_ms=999, success=False)
    
    async def select_best_async(
        self,
        region: Region = Region.ALL,
        country_code: Optional[str] = None,
        top_n: int = 10
    ) -> Optional[VpnGateServer]:
        """Select best server with async ping tests"""
        servers = self._filter_servers(region=region, country_code=country_code)
        if not servers:
            return None
        
        # Test top candidates concurrently
        candidates = servers[:top_n]
        results = await self.ping_servers_async(candidates)
        
        successful = [r for r in results if r.success]
        if successful:
            successful.sort(key=lambda r: r.latency_ms)
            return successful[0].server
        
        return servers[0]


# Example usage
if __name__ == "__main__":
    selector = ServerSelector()
    
    print("=== Server Selector Demo ===\n")
    
    # Best overall server
    best = selector.select_best()
    if best:
        print(f"Best Server: {best.country_flag} {best.country_long}")
        print(f"  Host: {best.hostname}")
        print(f"  Speed: {best.formatted_speed}")
        print(f"  Ping: {best.ping}ms")
        print()
    
    # Best server in Japan
    jp_best = selector.select_best(country_code="JP")
    if jp_best:
        print(f"Best Japan Server: {jp_best.hostname}")
        print(f"  Speed: {jp_best.formatted_speed}")
        print()
    
    # Fastest server
    fastest = selector.select_fastest()
    if fastest:
        print(f"Fastest Server: {fastest.country_flag} {fastest.hostname}")
        print(f"  Speed: {fastest.formatted_speed}")
        print()
    
    # Servers by region
    print("Top 5 servers in Europe:")
    for server in selector.get_sorted_servers(region=Region.EUROPE, limit=5):
        print(f"  {server.country_flag} {server.hostname} - {server.formatted_speed}")
    
    # Async example
    async def async_demo():
        async_selector = AsyncServerSelector()
        best = await async_selector.select_best_async(region=Region.ASIA)
        if best:
            print(f"\nBest Asia Server (async): {best.country_flag} {best.hostname}")
    
    print("\nRunning async test...")
    asyncio.run(async_demo())
