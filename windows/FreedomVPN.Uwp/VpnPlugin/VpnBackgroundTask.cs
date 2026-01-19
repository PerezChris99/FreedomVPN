using System.Net.Sockets;
using System.Net;
using Windows.ApplicationModel.Background;
using Windows.Networking.Vpn;
using Windows.Storage.Streams;

namespace FreedomVPN.Uwp.VpnPlugin;

/// <summary>
/// Background task that handles VPN plugin operations
/// 
/// This task is registered as a VPN plugin and handles:
/// - Connection establishment
/// - Packet encapsulation/decapsulation
/// - Keep-alive management
/// - Reconnection on network changes
/// 
/// For WireGuard integration, you would implement the WireGuard protocol
/// handling within this task, using the embedded WireGuard library.
/// </summary>
public sealed class VpnBackgroundTask : IBackgroundTask
{
    private BackgroundTaskDeferral? _deferral;
    private VpnChannel? _channel;
    private UdpClient? _tunnelSocket;
    private bool _isRunning;
    private string _serverAddress = "";
    private int _serverPort = 51820;
    
    // Statistics
    private long _bytesIn;
    private long _bytesOut;
    private long _packetsIn;
    private long _packetsOut;
    private DateTime _connectedAt;

    /// <summary>
    /// Entry point for the background task
    /// </summary>
    public void Run(IBackgroundTaskInstance taskInstance)
    {
        _deferral = taskInstance.GetDeferral();
        taskInstance.Canceled += OnCanceled;

        try
        {
            // Get the VPN channel from the trigger
            if (taskInstance.TriggerDetails is VpnChannel channel)
            {
                _channel = channel;
                _connectedAt = DateTime.UtcNow;
                ProcessVpnChannel(channel);
            }
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"VPN Background Task Error: {ex.Message}");
            _deferral?.Complete();
        }
    }

    /// <summary>
    /// Process VPN channel operations
    /// </summary>
    private async void ProcessVpnChannel(VpnChannel channel)
    {
        try
        {
            // Get server info from channel configuration
            var config = channel.Configuration;
            _serverAddress = config.ServerHostNameList?.FirstOrDefault()?.DisplayName ?? "vpn.server.com";
            // Port would come from custom configuration
            
            // Set up channel configuration
            var routeScope = new VpnRouteAssignment();
            
            // Route all IPv4 traffic through VPN
            routeScope.Ipv4InclusionRoutes.Add(new VpnRoute(
                new Windows.Networking.HostName("0.0.0.0"), 0));
            
            // Route all IPv6 traffic through VPN  
            routeScope.Ipv6InclusionRoutes.Add(new VpnRoute(
                new Windows.Networking.HostName("::"), 0));

            // Configure DNS
            var dnsAssignment = new VpnDomainNameAssignment();
            var dnsServers = new List<Windows.Networking.HostName>
            {
                new Windows.Networking.HostName("8.8.8.8"),
                new Windows.Networking.HostName("8.8.4.4"),
                new Windows.Networking.HostName("1.1.1.1")  // Cloudflare as backup
            };

            // Create UDP socket for tunnel
            _tunnelSocket = new UdpClient();
            _tunnelSocket.Connect(_serverAddress, _serverPort);
            
            // Get the socket's stream transport for the channel
            var socket = new Windows.Networking.Sockets.DatagramSocket();
            await socket.ConnectAsync(new Windows.Networking.HostName(_serverAddress), _serverPort.ToString());
            var transport = socket;

            // Start the VPN channel
            channel.Start(
                mainOuterTunnelTransport: transport,
                optionalOuterTunnelTransport: null,
                requestedAssignment: routeScope,
                domainNameAssignment: dnsAssignment,
                mtuSize: 1280,
                maxFrameSize: 1500,
                optimizeForLowCostNetwork: false,
                mainInnerTunnelTransport: null,
                optionalInnerTunnelTransport: null
            );

            _isRunning = true;
            System.Diagnostics.Debug.WriteLine("VPN Channel started");

            // Start packet processing loops
            await Task.WhenAll(
                ProcessOutgoingPackets(),
                ProcessIncomingPackets(),
                SendKeepalives()
            );
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"ProcessVpnChannel error: {ex.Message}");
            channel.Stop();
            _deferral?.Complete();
        }
    }

    /// <summary>
    /// Process outgoing packets (from apps to VPN server)
    /// </summary>
    private async Task ProcessOutgoingPackets()
    {
        if (_channel == null) return;

        System.Diagnostics.Debug.WriteLine("Starting outgoing packet loop");

        while (_isRunning)
        {
            try
            {
                // Request packet buffer from channel
                _channel.RequestVpnPacketBuffer(VpnDataPathType.Send, out var packetBuffer);
                
                if (packetBuffer != null && packetBuffer.Buffer.Length > 0)
                {
                    // Read packet data
                    var packet = new byte[packetBuffer.Buffer.Length];
                    using var reader = DataReader.FromBuffer(packetBuffer.Buffer);
                    reader.ReadBytes(packet);

                    // TODO: Encrypt packet (WireGuard encryption goes here)
                    var encryptedPacket = EncryptPacket(packet);

                    // Send to VPN server
                    await _tunnelSocket!.SendAsync(encryptedPacket, encryptedPacket.Length);

                    _bytesOut += packet.Length;
                    _packetsOut++;
                }
                else
                {
                    await Task.Delay(1); // Prevent tight loop
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"Outgoing packet error: {ex.Message}");
                await Task.Delay(100);
            }
        }
    }

    /// <summary>
    /// Process incoming packets (from VPN server to apps)
    /// </summary>
    private async Task ProcessIncomingPackets()
    {
        if (_channel == null || _tunnelSocket == null) return;

        System.Diagnostics.Debug.WriteLine("Starting incoming packet loop");

        while (_isRunning)
        {
            try
            {
                // Receive from VPN server
                var result = await _tunnelSocket.ReceiveAsync();
                
                if (result.Buffer.Length > 0)
                {
                    // TODO: Decrypt packet (WireGuard decryption goes here)
                    var decryptedPacket = DecryptPacket(result.Buffer);

                    // Get buffer to inject into channel
                    _channel.RequestVpnPacketBuffer(VpnDataPathType.Receive, out var packetBuffer);
                    
                    if (packetBuffer != null)
                    {
                        // Write decrypted data to buffer
                        using var writer = new DataWriter(packetBuffer.Buffer.AsStream().AsOutputStream());
                        writer.WriteBytes(decryptedPacket);
                        await writer.StoreAsync();
                    }

                    _bytesIn += result.Buffer.Length;
                    _packetsIn++;
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"Incoming packet error: {ex.Message}");
                await Task.Delay(100);
            }
        }
    }

    /// <summary>
    /// Send periodic keepalive packets to maintain connection
    /// </summary>
    private async Task SendKeepalives()
    {
        while (_isRunning)
        {
            try
            {
                await Task.Delay(25000); // Every 25 seconds

                if (_tunnelSocket != null)
                {
                    // Send keepalive (empty packet or protocol-specific)
                    var keepalive = new byte[] { 0x00 };
                    await _tunnelSocket.SendAsync(keepalive, 1);
                    System.Diagnostics.Debug.WriteLine("Sent keepalive");
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"Keepalive error: {ex.Message}");
            }
        }
    }

    /// <summary>
    /// Encrypt outgoing packet
    /// TODO: Implement WireGuard encryption
    /// </summary>
    private byte[] EncryptPacket(byte[] packet)
    {
        // Placeholder - will be replaced with WireGuard encryption
        return packet;
    }

    /// <summary>
    /// Decrypt incoming packet
    /// TODO: Implement WireGuard decryption
    /// </summary>
    private byte[] DecryptPacket(byte[] packet)
    {
        // Placeholder - will be replaced with WireGuard decryption
        return packet;
    }

    /// <summary>
    /// Get current statistics
    /// </summary>
    public (long bytesIn, long bytesOut, long packetsIn, long packetsOut, TimeSpan duration) GetStats()
    {
        return (_bytesIn, _bytesOut, _packetsIn, _packetsOut, DateTime.UtcNow - _connectedAt);
    }

    /// <summary>
    /// Handle task cancellation
    /// </summary>
    private void OnCanceled(IBackgroundTaskInstance sender, BackgroundTaskCancellationReason reason)
    {
        System.Diagnostics.Debug.WriteLine($"VPN Background Task Canceled: {reason}");
        
        _isRunning = false;
        
        try
        {
            _tunnelSocket?.Close();
            _channel?.Stop();
        }
        catch { }
        finally
        {
            _deferral?.Complete();
        }
    }
}
