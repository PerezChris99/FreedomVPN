using System.Net.NetworkInformation;
using Windows.Networking.Connectivity;

namespace FreedomVPN.Uwp.Services;

/// <summary>
/// Network Monitor and Auto-Reconnect Service for Windows
/// 
/// Monitors network connectivity and handles:
/// 1. Network state changes (WiFi, Ethernet, Mobile)
/// 2. Automatic VPN reconnection on network change
/// 3. Exponential backoff for failed connections
/// 4. Smart server switching on persistent failures
/// 
/// Critical for Uganda and censored regions where:
/// - Network connectivity can be unstable
/// - ISPs may drop VPN connections
/// - Networks may switch frequently
/// </summary>
public class NetworkMonitorService : IDisposable
{
    private const int MIN_RECONNECT_DELAY_MS = 1000;
    private const int MAX_RECONNECT_DELAY_MS = 60000;
    private const double RECONNECT_BACKOFF_MULTIPLIER = 2.0;
    private const int MAX_RECONNECT_ATTEMPTS = 10;

    public enum NetworkState
    {
        Available,
        Unavailable,
        Changed
    }

    public enum NetworkType
    {
        WiFi,
        Ethernet,
        Cellular,
        Unknown
    }

    public class NetworkChangedEventArgs : EventArgs
    {
        public NetworkState State { get; set; }
        public NetworkType Type { get; set; }
        public bool IsConnected { get; set; }
    }

    // Events
    public event EventHandler<NetworkChangedEventArgs>? NetworkChanged;
    public event EventHandler? ReconnectRequested;

    // State
    public bool IsConnected { get; private set; }
    public NetworkType CurrentNetworkType { get; private set; } = NetworkType.Unknown;

    // Reconnect state
    private int _reconnectAttempts = 0;
    private int _currentReconnectDelay = MIN_RECONNECT_DELAY_MS;
    private CancellationTokenSource? _reconnectCts;
    private Task? _reconnectTask;

    public NetworkMonitorService()
    {
        // Register for network changes
        NetworkChange.NetworkAvailabilityChanged += OnNetworkAvailabilityChanged;
        NetworkChange.NetworkAddressChanged += OnNetworkAddressChanged;
        
        // Windows specific network monitoring
        NetworkInformation.NetworkStatusChanged += OnNetworkStatusChanged;

        // Check initial state
        CheckCurrentNetwork();
    }

    /// <summary>
    /// Handle network availability changes
    /// </summary>
    private void OnNetworkAvailabilityChanged(object? sender, NetworkAvailabilityEventArgs e)
    {
        System.Diagnostics.Debug.WriteLine($"Network availability changed: {e.IsAvailable}");

        IsConnected = e.IsAvailable;

        NetworkChanged?.Invoke(this, new NetworkChangedEventArgs
        {
            State = e.IsAvailable ? NetworkState.Available : NetworkState.Unavailable,
            Type = CurrentNetworkType,
            IsConnected = e.IsAvailable
        });

        if (!e.IsAvailable)
        {
            RequestReconnect();
        }
    }

    /// <summary>
    /// Handle network address changes (interface changes)
    /// </summary>
    private void OnNetworkAddressChanged(object? sender, EventArgs e)
    {
        System.Diagnostics.Debug.WriteLine("Network address changed");
        CheckCurrentNetwork();
    }

    /// <summary>
    /// Handle Windows network status changes
    /// </summary>
    private void OnNetworkStatusChanged(object? sender)
    {
        System.Diagnostics.Debug.WriteLine("Windows network status changed");
        CheckCurrentNetwork();
    }

    /// <summary>
    /// Check and update current network state
    /// </summary>
    private void CheckCurrentNetwork()
    {
        try
        {
            var profile = NetworkInformation.GetInternetConnectionProfile();

            if (profile == null)
            {
                IsConnected = false;
                CurrentNetworkType = NetworkType.Unknown;
                NetworkChanged?.Invoke(this, new NetworkChangedEventArgs
                {
                    State = NetworkState.Unavailable,
                    Type = NetworkType.Unknown,
                    IsConnected = false
                });
                return;
            }

            IsConnected = profile.GetNetworkConnectivityLevel() == NetworkConnectivityLevel.InternetAccess;

            // Determine network type
            if (profile.IsWlanConnectionProfile)
            {
                CurrentNetworkType = NetworkType.WiFi;
            }
            else if (profile.IsWwanConnectionProfile)
            {
                CurrentNetworkType = NetworkType.Cellular;
            }
            else
            {
                CurrentNetworkType = NetworkType.Ethernet;
            }

            NetworkChanged?.Invoke(this, new NetworkChangedEventArgs
            {
                State = NetworkState.Changed,
                Type = CurrentNetworkType,
                IsConnected = IsConnected
            });

            System.Diagnostics.Debug.WriteLine($"Network: {CurrentNetworkType}, Connected: {IsConnected}");
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"Error checking network: {ex.Message}");
        }
    }

    /// <summary>
    /// Request VPN reconnection with exponential backoff
    /// </summary>
    public void RequestReconnect()
    {
        if (_reconnectAttempts >= MAX_RECONNECT_ATTEMPTS)
        {
            System.Diagnostics.Debug.WriteLine("Max reconnect attempts reached");
            ResetReconnectState();
            return;
        }

        _reconnectCts?.Cancel();
        _reconnectCts = new CancellationTokenSource();

        _reconnectTask = Task.Run(async () =>
        {
            try
            {
                _reconnectAttempts++;
                System.Diagnostics.Debug.WriteLine(
                    $"Scheduling reconnect attempt {_reconnectAttempts} in {_currentReconnectDelay}ms");

                await Task.Delay(_currentReconnectDelay, _reconnectCts.Token);

                // Increase delay for next attempt (exponential backoff)
                _currentReconnectDelay = Math.Min(
                    (int)(_currentReconnectDelay * RECONNECT_BACKOFF_MULTIPLIER),
                    MAX_RECONNECT_DELAY_MS);

                // Trigger reconnection
                ReconnectRequested?.Invoke(this, EventArgs.Empty);
            }
            catch (TaskCanceledException)
            {
                System.Diagnostics.Debug.WriteLine("Reconnect cancelled");
            }
        });
    }

    /// <summary>
    /// Reset reconnect state (call after successful connection)
    /// </summary>
    public void ResetReconnectState()
    {
        _reconnectAttempts = 0;
        _currentReconnectDelay = MIN_RECONNECT_DELAY_MS;
        _reconnectCts?.Cancel();
        _reconnectCts = null;
    }

    /// <summary>
    /// Get current reconnect attempt count
    /// </summary>
    public int GetReconnectAttempt() => _reconnectAttempts;

    /// <summary>
    /// Check if internet is currently available
    /// </summary>
    public bool IsInternetAvailable()
    {
        try
        {
            var profile = NetworkInformation.GetInternetConnectionProfile();
            return profile?.GetNetworkConnectivityLevel() == NetworkConnectivityLevel.InternetAccess;
        }
        catch
        {
            return NetworkInterface.GetIsNetworkAvailable();
        }
    }

    /// <summary>
    /// Get network cost type (important for metered connections)
    /// </summary>
    public string GetNetworkCostType()
    {
        try
        {
            var profile = NetworkInformation.GetInternetConnectionProfile();
            var cost = profile?.GetConnectionCost();

            return cost?.NetworkCostType switch
            {
                NetworkCostType.Unrestricted => "Unrestricted",
                NetworkCostType.Fixed => "Fixed",
                NetworkCostType.Variable => "Variable",
                NetworkCostType.Unknown => "Unknown",
                _ => "Unknown"
            };
        }
        catch
        {
            return "Unknown";
        }
    }

    public void Dispose()
    {
        NetworkChange.NetworkAvailabilityChanged -= OnNetworkAvailabilityChanged;
        NetworkChange.NetworkAddressChanged -= OnNetworkAddressChanged;
        NetworkInformation.NetworkStatusChanged -= OnNetworkStatusChanged;
        _reconnectCts?.Cancel();
        _reconnectCts?.Dispose();
    }
}
