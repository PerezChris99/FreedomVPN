"""
WireGuard Configuration Generator

This module helps generate WireGuard configuration files from
VPN Gate OpenVPN configs or from direct WireGuard parameters.

Note: VPN Gate primarily provides OpenVPN configs. To use WireGuard,
you would need to either:
1. Set up your own WireGuard servers
2. Find a service that provides WireGuard configs
3. Use a protocol translator (limited compatibility)
"""

import base64
import os
import secrets
from dataclasses import dataclass, field
from typing import List, Optional
from datetime import datetime


@dataclass
class WireGuardPeer:
    """WireGuard peer (server) configuration"""
    public_key: str
    endpoint: str  # host:port
    allowed_ips: List[str] = field(default_factory=lambda: ["0.0.0.0/0", "::/0"])
    persistent_keepalive: int = 25  # seconds
    preshared_key: Optional[str] = None


@dataclass
class WireGuardConfig:
    """WireGuard interface configuration"""
    private_key: str
    address: List[str] = field(default_factory=lambda: ["10.0.0.2/32"])
    dns: List[str] = field(default_factory=lambda: ["8.8.8.8", "8.8.4.4"])
    mtu: int = 1280
    peers: List[WireGuardPeer] = field(default_factory=list)
    
    @property
    def public_key(self) -> str:
        """
        Generate public key from private key.
        Note: In a real implementation, you would use the actual
        Curve25519 key derivation. This is a placeholder.
        """
        # This would require proper crypto implementation
        # For now, return a placeholder
        return base64.b64encode(b"placeholder_public_key_32b").decode()
    
    def to_config_string(self) -> str:
        """Generate WireGuard config file content"""
        lines = [
            "[Interface]",
            f"PrivateKey = {self.private_key}",
            f"Address = {', '.join(self.address)}",
            f"DNS = {', '.join(self.dns)}",
            f"MTU = {self.mtu}",
        ]
        
        for peer in self.peers:
            lines.append("")
            lines.append("[Peer]")
            lines.append(f"PublicKey = {peer.public_key}")
            if peer.preshared_key:
                lines.append(f"PresharedKey = {peer.preshared_key}")
            lines.append(f"Endpoint = {peer.endpoint}")
            lines.append(f"AllowedIPs = {', '.join(peer.allowed_ips)}")
            lines.append(f"PersistentKeepalive = {peer.persistent_keepalive}")
        
        return "\n".join(lines)
    
    def save(self, filepath: str):
        """Save config to file"""
        with open(filepath, 'w') as f:
            f.write(self.to_config_string())
    
    @classmethod
    def generate_keypair(cls) -> tuple:
        """
        Generate a WireGuard keypair.
        Returns (private_key, public_key) as base64 strings.
        
        Note: This is a simplified placeholder. Real implementation
        would use Curve25519 key generation.
        """
        # Generate 32 random bytes for private key
        private_bytes = secrets.token_bytes(32)
        private_key = base64.b64encode(private_bytes).decode()
        
        # In real implementation, derive public key using Curve25519
        # For now, return a placeholder
        public_key = base64.b64encode(b"placeholder_pubkey_32bytes!!").decode()
        
        return private_key, public_key


class WireGuardConfigGenerator:
    """
    Generator for WireGuard configurations.
    Can create configs for various scenarios.
    """
    
    def __init__(self):
        self.generated_configs: List[WireGuardConfig] = []
    
    def generate_client_config(
        self,
        server_public_key: str,
        server_endpoint: str,
        client_private_key: Optional[str] = None,
        client_address: str = "10.0.0.2/32",
        dns_servers: Optional[List[str]] = None,
        allowed_ips: Optional[List[str]] = None,
        preshared_key: Optional[str] = None
    ) -> WireGuardConfig:
        """
        Generate a client configuration for connecting to a WireGuard server.
        
        Args:
            server_public_key: Server's public key
            server_endpoint: Server endpoint (host:port)
            client_private_key: Client's private key (generated if not provided)
            client_address: Client's VPN IP address
            dns_servers: DNS servers to use
            allowed_ips: IPs to route through VPN
            preshared_key: Optional preshared key for extra security
            
        Returns:
            WireGuardConfig object
        """
        if client_private_key is None:
            client_private_key, _ = WireGuardConfig.generate_keypair()
        
        peer = WireGuardPeer(
            public_key=server_public_key,
            endpoint=server_endpoint,
            allowed_ips=allowed_ips or ["0.0.0.0/0", "::/0"],
            preshared_key=preshared_key
        )
        
        config = WireGuardConfig(
            private_key=client_private_key,
            address=[client_address],
            dns=dns_servers or ["8.8.8.8", "8.8.4.4"],
            peers=[peer]
        )
        
        self.generated_configs.append(config)
        return config
    
    def generate_split_tunnel_config(
        self,
        server_public_key: str,
        server_endpoint: str,
        routes: List[str],  # Only these IPs go through VPN
        client_private_key: Optional[str] = None,
        client_address: str = "10.0.0.2/32"
    ) -> WireGuardConfig:
        """
        Generate a split-tunnel configuration.
        Only specified routes go through the VPN.
        """
        return self.generate_client_config(
            server_public_key=server_public_key,
            server_endpoint=server_endpoint,
            client_private_key=client_private_key,
            client_address=client_address,
            allowed_ips=routes
        )
    
    def generate_server_config(
        self,
        server_private_key: str,
        server_address: str,
        listen_port: int = 51820,
        clients: Optional[List[dict]] = None
    ) -> str:
        """
        Generate a server configuration.
        
        Args:
            server_private_key: Server's private key
            server_address: Server's VPN IP address
            listen_port: Port to listen on
            clients: List of client configs with 'public_key' and 'allowed_ips'
            
        Returns:
            Config file content as string
        """
        lines = [
            "[Interface]",
            f"PrivateKey = {server_private_key}",
            f"Address = {server_address}",
            f"ListenPort = {listen_port}",
            "",
            "# Enable IP forwarding",
            "PostUp = iptables -A FORWARD -i %i -j ACCEPT; iptables -t nat -A POSTROUTING -o eth0 -j MASQUERADE",
            "PostDown = iptables -D FORWARD -i %i -j ACCEPT; iptables -t nat -D POSTROUTING -o eth0 -j MASQUERADE",
        ]
        
        if clients:
            for client in clients:
                lines.extend([
                    "",
                    "[Peer]",
                    f"PublicKey = {client['public_key']}",
                    f"AllowedIPs = {client.get('allowed_ips', '10.0.0.2/32')}",
                ])
                if 'preshared_key' in client:
                    lines.append(f"PresharedKey = {client['preshared_key']}")
        
        return "\n".join(lines)


# Example configurations for testing
EXAMPLE_CONFIGS = {
    "full_tunnel": {
        "description": "Route all traffic through VPN",
        "allowed_ips": ["0.0.0.0/0", "::/0"]
    },
    "ipv4_only": {
        "description": "Route only IPv4 traffic through VPN",
        "allowed_ips": ["0.0.0.0/0"]
    },
    "lan_bypass": {
        "description": "VPN for all except local network",
        "allowed_ips": [
            "0.0.0.0/1",
            "128.0.0.0/1",
            # Excludes 192.168.0.0/16, 10.0.0.0/8, 172.16.0.0/12
        ]
    }
}


if __name__ == "__main__":
    print("=== WireGuard Config Generator Demo ===\n")
    
    generator = WireGuardConfigGenerator()
    
    # Generate example client config
    config = generator.generate_client_config(
        server_public_key="SERVER_PUBLIC_KEY_BASE64_HERE",
        server_endpoint="vpn.example.com:51820",
        client_address="10.0.0.2/32",
        dns_servers=["1.1.1.1", "1.0.0.1"]
    )
    
    print("Generated Client Config:")
    print("-" * 40)
    print(config.to_config_string())
    print()
    
    # Generate split tunnel config
    split_config = generator.generate_split_tunnel_config(
        server_public_key="SERVER_PUBLIC_KEY_BASE64_HERE",
        server_endpoint="vpn.example.com:51820",
        routes=["10.10.0.0/16", "192.168.100.0/24"],  # Only these go through VPN
        client_address="10.0.0.3/32"
    )
    
    print("\nSplit Tunnel Config:")
    print("-" * 40)
    print(split_config.to_config_string())
    print()
    
    # Generate keypair
    private, public = WireGuardConfig.generate_keypair()
    print(f"\nGenerated Keypair:")
    print(f"Private Key: {private}")
    print(f"Public Key: {public}")
