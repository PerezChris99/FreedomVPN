using FreedomVPN.Uwp.Models;
using Windows.Networking.Vpn;

namespace FreedomVPN.Uwp.Services;

/// <summary>
/// Service for managing VPN connections on Windows
/// Uses Windows.Networking.Vpn APIs for native Windows VPN integration
/// </summary>
public class VpnConnectionService
{
    private const string VpnProfileName = "FreedomVPN";
    
    private VpnManagementAgent? _vpnAgent;
    private VpnGateServer? _currentServer;
    private bool _isConnected;

    public VpnConnectionService()
    {
        _vpnAgent = new VpnManagementAgent();
    }

    /// <summary>
    /// Connect to a VPN server
    /// </summary>
    public async Task ConnectAsync(VpnGateServer server)
    {
        _currentServer = server;
        
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
    }

    /// <summary>
    /// Disconnect from VPN
    /// </summary>
    public async Task DisconnectAsync()
    {
        var profile = await GetProfileAsync();
        if (profile != null)
        {
            await _vpnAgent!.DisconnectProfileAsync(profile);
        }
        
        _isConnected = false;
        _currentServer = null;
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
        // Note: For a full implementation, you would:
        // 1. Use VpnPlugInProfile for custom protocols like WireGuard
        // 2. Or use VpnNativeProfile for built-in Windows protocols
        
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
