/**
 * FreedomVPN Dynamic Statistics Engine
 * 
 * Provides real-time, accurate statistics across all platforms:
 * - Bandwidth usage (upload/download)
 * - Data savings from compression
 * - Money saved (in local currency)
 * - Connection quality metrics
 * - Historical data and trends
 */

class DynamicStatsEngine {
  constructor(options = {}) {
    this.config = {
      // Uganda pricing defaults
      pricePerMB: options.pricePerMB || 50, // UGX per MB
      currency: options.currency || 'UGX',
      compressionRatio: options.compressionRatio || 0.45, // 45% average savings
      updateInterval: options.updateInterval || 1000, // 1 second
      historyLength: options.historyLength || 3600, // 1 hour of data
    };
    
    // Real-time stats
    this.stats = {
      session: {
        bytesIn: 0,
        bytesOut: 0,
        packetsIn: 0,
        packetsOut: 0,
        startTime: null,
        endTime: null
      },
      realtime: {
        downloadSpeed: 0, // bytes per second
        uploadSpeed: 0,
        latency: 0,
        jitter: 0,
        packetLoss: 0
      },
      savings: {
        dataCompressed: 0,
        dataSaved: 0,
        moneySaved: 0
      },
      quality: {
        score: 100,
        status: 'excellent',
        issues: []
      }
    };
    
    // Historical data for graphs
    this.history = {
      downloadSpeed: [],
      uploadSpeed: [],
      latency: [],
      timestamps: []
    };
    
    // Bandwidth measurement
    this._lastBytesIn = 0;
    this._lastBytesOut = 0;
    this._lastMeasureTime = Date.now();
    this._latencyHistory = [];
    
    this._updateInterval = null;
    this._listeners = new Map();
  }
  
  // Start tracking
  start() {
    this.stats.session.startTime = new Date();
    this._lastMeasureTime = Date.now();
    
    this._updateInterval = setInterval(() => {
      this.update();
    }, this.config.updateInterval);
    
    console.log('[FreedomVPN Stats] Tracking started');
  }
  
  // Stop tracking
  stop() {
    if (this._updateInterval) {
      clearInterval(this._updateInterval);
    }
    this.stats.session.endTime = new Date();
    console.log('[FreedomVPN Stats] Tracking stopped');
  }
  
  // Record incoming data
  recordDownload(bytes) {
    this.stats.session.bytesIn += bytes;
    this.stats.session.packetsIn++;
    
    // Calculate compressed data savings
    const originalBytes = bytes / (1 - this.config.compressionRatio);
    const saved = originalBytes - bytes;
    this.stats.savings.dataSaved += saved;
    this.stats.savings.dataCompressed += bytes;
  }
  
  // Record outgoing data  
  recordUpload(bytes) {
    this.stats.session.bytesOut += bytes;
    this.stats.session.packetsOut++;
  }
  
  // Record latency measurement
  recordLatency(ms) {
    this._latencyHistory.push(ms);
    if (this._latencyHistory.length > 100) {
      this._latencyHistory.shift();
    }
    
    // Calculate jitter (variance in latency)
    if (this._latencyHistory.length > 1) {
      const diffs = [];
      for (let i = 1; i < this._latencyHistory.length; i++) {
        diffs.push(Math.abs(this._latencyHistory[i] - this._latencyHistory[i-1]));
      }
      this.stats.realtime.jitter = diffs.reduce((a, b) => a + b, 0) / diffs.length;
    }
    
    this.stats.realtime.latency = ms;
  }
  
  // Record packet loss
  recordPacketLoss(lossPercent) {
    this.stats.realtime.packetLoss = lossPercent;
  }
  
  // Update all stats
  update() {
    const now = Date.now();
    const elapsed = (now - this._lastMeasureTime) / 1000; // seconds
    
    if (elapsed > 0) {
      // Calculate speeds
      const bytesInDelta = this.stats.session.bytesIn - this._lastBytesIn;
      const bytesOutDelta = this.stats.session.bytesOut - this._lastBytesOut;
      
      this.stats.realtime.downloadSpeed = Math.round(bytesInDelta / elapsed);
      this.stats.realtime.uploadSpeed = Math.round(bytesOutDelta / elapsed);
      
      this._lastBytesIn = this.stats.session.bytesIn;
      this._lastBytesOut = this.stats.session.bytesOut;
      this._lastMeasureTime = now;
    }
    
    // Update money saved
    const mbSaved = this.stats.savings.dataSaved / (1024 * 1024);
    this.stats.savings.moneySaved = Math.round(mbSaved * this.config.pricePerMB);
    
    // Update quality score
    this.updateQualityScore();
    
    // Record history
    this.recordHistory();
    
    // Emit update event
    this.emit('update', this.getStats());
  }
  
  // Calculate connection quality
  updateQualityScore() {
    let score = 100;
    const issues = [];
    
    // Latency impact
    if (this.stats.realtime.latency > 500) {
      score -= 30;
      issues.push('High latency');
    } else if (this.stats.realtime.latency > 200) {
      score -= 15;
      issues.push('Moderate latency');
    } else if (this.stats.realtime.latency > 100) {
      score -= 5;
    }
    
    // Jitter impact
    if (this.stats.realtime.jitter > 50) {
      score -= 20;
      issues.push('High jitter');
    } else if (this.stats.realtime.jitter > 20) {
      score -= 10;
    }
    
    // Packet loss impact
    if (this.stats.realtime.packetLoss > 5) {
      score -= 30;
      issues.push('Packet loss');
    } else if (this.stats.realtime.packetLoss > 1) {
      score -= 15;
    }
    
    // Speed impact (relative to Uganda's average ~5Mbps)
    const downloadMbps = (this.stats.realtime.downloadSpeed * 8) / (1024 * 1024);
    if (downloadMbps < 0.5) {
      score -= 20;
      issues.push('Very slow connection');
    } else if (downloadMbps < 2) {
      score -= 10;
      issues.push('Slow connection');
    }
    
    score = Math.max(0, Math.min(100, score));
    
    let status;
    if (score >= 90) status = 'excellent';
    else if (score >= 70) status = 'good';
    else if (score >= 50) status = 'fair';
    else if (score >= 30) status = 'poor';
    else status = 'critical';
    
    this.stats.quality = { score, status, issues };
  }
  
  // Record data for historical graphs
  recordHistory() {
    const maxLength = this.config.historyLength;
    
    this.history.timestamps.push(Date.now());
    this.history.downloadSpeed.push(this.stats.realtime.downloadSpeed);
    this.history.uploadSpeed.push(this.stats.realtime.uploadSpeed);
    this.history.latency.push(this.stats.realtime.latency);
    
    // Trim to max length
    if (this.history.timestamps.length > maxLength) {
      this.history.timestamps.shift();
      this.history.downloadSpeed.shift();
      this.history.uploadSpeed.shift();
      this.history.latency.shift();
    }
  }
  
  // Get formatted stats
  getStats() {
    return {
      session: {
        ...this.stats.session,
        duration: this.getSessionDuration(),
        totalData: this.stats.session.bytesIn + this.stats.session.bytesOut
      },
      realtime: {
        ...this.stats.realtime,
        downloadSpeedFormatted: this.formatSpeed(this.stats.realtime.downloadSpeed),
        uploadSpeedFormatted: this.formatSpeed(this.stats.realtime.uploadSpeed),
        latencyFormatted: `${Math.round(this.stats.realtime.latency)} ms`
      },
      savings: {
        ...this.stats.savings,
        dataSavedFormatted: this.formatBytes(this.stats.savings.dataSaved),
        moneySavedFormatted: `${this.stats.savings.moneySaved.toLocaleString()} ${this.config.currency}`
      },
      quality: this.stats.quality
    };
  }
  
  // Get session duration
  getSessionDuration() {
    if (!this.stats.session.startTime) return 0;
    const end = this.stats.session.endTime || new Date();
    return end.getTime() - this.stats.session.startTime.getTime();
  }
  
  // Format duration as HH:MM:SS
  formatDuration(ms) {
    const seconds = Math.floor(ms / 1000);
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;
    
    return [
      hours.toString().padStart(2, '0'),
      minutes.toString().padStart(2, '0'),
      secs.toString().padStart(2, '0')
    ].join(':');
  }
  
  // Format bytes to human readable
  formatBytes(bytes) {
    if (bytes === 0) return '0 B';
    
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  }
  
  // Format speed to human readable
  formatSpeed(bytesPerSecond) {
    const bitsPerSecond = bytesPerSecond * 8;
    
    if (bitsPerSecond === 0) return '0 bps';
    
    const k = 1024;
    const sizes = ['bps', 'Kbps', 'Mbps', 'Gbps'];
    const i = Math.floor(Math.log(bitsPerSecond) / Math.log(k));
    
    return parseFloat((bitsPerSecond / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  }
  
  // Get history for charts
  getHistory(metric, duration = 60000) {
    const cutoff = Date.now() - duration;
    const startIndex = this.history.timestamps.findIndex(t => t >= cutoff);
    
    if (startIndex === -1) return { timestamps: [], values: [] };
    
    return {
      timestamps: this.history.timestamps.slice(startIndex),
      values: this.history[metric]?.slice(startIndex) || []
    };
  }
  
  // Event handling
  on(event, callback) {
    if (!this._listeners.has(event)) {
      this._listeners.set(event, []);
    }
    this._listeners.get(event).push(callback);
  }
  
  off(event, callback) {
    const listeners = this._listeners.get(event);
    if (listeners) {
      const index = listeners.indexOf(callback);
      if (index > -1) listeners.splice(index, 1);
    }
  }
  
  emit(event, data) {
    const listeners = this._listeners.get(event) || [];
    listeners.forEach(cb => cb(data));
  }
  
  // Reset stats
  reset() {
    this.stats = {
      session: {
        bytesIn: 0,
        bytesOut: 0,
        packetsIn: 0,
        packetsOut: 0,
        startTime: new Date(),
        endTime: null
      },
      realtime: {
        downloadSpeed: 0,
        uploadSpeed: 0,
        latency: 0,
        jitter: 0,
        packetLoss: 0
      },
      savings: {
        dataCompressed: 0,
        dataSaved: 0,
        moneySaved: 0
      },
      quality: {
        score: 100,
        status: 'excellent',
        issues: []
      }
    };
    
    this.history = {
      downloadSpeed: [],
      uploadSpeed: [],
      latency: [],
      timestamps: []
    };
  }
}

// Persistent stats storage
class StatsStorage {
  constructor(storageKey = 'freedomvpn_stats') {
    this.storageKey = storageKey;
  }
  
  async save(stats) {
    const data = {
      ...stats,
      savedAt: new Date().toISOString()
    };
    
    try {
      if (typeof chrome !== 'undefined' && chrome.storage) {
        await chrome.storage.local.set({ [this.storageKey]: data });
      } else if (typeof localStorage !== 'undefined') {
        localStorage.setItem(this.storageKey, JSON.stringify(data));
      }
    } catch (error) {
      console.error('[FreedomVPN Stats] Failed to save:', error);
    }
  }
  
  async load() {
    try {
      if (typeof chrome !== 'undefined' && chrome.storage) {
        const result = await chrome.storage.local.get(this.storageKey);
        return result[this.storageKey] || null;
      } else if (typeof localStorage !== 'undefined') {
        const data = localStorage.getItem(this.storageKey);
        return data ? JSON.parse(data) : null;
      }
    } catch (error) {
      console.error('[FreedomVPN Stats] Failed to load:', error);
    }
    return null;
  }
  
  async getLifetimeStats() {
    const data = await this.load();
    if (!data) {
      return {
        totalBytesIn: 0,
        totalBytesOut: 0,
        totalSaved: 0,
        totalMoneySaved: 0,
        totalConnections: 0,
        totalTime: 0
      };
    }
    return data.lifetime || {};
  }
  
  async updateLifetimeStats(sessionStats) {
    const lifetime = await this.getLifetimeStats();
    
    lifetime.totalBytesIn = (lifetime.totalBytesIn || 0) + sessionStats.bytesIn;
    lifetime.totalBytesOut = (lifetime.totalBytesOut || 0) + sessionStats.bytesOut;
    lifetime.totalSaved = (lifetime.totalSaved || 0) + sessionStats.dataSaved;
    lifetime.totalMoneySaved = (lifetime.totalMoneySaved || 0) + sessionStats.moneySaved;
    lifetime.totalConnections = (lifetime.totalConnections || 0) + 1;
    lifetime.totalTime = (lifetime.totalTime || 0) + sessionStats.duration;
    
    await this.save({ lifetime });
    
    return lifetime;
  }
}

// Export
if (typeof module !== 'undefined' && module.exports) {
  module.exports = { DynamicStatsEngine, StatsStorage };
}

if (typeof window !== 'undefined') {
  window.FreedomVPN = window.FreedomVPN || {};
  window.FreedomVPN.DynamicStatsEngine = DynamicStatsEngine;
  window.FreedomVPN.StatsStorage = StatsStorage;
}
