# Windows UWP VPN Development Setup Guide

This guide covers setting up the Windows development environment for FreedomVPN.

## Prerequisites

- **Visual Studio 2022** with UWP development workload
- **Windows 10 SDK** (10.0.19041.0 or higher)
- **.NET 6.0** or higher
- **Windows 10/11** for testing (VPN APIs require actual Windows)

## Project Setup

### 1. Open Solution

```powershell
cd FreedomVPN\windows
start FreedomVPN.sln
```

### 2. Configure Developer Mode

VPN apps require Developer Mode on Windows:
1. Settings → Update & Security → For developers
2. Enable "Developer mode"

### 3. Trust the Package

For local testing, you'll need to trust the self-signed certificate or create a trusted one.

## Windows VPN Architecture

### Overview

Windows provides two approaches for VPN plugins:

1. **VPN Plugin (UWP)**: Uses `Windows.Networking.Vpn` namespace
2. **Native VPN**: Traditional VPN using `RasClient` or WireGuard-NT

### VPN Plugin Components

```
├── VpnBackgroundTask.cs    # Handles VPN channel operations
├── VpnPluginProcessor.cs   # Packet processing logic
├── VpnConnectionService.cs # High-level connection management
```

## VpnManagementAgent

The main class for managing VPN profiles:

```csharp
public class VpnConnectionService
{
    private VpnManagementAgent _vpnAgent = new VpnManagementAgent();
    
    public async Task ConnectAsync(VpnGateServer server)
    {
        // Create profile
        var profile = new VpnNativeProfile
        {
            ProfileName = "FreedomVPN",
            NativeProtocolType = VpnNativeProtocolType.IpsecIkev2,
            AlwaysOn = false
        };
        profile.Servers.Add(server.Ip);
        
        // Add and connect
        await _vpnAgent.AddProfileFromObjectAsync(profile);
        await _vpnAgent.ConnectProfileAsync(profile);
    }
}
```

## VPN Plugin Background Task

For custom protocols like WireGuard, implement a background task:

```csharp
public sealed class VpnBackgroundTask : IBackgroundTask
{
    public void Run(IBackgroundTaskInstance taskInstance)
    {
        if (taskInstance.TriggerDetails is VpnChannel channel)
        {
            // Configure routing
            var routeScope = new VpnRouteAssignment();
            routeScope.Ipv4InclusionRoutes.Add(
                new VpnRoute(new HostName("0.0.0.0"), 0));
            
            // Start the channel
            channel.Start(
                mainOuterTunnelTransport: transport,
                requestedAssignment: routeScope,
                mtuSize: 1280
            );
            
            // Process packets
            ProcessPackets(channel);
        }
    }
}
```

## Package Manifest Configuration

Required capabilities in `Package.appxmanifest`:

```xml
<Capabilities>
    <Capability Name="internetClient" />
    <Capability Name="internetClientServer" />
    <Capability Name="privateNetworkClientServer" />
    
    <!-- VPN capability - REQUIRED -->
    <rescap:Capability Name="networkingVpnProvider" />
</Capabilities>
```

VPN Plugin extension:

```xml
<Extensions>
    <uap:Extension Category="windows.vpnPlugIn">
        <uap:VpnPlugIn 
            ServerHostName="freedomvpn.local" 
            CustomConfig="default" />
    </uap:Extension>
</Extensions>
```

## VPN Gate Integration

### Fetching Server List

```csharp
public class VpnGateService
{
    private readonly HttpClient _httpClient = new HttpClient();
    
    public async Task<List<VpnGateServer>> FetchServersAsync()
    {
        var response = await _httpClient.GetStringAsync(
            "https://www.vpngate.net/api/iphone/");
        
        return ParseCsv(response);
    }
    
    private List<VpnGateServer> ParseCsv(string csv)
    {
        var servers = new List<VpnGateServer>();
        foreach (var line in csv.Split('\n'))
        {
            if (line.StartsWith("*") || line.StartsWith("#"))
                continue;
                
            var server = VpnGateServer.FromCsvLine(line);
            if (server != null)
                servers.Add(server);
        }
        return servers;
    }
}
```

## WireGuard Integration on Windows

### Option 1: WireGuard-NT (Recommended)

WireGuard-NT provides a kernel-mode driver for Windows:

```csharp
// Using WireGuard-NT wrapper
var tunnel = new WireGuardTunnel("FreedomVPN");
tunnel.SetConfiguration(config);
tunnel.Start();
```

### Option 2: Wireguard-go (Userspace)

For UWP sandboxed apps, you may need to use a userspace implementation.

## Building & Deployment

### Debug Build

```powershell
dotnet build -c Debug
```

### Create Package

```powershell
dotnet publish -c Release -p:Platform=x64
```

### Deploy to Device

In Visual Studio:
1. Right-click project → Deploy
2. Or use `Add-AppDevPackage.ps1` script

## Testing

### Local Testing

1. Build and deploy the app
2. Open Windows Settings → Network & Internet → VPN
3. Your VPN profile should appear after connecting through the app

### Debug VPN Plugin

1. Set breakpoints in `VpnBackgroundTask`
2. Attach debugger to the background host process
3. Trigger VPN connection

## Common Issues

### 1. Capability Denied

Ensure `networkingVpnProvider` capability is declared:
```xml
<rescap:Capability Name="networkingVpnProvider" />
```

### 2. Background Task Not Running

Register the background task properly in the manifest:
```xml
<Extension Category="windows.backgroundTasks" 
           EntryPoint="FreedomVPN.Uwp.VpnPlugin.VpnBackgroundTask">
    <BackgroundTasks>
        <Task Type="controlChannel" />
    </BackgroundTasks>
</Extension>
```

### 3. Connection Fails Immediately

- Check Windows Event Viewer for VPN-related errors
- Ensure server is reachable
- Verify certificate/authentication settings

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    FreedomVPN UWP App                       │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────┐    │
│  │   MainPage  │  │  ViewModel   │  │ VpnGateService  │    │
│  └─────────────┘  └──────────────┘  └─────────────────┘    │
│         │                │                   │              │
│         └────────────────┼───────────────────┘              │
│                          ▼                                  │
│              ┌──────────────────────┐                       │
│              │ VpnConnectionService │                       │
│              └──────────────────────┘                       │
│                          │                                  │
├──────────────────────────┼──────────────────────────────────┤
│                          ▼                                  │
│              ┌──────────────────────┐                       │
│              │  VpnBackgroundTask   │  (Background Process) │
│              └──────────────────────┘                       │
│                          │                                  │
│              ┌──────────────────────┐                       │
│              │ VpnPluginProcessor   │                       │
│              └──────────────────────┘                       │
├──────────────────────────┼──────────────────────────────────┤
│                          ▼                                  │
│              ┌──────────────────────┐                       │
│              │    Windows VPN       │  (Kernel Mode)        │
│              │    Networking        │                       │
│              └──────────────────────┘                       │
└─────────────────────────────────────────────────────────────┘
```

## Next Steps

1. Implement WireGuard-NT integration for native WireGuard support
2. Add Windows system tray icon for quick access
3. Implement split-tunneling configuration
4. Add auto-connect on startup option
5. Create installer using MSIX

## Resources

- [Windows.Networking.Vpn Namespace](https://docs.microsoft.com/en-us/uwp/api/windows.networking.vpn)
- [UWP VPN Plugin Sample](https://github.com/microsoft/Windows-universal-samples/tree/main/Samples/VpnPlugin)
- [WireGuard-NT](https://git.zx2c4.com/wireguard-nt/about/)
- [WinUI 3 Documentation](https://docs.microsoft.com/en-us/windows/apps/winui/winui3/)
