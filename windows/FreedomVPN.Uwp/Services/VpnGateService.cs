using FreedomVPN.Uwp.Models;
using System.Net.Http;
using System.Text;

namespace FreedomVPN.Uwp.Services;

/// <summary>
/// Service for fetching and parsing VPN Gate server list
/// </summary>
public class VpnGateService
{
    private const string VpnGateApiUrl = "https://www.vpngate.net/api/iphone/";
    private const string VpnGateMirrorUrl = "http://www.vpngate.net/api/iphone/";
    private const int CacheDurationMinutes = 5;

    private readonly HttpClient _httpClient;
    private List<VpnGateServer> _cachedServers = new();
    private DateTime _lastFetchTime = DateTime.MinValue;

    public VpnGateService()
    {
        _httpClient = new HttpClient
        {
            Timeout = TimeSpan.FromSeconds(60)
        };
        _httpClient.DefaultRequestHeaders.Add("User-Agent", "FreedomVPN/1.0 Windows");
    }

    /// <summary>
    /// Fetch server list from VPN Gate API
    /// </summary>
    public async Task<List<VpnGateServer>> FetchServersAsync(bool forceRefresh = false)
    {
        // Return cached if valid
        if (!forceRefresh && _cachedServers.Any() && 
            DateTime.Now - _lastFetchTime < TimeSpan.FromMinutes(CacheDurationMinutes))
        {
            return _cachedServers;
        }

        string csvData;
        
        try
        {
            csvData = await _httpClient.GetStringAsync(VpnGateApiUrl);
        }
        catch
        {
            // Try mirror on failure
            csvData = await _httpClient.GetStringAsync(VpnGateMirrorUrl);
        }

        var servers = ParseServerList(csvData)
            .Where(s => s.HasOpenVpnConfig)
            .Where(s => s.Ping > 0 && s.Ping < 1000)
            .OrderByDescending(s => s.QualityScore)
            .ToList();

        _cachedServers = servers;
        _lastFetchTime = DateTime.Now;

        return servers;
    }

    /// <summary>
    /// Parse CSV data into server list
    /// </summary>
    private List<VpnGateServer> ParseServerList(string csvData)
    {
        var servers = new List<VpnGateServer>();
        var lines = csvData.Split(new[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries);

        foreach (var line in lines)
        {
            // Skip header and comment lines
            if (line.StartsWith("*") || line.StartsWith("#") || string.IsNullOrWhiteSpace(line))
            {
                continue;
            }

            var server = VpnGateServer.FromCsvLine(line);
            if (server != null)
            {
                servers.Add(server);
            }
        }

        return servers;
    }

    /// <summary>
    /// Get servers filtered by country
    /// </summary>
    public async Task<List<VpnGateServer>> GetServersByCountryAsync(string countryCode)
    {
        var servers = await FetchServersAsync();
        return servers
            .Where(s => s.CountryShort.Equals(countryCode, StringComparison.OrdinalIgnoreCase))
            .ToList();
    }

    /// <summary>
    /// Get the best server overall
    /// </summary>
    public async Task<VpnGateServer?> GetBestServerAsync()
    {
        var servers = await FetchServersAsync();
        return servers.MaxBy(s => s.QualityScore);
    }

    /// <summary>
    /// Decode OpenVPN config from Base64
    /// </summary>
    public string? DecodeOpenVpnConfig(VpnGateServer server)
    {
        try
        {
            var bytes = Convert.FromBase64String(server.OpenVpnConfigBase64);
            return Encoding.UTF8.GetString(bytes);
        }
        catch
        {
            return null;
        }
    }

    /// <summary>
    /// Get available countries
    /// </summary>
    public async Task<List<(string Code, string Name)>> GetAvailableCountriesAsync()
    {
        var servers = await FetchServersAsync();
        return servers
            .Select(s => (s.CountryShort, s.CountryLong))
            .Distinct()
            .OrderBy(c => c.CountryLong)
            .ToList();
    }

    /// <summary>
    /// Clear cached servers
    /// </summary>
    public void ClearCache()
    {
        _cachedServers.Clear();
        _lastFetchTime = DateTime.MinValue;
    }
}
