namespace FreedomVPN.Uwp.Models;

/// <summary>
/// Model representing a VPN Gate server
/// </summary>
public class VpnGateServer
{
    public string HostName { get; set; } = string.Empty;
    public string Ip { get; set; } = string.Empty;
    public long Score { get; set; }
    public int Ping { get; set; }
    public long Speed { get; set; }
    public string CountryLong { get; set; } = string.Empty;
    public string CountryShort { get; set; } = string.Empty;
    public int NumVpnSessions { get; set; }
    public long Uptime { get; set; }
    public long TotalUsers { get; set; }
    public long TotalTraffic { get; set; }
    public string LogType { get; set; } = string.Empty;
    public string Operator { get; set; } = string.Empty;
    public string Message { get; set; } = string.Empty;
    public string OpenVpnConfigBase64 { get; set; } = string.Empty;

    /// <summary>
    /// Get speed in Mbps
    /// </summary>
    public double SpeedMbps => Speed / 1_000_000.0;

    /// <summary>
    /// Formatted speed string
    /// </summary>
    public string FormattedSpeed => Speed switch
    {
        >= 1_000_000_000 => $"{Speed / 1_000_000_000.0:F1} Gbps",
        >= 1_000_000 => $"{Speed / 1_000_000.0:F1} Mbps",
        >= 1_000 => $"{Speed / 1_000.0:F1} Kbps",
        _ => $"{Speed} bps"
    };

    /// <summary>
    /// Calculate quality score (higher is better)
    /// </summary>
    public double QualityScore
    {
        get
        {
            var pingScore = Ping > 0 ? 100.0 / Ping : 0;
            var speedScore = SpeedMbps;
            var loadScore = NumVpnSessions > 0 ? 100.0 / NumVpnSessions : 100;
            return pingScore * 0.3 + speedScore * 0.5 + loadScore * 0.2;
        }
    }

    /// <summary>
    /// Check if server has OpenVPN config
    /// </summary>
    public bool HasOpenVpnConfig => !string.IsNullOrWhiteSpace(OpenVpnConfigBase64);

    /// <summary>
    /// Get country flag emoji
    /// </summary>
    public string CountryFlag
    {
        get
        {
            if (CountryShort.Length != 2) return "🌐";
            
            var firstChar = char.ConvertFromUtf32(CountryShort[0] - 'A' + 0x1F1E6);
            var secondChar = char.ConvertFromUtf32(CountryShort[1] - 'A' + 0x1F1E6);
            return firstChar + secondChar;
        }
    }

    /// <summary>
    /// Parse from CSV line
    /// </summary>
    public static VpnGateServer? FromCsvLine(string line)
    {
        try
        {
            var parts = line.Split(',');
            if (parts.Length < 15) return null;

            return new VpnGateServer
            {
                HostName = parts[0],
                Ip = parts[1],
                Score = long.TryParse(parts[2], out var score) ? score : 0,
                Ping = int.TryParse(parts[3], out var ping) ? ping : 999,
                Speed = long.TryParse(parts[4], out var speed) ? speed : 0,
                CountryLong = parts[5],
                CountryShort = parts[6],
                NumVpnSessions = int.TryParse(parts[7], out var sessions) ? sessions : 0,
                Uptime = long.TryParse(parts[8], out var uptime) ? uptime : 0,
                TotalUsers = long.TryParse(parts[9], out var users) ? users : 0,
                TotalTraffic = long.TryParse(parts[10], out var traffic) ? traffic : 0,
                LogType = parts[11],
                Operator = parts[12],
                Message = parts[13],
                OpenVpnConfigBase64 = parts.Length > 14 ? parts[14] : string.Empty
            };
        }
        catch
        {
            return null;
        }
    }
}
