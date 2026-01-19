using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using FreedomVPN.Uwp.Models;
using FreedomVPN.Uwp.Services;
using Microsoft.UI;
using Microsoft.UI.Xaml.Media;
using System.Collections.ObjectModel;

namespace FreedomVPN.Uwp.ViewModels;

/// <summary>
/// Main ViewModel for the FreedomVPN Windows application
/// Uses CommunityToolkit.Mvvm for MVVM pattern implementation
/// </summary>
public partial class MainViewModel : ObservableObject
{
    private readonly VpnGateService _vpnGateService;
    private readonly VpnConnectionService _vpnConnectionService;

    public MainViewModel()
    {
        _vpnGateService = new VpnGateService();
        _vpnConnectionService = new VpnConnectionService();
        
        // Initialize collections
        Servers = new ObservableCollection<VpnGateServer>();
        
        // Load servers on startup
        _ = LoadServersAsync();
    }

    #region Observable Properties

    [ObservableProperty]
    private ConnectionState _connectionState = ConnectionState.Disconnected;

    [ObservableProperty]
    private VpnGateServer? _selectedServer;

    [ObservableProperty]
    private ObservableCollection<VpnGateServer> _servers;

    [ObservableProperty]
    private bool _isLoading;

    [ObservableProperty]
    private bool _isServerListVisible;

    [ObservableProperty]
    private string? _errorMessage;

    #endregion

    #region Computed Properties

    public bool IsConnected => ConnectionState == ConnectionState.Connected;
    public bool IsConnecting => ConnectionState == ConnectionState.Connecting;

    public string ConnectionStatusText => ConnectionState switch
    {
        ConnectionState.Connected => "Protected",
        ConnectionState.Connecting => "Connecting...",
        ConnectionState.Disconnecting => "Disconnecting...",
        ConnectionState.Error => "Connection Error",
        _ => "Not Protected"
    };

    public string ConnectionIcon => ConnectionState switch
    {
        ConnectionState.Connected => "\uE72E",  // Lock
        ConnectionState.Connecting => "\uE712", // Progress
        _ => "\uE785"  // Unlock
    };

    public Brush ConnectionStatusBrush => ConnectionState switch
    {
        ConnectionState.Connected => new SolidColorBrush(Colors.Green),
        ConnectionState.Connecting => new SolidColorBrush(Colors.Orange),
        ConnectionState.Error => new SolidColorBrush(Colors.Red),
        _ => new SolidColorBrush(Colors.Gray)
    };

    public string ConnectButtonText => IsConnected ? "Disconnect" : "Connect";

    public string SelectedServerName => SelectedServer?.CountryLong ?? "Select a Server";
    public string SelectedServerFlag => SelectedServer?.CountryFlag ?? "🌐";
    public string SelectedServerStats => SelectedServer != null 
        ? $"{SelectedServer.FormattedSpeed} • {SelectedServer.Ping}ms" 
        : "Tap to select";

    public string ConnectedServerInfo => SelectedServer != null 
        ? $"{SelectedServer.CountryFlag} {SelectedServer.CountryLong}" 
        : "";

    public int ServerCount => Servers.Count;

    #endregion

    #region Commands

    [RelayCommand]
    private async Task ToggleConnectionAsync()
    {
        if (IsConnected)
        {
            await DisconnectAsync();
        }
        else
        {
            await ConnectAsync();
        }
    }

    [RelayCommand]
    private async Task ConnectAsync()
    {
        if (SelectedServer == null)
        {
            // Auto-select best server if none selected
            await AutoSelectServerAsync();
        }

        if (SelectedServer == null) return;

        try
        {
            ConnectionState = ConnectionState.Connecting;
            OnPropertyChanged(nameof(IsConnecting));
            OnPropertyChanged(nameof(ConnectionStatusText));
            OnPropertyChanged(nameof(ConnectionIcon));
            OnPropertyChanged(nameof(ConnectionStatusBrush));
            OnPropertyChanged(nameof(ConnectButtonText));

            await _vpnConnectionService.ConnectAsync(SelectedServer);
            
            ConnectionState = ConnectionState.Connected;
        }
        catch (Exception ex)
        {
            ErrorMessage = ex.Message;
            ConnectionState = ConnectionState.Error;
        }
        finally
        {
            NotifyConnectionStateChanged();
        }
    }

    [RelayCommand]
    private async Task DisconnectAsync()
    {
        try
        {
            ConnectionState = ConnectionState.Disconnecting;
            NotifyConnectionStateChanged();

            await _vpnConnectionService.DisconnectAsync();
            
            ConnectionState = ConnectionState.Disconnected;
        }
        catch (Exception ex)
        {
            ErrorMessage = ex.Message;
            ConnectionState = ConnectionState.Error;
        }
        finally
        {
            NotifyConnectionStateChanged();
        }
    }

    [RelayCommand]
    private async Task RefreshServersAsync()
    {
        await LoadServersAsync(forceRefresh: true);
    }

    [RelayCommand]
    private async Task AutoSelectServerAsync()
    {
        var servers = Servers.ToList();
        if (!servers.Any())
        {
            await LoadServersAsync();
            servers = Servers.ToList();
        }

        // Select the server with the best quality score
        SelectedServer = servers
            .OrderByDescending(s => s.QualityScore)
            .FirstOrDefault();

        OnPropertyChanged(nameof(SelectedServerName));
        OnPropertyChanged(nameof(SelectedServerFlag));
        OnPropertyChanged(nameof(SelectedServerStats));
    }

    [RelayCommand]
    private void ShowServerList()
    {
        IsServerListVisible = !IsServerListVisible;
    }

    [RelayCommand]
    private void OpenSettings()
    {
        // TODO: Open settings page
    }

    [RelayCommand]
    private void SelectServer(VpnGateServer server)
    {
        SelectedServer = server;
        IsServerListVisible = false;
        
        OnPropertyChanged(nameof(SelectedServerName));
        OnPropertyChanged(nameof(SelectedServerFlag));
        OnPropertyChanged(nameof(SelectedServerStats));
    }

    #endregion

    #region Private Methods

    private async Task LoadServersAsync(bool forceRefresh = false)
    {
        try
        {
            IsLoading = true;
            ErrorMessage = null;

            var serverList = await _vpnGateService.FetchServersAsync(forceRefresh);
            
            Servers.Clear();
            foreach (var server in serverList.Take(50))
            {
                Servers.Add(server);
            }

            OnPropertyChanged(nameof(ServerCount));
        }
        catch (Exception ex)
        {
            ErrorMessage = $"Failed to load servers: {ex.Message}";
        }
        finally
        {
            IsLoading = false;
        }
    }

    private void NotifyConnectionStateChanged()
    {
        OnPropertyChanged(nameof(IsConnected));
        OnPropertyChanged(nameof(IsConnecting));
        OnPropertyChanged(nameof(ConnectionStatusText));
        OnPropertyChanged(nameof(ConnectionIcon));
        OnPropertyChanged(nameof(ConnectionStatusBrush));
        OnPropertyChanged(nameof(ConnectButtonText));
        OnPropertyChanged(nameof(ConnectedServerInfo));
    }

    #endregion
}

/// <summary>
/// Connection state enumeration
/// </summary>
public enum ConnectionState
{
    Disconnected,
    Connecting,
    Connected,
    Disconnecting,
    Error
}
