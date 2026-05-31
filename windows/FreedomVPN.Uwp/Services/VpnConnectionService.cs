using FreedomVPN.Uwp.Models;
using FreedomVPN.Uwp.WireGuard;
using Windows.Networking.Vpn;

namespace FreedomVPN.Uwp.Services;

/// <summary>
/// Service for managing VPN connections on Windows
/// 
/// Supports:
/// - WireGuard protocol (preferred for speed and security)
/// - Windows native VPN (IKEv2 fallback)
/// 
/// For Uganda and censored regions:
/// - Automatic protocol fallback if one is blocked
/// - Alternative port support
/// - Quick reconnection on network changes
/// </summary>
public class VpnConnectionService
{
    private const string VpnProfileName = "FreedomVPN";
    
    private VpnManagementAgent? _vpnAgent;
    private VpnGateServer? _currentServer;
    private WireGuardTunnel? _wireGuardTunnel;
    private bool _isConnected;
    private bool _useWireGuard = true;

    // Events
    public event EventHandler<ConnectionState>? StateChanged;
    public event EventHandler<ConnectionStats>? StatsUpdated;

    public enum ConnectionState
    {
        Disconnected,
        Connecting,
        Connected,
        Disconnecting,
        Error
    }

    public class ConnectionStats
    {
        public long BytesIn { get; set; }
        public long BytesOut { get; set; }
        public TimeSpan Duration { get; set; }
        public long SpeedIn { get; set; }
        public long SpeedOut { get; set; }
    }

    public ConnectionState CurrentState { get; private set; } = ConnectionState.Disconnected;

    public VpnConnectionService()
    {
        _vpnAgent = new VpnManagementAgent();
    }

    /// <summary>
    /// Connect using WireGuard protocol
    /// </summary>
    public async Task ConnectWireGuardAsync(
        string serverPublicKey,
        string serverEndpoint,
        int serverPort = 51820)
    {
        try
        {
            SetState(ConnectionState.Connecting);

            _wireGuardTunnel = new WireGuardTunnel();
            _wireGuardTunnel.StateChanged += (s, state) =>
            {
                CurrentState = state switch
                {
                    WireGuardTunnel.TunnelState.Connected => ConnectionState.Connected,
                    WireGuardTunnel.TunnelState.Connecting => ConnectionState.Connecting,
                    WireGuardTunnel.TunnelState.Disconnecting => ConnectionState.Disconnecting,
                    WireGuardTunnel.TunnelState.Error => ConnectionState.Error,
                    _ => ConnectionState.Disconnected
                };
                StateChanged?.Invoke(this, CurrentState);
            };

            _wireGuardTunnel.StatsUpdated += (s, stats) =>
            {
                StatsUpdated?.Invoke(this, new ConnectionStats
                {
                    BytesIn = stats.BytesIn,
                    BytesOut = stats.BytesOut,
                    Duration = stats.Duration,
                    SpeedIn = stats.SpeedIn,
                    SpeedOut = stats.SpeedOut
                });
            };

            var success = await _wireGuardTunnel.ConnectAsync(
                serverPublicKey,
                serverEndpoint,
                serverPort);

            if (success)
            {
                _isConnected = true;
                _useWireGuard = true;
                SetState(ConnectionState.Connected);
            }
            else
            {
                throw new Exception("WireGuard connection failed");
            }
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"WireGuard connection error: {ex.Message}");
            SetState(ConnectionState.Error);
            throw;
        }
    }

    /// <summary>
    /// Connect to a VPN server using best available protocol
    /// </summary>
    public async Task ConnectAsync(VpnGateServer server)
    {
        _currentServer = server;
        SetState(ConnectionState.Connecting);

        try
        {
            // Try WireGuard first if server supports it
            if (WireGuardTunnel.IsWireGuardInstalled() && !string.IsNullOrEmpty(server.WireGuardPublicKey))
            {
                try
                {
                    await ConnectWireGuardAsync(server.WireGuardPublicKey, server.Ip, 51820);
                    return;
                }
                catch
                {
                    System.Diagnostics.Debug.WriteLine("WireGuard failed, falling back to native VPN");
                }
            }

            // Fallback to Windows native VPN
            await ConnectNativeAsync(server);
        }
        catch (Exception ex)
        {
            SetState(ConnectionState.Error);
            throw;
        }
    }

    /// <summary>
    /// Connect using Windows native VPN
    /// </summary>
    private async Task ConnectNativeAsync(VpnGateServer server)
    {
        // Create or update VPN profile
        await CreateOrUpdateProfileAsync(server);
        
        // Connect using the profile
        var profile = await GetProfileAsync();
        if (profile != null)
        {
            var result = await _vpnAgent!.ConnectProfileAsync(profile);
            
            if (result.Status != VpnManagementConnectionStatus.Connected)
            {
                throw new Exception($"Connection failed: {result.Status}");
            }
        }
        
        _isConnected = true;
        _useWireGuard = false;
        SetState(ConnectionState.Connected);
    }

    /// <summary>
    /// Disconnect from VPN
    /// </summary>
    public async Task DisconnectAsync()
    {
        SetState(ConnectionState.Disconnecting);

        try
        {
            if (_useWireGuard && _wireGuardTunnel != null)
            {
                await _wireGuardTunnel.DisconnectAsync();
                _wireGuardTunnel.Dispose();
                _wireGuardTunnel = null;
            }
            else
            {
                var profile = await GetProfileAsync();
                if (profile != null)
                {
                    await _vpnAgent!.DisconnectProfileAsync(profile);
                }
            }
            
            _isConnected = false;
            _currentServer = null;
            SetState(ConnectionState.Disconnected);
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"Disconnect error: {ex.Message}");
            SetState(ConnectionState.Error);
        }
    }

    /// <summary>
    /// Check if currently connected
    /// </summary>
    public bool IsConnected => _isConnected;

    /// <summary>
    /// Get current server
    /// </summary>
    public VpnGateServer? CurrentServer => _currentServer;

    /// <summary>
    /// Check if using WireGuard
    /// </summary>
    public bool IsUsingWireGuard => _useWireGuard;

    /// <summary>
    /// Check if WireGuard is available
    /// </summary>
    public static bool IsWireGuardAvailable() => WireGuardTunnel.IsWireGuardInstalled();

    /// <summary>
    /// Get current connection stats
    /// </summary>
    public async Task<ConnectionStats?> GetStatsAsync()
    {
        if (_useWireGuard && _wireGuardTunnel != null)
        {
            var stats = await _wireGuardTunnel.GetStatsAsync();
            if (stats != null)
            {
                return new ConnectionStats
                {
                    BytesIn = stats.BytesIn,
                    BytesOut = stats.BytesOut,
                    Duration = stats.Duration,
                    SpeedIn = stats.SpeedIn,
                    SpeedOut = stats.SpeedOut
                };
            }
        }
        return null;
    }

    private void SetState(ConnectionState state)
    {
        CurrentState = state;
        StateChanged?.Invoke(this, state);
    }

    /// <summary>
    /// Create or update VPN profile
    /// </summary>
    private async Task CreateOrUpdateProfileAsync(VpnGateServer server)
    {
        // Remove existing profile if exists
        var existingProfile = await GetProfileAsync();
        if (existingProfile != null)
        {
            await _vpnAgent!.DeleteProfileAsync(existingProfile);
        }

        // Create new profile
        var profile = new VpnNativeProfile
        {
            ProfileName = VpnProfileName,
            NativeProtocolType = VpnNativeProtocolType.IpsecIkev2,
            UserAuthenticationMethod = VpnAuthenticationMethod.Eap,
            AlwaysOn = false,
            RememberCredentials = true
        };

        // Add the server
        profile.Servers.Add(server.Ip);

        // Add the profile to Windows
        var result = await _vpnAgent!.AddProfileFromObjectAsync(profile);
        
        if (result != VpnManagementErrorStatus.Ok)
        {
            throw new Exception($"Failed to create VPN profile: {result}");
        }
    }

    /// <summary>
    /// Get existing VPN profile
    /// </summary>
    private async Task<IVpnProfile?> GetProfileAsync()
    {
        var profiles = await _vpnAgent!.GetProfilesAsync();
        return profiles.FirstOrDefault(p => p.ProfileName == VpnProfileName);
    }

    /// <summary>
    /// Get connection status
    /// </summary>
    public async Task<VpnManagementConnectionStatus> GetConnectionStatusAsync()
    {
        var profile = await GetProfileAsync();
        if (profile == null)
        {
            return VpnManagementConnectionStatus.Disconnected;
        }

        return profile.ConnectionStatus;
    }
}
