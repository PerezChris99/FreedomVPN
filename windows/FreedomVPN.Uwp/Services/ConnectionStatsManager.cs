namespace FreedomVPN.Uwp.Services;

/// <summary>
/// Real-time VPN Connection Statistics Manager for Windows
/// 
/// Tracks and reports:
/// 1. Data usage (bytes sent/received)
/// 2. Current speed (upload/download)
/// 3. Connection duration
/// 4. Packet counts
/// 5. Average speeds over time
/// 
/// Useful for Uganda and censored regions to:
/// - Monitor data usage on limited plans
/// - Identify throttling by ISPs
/// - Measure actual connection quality
/// </summary>
public class ConnectionStatsManager : IDisposable
{
    /// <summary>
    /// Connection statistics data
    /// </summary>
    public class Stats
    {
        public long BytesIn { get; set; }
        public long BytesOut { get; set; }
        public long PacketsIn { get; set; }
        public long PacketsOut { get; set; }
        public long SpeedIn { get; set; }      // bytes/sec
        public long SpeedOut { get; set; }     // bytes/sec
        public long AvgSpeedIn { get; set; }   // bytes/sec average
        public long AvgSpeedOut { get; set; }  // bytes/sec average
        public long PeakSpeedIn { get; set; }  // bytes/sec peak
        public long PeakSpeedOut { get; set; } // bytes/sec peak
        public DateTime ConnectedAt { get; set; }
        public TimeSpan Duration { get; set; }
        public string ServerName { get; set; } = "";
        public string ServerCountry { get; set; } = "";
        public string Protocol { get; set; } = "WireGuard";

        // Formatted values for UI
        public string FormattedBytesIn => FormatBytes(BytesIn);
        public string FormattedBytesOut => FormatBytes(BytesOut);
        public string FormattedTotal => FormatBytes(BytesIn + BytesOut);
        public string FormattedSpeedIn => $"{FormatBytes(SpeedIn)}/s";
        public string FormattedSpeedOut => $"{FormatBytes(SpeedOut)}/s";
        public string FormattedAvgSpeedIn => $"{FormatBytes(AvgSpeedIn)}/s";
        public string FormattedAvgSpeedOut => $"{FormatBytes(AvgSpeedOut)}/s";
        public string FormattedPeakSpeedIn => $"{FormatBytes(PeakSpeedIn)}/s";
        public string FormattedPeakSpeedOut => $"{FormatBytes(PeakSpeedOut)}/s";
        public string FormattedDuration => FormatDuration(Duration);

        public static string FormatBytes(long bytes) => bytes switch
        {
            >= 1_000_000_000 => $"{bytes / 1_000_000_000.0:F2} GB",
            >= 1_000_000 => $"{bytes / 1_000_000.0:F2} MB",
            >= 1_000 => $"{bytes / 1_000.0:F2} KB",
            _ => $"{bytes} B"
        };

        public static string FormatDuration(TimeSpan duration)
        {
            return duration.TotalHours >= 1
                ? $"{(int)duration.TotalHours:D2}:{duration.Minutes:D2}:{duration.Seconds:D2}"
                : $"{duration.Minutes:D2}:{duration.Seconds:D2}";
        }
    }

    // Events
    public event EventHandler<Stats>? StatsUpdated;

    // Current stats
    public Stats CurrentStats { get; private set; } = new();

    // Internal counters
    private long _bytesIn;
    private long _bytesOut;
    private long _packetsIn;
    private long _packetsOut;
    
    private long _lastBytesIn;
    private long _lastBytesOut;
    private DateTime _lastUpdateTime;
    
    private long _peakSpeedIn;
    private long _peakSpeedOut;
    private DateTime _connectedAt;
    private string _serverName = "";
    private string _serverCountry = "";
    private string _protocol = "WireGuard";

    // Speed history for averaging
    private readonly List<(long SpeedIn, long SpeedOut)> _speedHistory = new();
    private const int MaxHistorySize = 60; // 60 seconds of history

    // Timer for periodic updates
    private Timer? _updateTimer;

    /// <summary>
    /// Start tracking statistics
    /// </summary>
    public void StartTracking(
        string serverName = "",
        string serverCountry = "",
        string protocol = "WireGuard")
    {
        _serverName = serverName;
        _serverCountry = serverCountry;
        _protocol = protocol;
        
        Reset();
        _connectedAt = DateTime.UtcNow;
        _lastUpdateTime = _connectedAt;

        // Start periodic updates
        _updateTimer = new Timer(
            callback: _ => UpdateStats(),
            state: null,
            dueTime: TimeSpan.FromSeconds(1),
            period: TimeSpan.FromSeconds(1));
    }

    /// <summary>
    /// Stop tracking statistics
    /// </summary>
    public void StopTracking()
    {
        _updateTimer?.Dispose();
        _updateTimer = null;
    }

    /// <summary>
    /// Record incoming bytes
    /// </summary>
    public void RecordBytesIn(long bytes)
    {
        Interlocked.Add(ref _bytesIn, bytes);
        Interlocked.Increment(ref _packetsIn);
    }

    /// <summary>
    /// Record outgoing bytes
    /// </summary>
    public void RecordBytesOut(long bytes)
    {
        Interlocked.Add(ref _bytesOut, bytes);
        Interlocked.Increment(ref _packetsOut);
    }

    /// <summary>
    /// Update stats from external source (e.g., WireGuard)
    /// </summary>
    public void UpdateFromExternal(long rxBytes, long txBytes)
    {
        if (rxBytes > _bytesIn) Interlocked.Exchange(ref _bytesIn, rxBytes);
        if (txBytes > _bytesOut) Interlocked.Exchange(ref _bytesOut, txBytes);
    }

    /// <summary>
    /// Reset all statistics
    /// </summary>
    public void Reset()
    {
        _bytesIn = 0;
        _bytesOut = 0;
        _packetsIn = 0;
        _packetsOut = 0;
        _lastBytesIn = 0;
        _lastBytesOut = 0;
        _peakSpeedIn = 0;
        _peakSpeedOut = 0;
        _speedHistory.Clear();
        CurrentStats = new Stats();
    }

    /// <summary>
    /// Get current stats snapshot
    /// </summary>
    public Stats GetSnapshot() => CurrentStats;

    /// <summary>
    /// Update statistics (called periodically)
    /// </summary>
    private void UpdateStats()
    {
        var now = DateTime.UtcNow;
        var elapsed = (now - _lastUpdateTime).TotalMilliseconds;
        
        if (elapsed <= 0) return;
        
        var currentBytesIn = Interlocked.Read(ref _bytesIn);
        var currentBytesOut = Interlocked.Read(ref _bytesOut);
        
        // Calculate current speed
        var deltaIn = currentBytesIn - _lastBytesIn;
        var deltaOut = currentBytesOut - _lastBytesOut;
        
        var speedIn = (long)(deltaIn * 1000 / elapsed);
        var speedOut = (long)(deltaOut * 1000 / elapsed);
        
        // Update peaks
        if (speedIn > _peakSpeedIn) _peakSpeedIn = speedIn;
        if (speedOut > _peakSpeedOut) _peakSpeedOut = speedOut;
        
        // Add to history for averaging
        _speedHistory.Add((speedIn, speedOut));
        while (_speedHistory.Count > MaxHistorySize)
        {
            _speedHistory.RemoveAt(0);
        }
        
        // Calculate averages
        var avgSpeedIn = _speedHistory.Count > 0
            ? _speedHistory.Sum(s => s.SpeedIn) / _speedHistory.Count
            : 0;
        
        var avgSpeedOut = _speedHistory.Count > 0
            ? _speedHistory.Sum(s => s.SpeedOut) / _speedHistory.Count
            : 0;
        
        // Update state
        CurrentStats = new Stats
        {
            BytesIn = currentBytesIn,
            BytesOut = currentBytesOut,
            PacketsIn = Interlocked.Read(ref _packetsIn),
            PacketsOut = Interlocked.Read(ref _packetsOut),
            SpeedIn = speedIn,
            SpeedOut = speedOut,
            AvgSpeedIn = avgSpeedIn,
            AvgSpeedOut = avgSpeedOut,
            PeakSpeedIn = _peakSpeedIn,
            PeakSpeedOut = _peakSpeedOut,
            ConnectedAt = _connectedAt,
            Duration = now - _connectedAt,
            ServerName = _serverName,
            ServerCountry = _serverCountry,
            Protocol = _protocol
        };
        
        // Remember for next calculation
        _lastBytesIn = currentBytesIn;
        _lastBytesOut = currentBytesOut;
        _lastUpdateTime = now;

        // Notify listeners
        StatsUpdated?.Invoke(this, CurrentStats);
    }

    /// <summary>
    /// Export stats for logging/analytics
    /// </summary>
    public Dictionary<string, object> ExportStats()
    {
        var s = CurrentStats;
        return new Dictionary<string, object>
        {
            ["bytes_in"] = s.BytesIn,
            ["bytes_out"] = s.BytesOut,
            ["packets_in"] = s.PacketsIn,
            ["packets_out"] = s.PacketsOut,
            ["speed_in"] = s.SpeedIn,
            ["speed_out"] = s.SpeedOut,
            ["avg_speed_in"] = s.AvgSpeedIn,
            ["avg_speed_out"] = s.AvgSpeedOut,
            ["peak_speed_in"] = s.PeakSpeedIn,
            ["peak_speed_out"] = s.PeakSpeedOut,
            ["duration_ms"] = s.Duration.TotalMilliseconds,
            ["server"] = s.ServerName,
            ["country"] = s.ServerCountry,
            ["protocol"] = s.Protocol
        };
    }

    public void Dispose()
    {
        _updateTimer?.Dispose();
    }
}
