using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;

namespace FreedomVPN.Uwp.WireGuard;

/// <summary>
/// WireGuard Configuration Generator and Parser
/// 
/// Generates WireGuard configuration for Windows clients.
/// For Uganda and censored regions, WireGuard is preferred because:
/// 1. Faster connection establishment (1-RTT)
/// 2. Lower overhead (less identifiable traffic patterns)
/// 3. Better performance on unstable networks
/// 4. Smaller code footprint
/// </summary>
public class WireGuardConfig
{
    // DNS servers for privacy and censorship bypass
    private static readonly string[] DnsServers = {
        "8.8.8.8",          // Google Primary
        "8.8.4.4",          // Google Secondary
        "1.1.1.1",          // Cloudflare Primary
        "1.0.0.1",          // Cloudflare Secondary
        "9.9.9.9",          // Quad9
        "208.67.222.222"    // OpenDNS
    };

    /// <summary>
    /// Interface configuration
    /// </summary>
    public class InterfaceConfig
    {
        public string PrivateKey { get; set; } = "";
        public string Address { get; set; } = "10.0.0.2/32";
        public string[] DNS { get; set; } = DnsServers;
        public int? ListenPort { get; set; }
        public int MTU { get; set; } = 1280;
    }

    /// <summary>
    /// Peer (server) configuration
    /// </summary>
    public class PeerConfig
    {
        public string PublicKey { get; set; } = "";
        public string? PreSharedKey { get; set; }
        public string Endpoint { get; set; } = "";
        public string[] AllowedIPs { get; set; } = { "0.0.0.0/0", "::/0" };
        public int PersistentKeepalive { get; set; } = 25;
    }

    public InterfaceConfig Interface { get; set; } = new();
    public List<PeerConfig> Peers { get; set; } = new();

    /// <summary>
    /// Generate a new WireGuard key pair
    /// </summary>
    public static (string PublicKey, string PrivateKey) GenerateKeyPair()
    {
        // Generate 32 random bytes for private key
        var privateKeyBytes = new byte[32];
        using var rng = RandomNumberGenerator.Create();
        rng.GetBytes(privateKeyBytes);

        // Clamp private key (WireGuard requirement)
        privateKeyBytes[0] &= 248;
        privateKeyBytes[31] &= 127;
        privateKeyBytes[31] |= 64;

        var privateKey = Convert.ToBase64String(privateKeyBytes);

        // Derive public key using Curve25519
        // Note: In production, use a proper Curve25519 implementation
        // For now, we'll use a placeholder - actual implementation would use
        // a library like NSec or BouncyCastle
        var publicKeyBytes = ComputePublicKey(privateKeyBytes);
        var publicKey = Convert.ToBase64String(publicKeyBytes);

        return (publicKey, privateKey);
    }

    /// <summary>
    /// Compute public key from private key using Curve25519
    /// Note: This is a simplified implementation. Use a proper crypto library in production.
    /// </summary>
    private static byte[] ComputePublicKey(byte[] privateKey)
    {
        // In production, use NSec or BouncyCastle for Curve25519
        // This is a placeholder that returns a dummy key
        // The actual implementation would be:
        // return Curve25519.ScalarMultBase(privateKey);
        
        var publicKey = new byte[32];
        using var sha256 = SHA256.Create();
        var hash = sha256.ComputeHash(privateKey);
        Array.Copy(hash, publicKey, 32);
        return publicKey;
    }

    /// <summary>
    /// Create configuration from server details
    /// </summary>
    public static WireGuardConfig Create(
        string serverPublicKey,
        string serverEndpoint,
        int serverPort = 51820,
        string? clientPrivateKey = null)
    {
        string privateKey;
        if (string.IsNullOrEmpty(clientPrivateKey))
        {
            var keys = GenerateKeyPair();
            privateKey = keys.PrivateKey;
        }
        else
        {
            privateKey = clientPrivateKey;
        }

        return new WireGuardConfig
        {
            Interface = new InterfaceConfig
            {
                PrivateKey = privateKey,
                Address = "10.0.0.2/32",
                DNS = DnsServers.Take(3).ToArray(),
                MTU = 1280
            },
            Peers = new List<PeerConfig>
            {
                new PeerConfig
                {
                    PublicKey = serverPublicKey,
                    Endpoint = $"{serverEndpoint}:{serverPort}",
                    AllowedIPs = new[] { "0.0.0.0/0", "::/0" },
                    PersistentKeepalive = 25
                }
            }
        };
    }

    /// <summary>
    /// Export configuration to WireGuard INI format
    /// </summary>
    public string ToConfigString()
    {
        var sb = new StringBuilder();

        // Interface section
        sb.AppendLine("[Interface]");
        sb.AppendLine($"PrivateKey = {Interface.PrivateKey}");
        sb.AppendLine($"Address = {Interface.Address}");
        sb.AppendLine($"DNS = {string.Join(", ", Interface.DNS)}");
        if (Interface.ListenPort.HasValue)
            sb.AppendLine($"ListenPort = {Interface.ListenPort}");
        sb.AppendLine($"MTU = {Interface.MTU}");
        sb.AppendLine();

        // Peer sections
        foreach (var peer in Peers)
        {
            sb.AppendLine("[Peer]");
            sb.AppendLine($"PublicKey = {peer.PublicKey}");
            if (!string.IsNullOrEmpty(peer.PreSharedKey))
                sb.AppendLine($"PresharedKey = {peer.PreSharedKey}");
            sb.AppendLine($"Endpoint = {peer.Endpoint}");
            sb.AppendLine($"AllowedIPs = {string.Join(", ", peer.AllowedIPs)}");
            sb.AppendLine($"PersistentKeepalive = {peer.PersistentKeepalive}");
            sb.AppendLine();
        }

        return sb.ToString();
    }

    /// <summary>
    /// Parse configuration from WireGuard INI format
    /// </summary>
    public static WireGuardConfig Parse(string configString)
    {
        var config = new WireGuardConfig();
        var lines = configString.Split('\n', StringSplitOptions.RemoveEmptyEntries);
        
        string? currentSection = null;
        PeerConfig? currentPeer = null;

        foreach (var rawLine in lines)
        {
            var line = rawLine.Trim();
            if (string.IsNullOrEmpty(line) || line.StartsWith("#"))
                continue;

            if (line == "[Interface]")
            {
                currentSection = "interface";
                continue;
            }
            if (line == "[Peer]")
            {
                currentSection = "peer";
                currentPeer = new PeerConfig();
                config.Peers.Add(currentPeer);
                continue;
            }

            var parts = line.Split('=', 2);
            if (parts.Length != 2) continue;

            var key = parts[0].Trim().ToLower();
            var value = parts[1].Trim();

            if (currentSection == "interface")
            {
                switch (key)
                {
                    case "privatekey":
                        config.Interface.PrivateKey = value;
                        break;
                    case "address":
                        config.Interface.Address = value;
                        break;
                    case "dns":
                        config.Interface.DNS = value.Split(',').Select(s => s.Trim()).ToArray();
                        break;
                    case "listenport":
                        config.Interface.ListenPort = int.Parse(value);
                        break;
                    case "mtu":
                        config.Interface.MTU = int.Parse(value);
                        break;
                }
            }
            else if (currentSection == "peer" && currentPeer != null)
            {
                switch (key)
                {
                    case "publickey":
                        currentPeer.PublicKey = value;
                        break;
                    case "presharedkey":
                        currentPeer.PreSharedKey = value;
                        break;
                    case "endpoint":
                        currentPeer.Endpoint = value;
                        break;
                    case "allowedips":
                        currentPeer.AllowedIPs = value.Split(',').Select(s => s.Trim()).ToArray();
                        break;
                    case "persistentkeepalive":
                        currentPeer.PersistentKeepalive = int.Parse(value);
                        break;
                }
            }
        }

        return config;
    }

    /// <summary>
    /// Get alternative ports for bypassing firewalls
    /// </summary>
    public static int[] GetAlternativePorts() => new[] { 443, 80, 53, 1194, 4500 };
}
