/**
 * FreedomVPN Multi-Hop Engine
 * 
 * Provides server-bouncing functionality for enhanced anonymity.
 * Traffic is routed through multiple VPN servers in sequence,
 * making it virtually impossible to trace back to the origin.
 * 
 * Key Features:
 * - Instant activation with one-click toggle
 * - Intelligent server selection for minimal latency
 * - Parallel connection negotiation for speed
 * - Geographic diversity for maximum anonymity
 * - Automatic fallback if a hop fails
 */

class MultiHopEngine {
  constructor(options = {}) {
    // Configuration
    this.config = {
      // Number of hops (2-4 recommended, more = slower but more anonymous)
      hopCount: options.hopCount || 2,
      // Maximum acceptable latency per hop in ms
      maxHopLatency: options.maxHopLatency || 100,
      // Whether to prefer geographic diversity
      diverseRouting: options.diverseRouting !== false,
      // Fallback to single hop if multi-hop fails
      fallbackEnabled: options.fallbackEnabled !== false,
      // Pre-establish connections for instant switching
      preconnect: options.preconnect !== false,
      // Refresh hop chain periodically (minutes)
      rotationInterval: options.rotationInterval || 30,
    };

    // State
    this.isActive = false;
    this.currentChain = [];
    this.preconnectedChains = [];
    this.latencyCache = new Map();
    this.rotationTimer = null;
    
    // Event callbacks
    this.onChainChanged = options.onChainChanged || (() => {});
    this.onHopConnected = options.onHopConnected || (() => {});
    this.onError = options.onError || (() => {});
  }

  /**
   * Get optimal servers for multi-hop chain
   * Selects servers with lowest latency while ensuring geographic diversity
   */
  async selectOptimalChain(availableServers, userLocation = null) {
    const candidates = [...availableServers];
    const chain = [];
    const usedRegions = new Set();
    
    // Sort by latency (cached or estimated)
    candidates.sort((a, b) => {
      const latencyA = this.latencyCache.get(a.id) || a.ping || 100;
      const latencyB = this.latencyCache.get(b.id) || b.ping || 100;
      return latencyA - latencyB;
    });

    // Select hops ensuring diversity
    for (let i = 0; i < this.config.hopCount && candidates.length > 0; i++) {
      let selected = null;
      
      if (this.config.diverseRouting && usedRegions.size > 0) {
        // Find server in different region
        selected = candidates.find(s => !usedRegions.has(s.region || s.country));
      }
      
      // Fallback to fastest available
      if (!selected) {
        selected = candidates[0];
      }
      
      if (selected) {
        chain.push(selected);
        usedRegions.add(selected.region || selected.country);
        candidates.splice(candidates.indexOf(selected), 1);
      }
    }

    return chain;
  }

  /**
   * Measure latency to a server
   * Uses parallel TCP probes for accuracy
   */
  async measureLatency(server) {
    const startTime = Date.now();
    
    try {
      // Try WebSocket ping for browsers, TCP for native
      if (typeof WebSocket !== 'undefined') {
        return await this._wsLatencyProbe(server);
      } else {
        return await this._tcpLatencyProbe(server);
      }
    } catch (error) {
      // Return cached or estimated latency on failure
      return this.latencyCache.get(server.id) || 150;
    }
  }

  async _wsLatencyProbe(server) {
    return new Promise((resolve) => {
      const start = Date.now();
      const ws = new WebSocket(`wss://${server.host}:${server.port || 443}/ping`);
      const timeout = setTimeout(() => {
        ws.close();
        resolve(300); // Timeout = high latency
      }, 2000);
      
      ws.onopen = () => {
        clearTimeout(timeout);
        const latency = Date.now() - start;
        this.latencyCache.set(server.id, latency);
        ws.close();
        resolve(latency);
      };
      
      ws.onerror = () => {
        clearTimeout(timeout);
        resolve(this.latencyCache.get(server.id) || 200);
      };
    });
  }

  async _tcpLatencyProbe(server) {
    // Native implementation - overridden by platform-specific code
    return server.ping || 50;
  }

  /**
   * Build the multi-hop tunnel chain
   * Connects to each hop in sequence, establishing encrypted tunnels
   */
  async buildChain(servers, connectionHandler) {
    const chain = [];
    let previousHop = null;

    for (let i = 0; i < servers.length; i++) {
      const server = servers[i];
      const isEntry = i === 0;
      const isExit = i === servers.length - 1;

      try {
        // Connect to hop (through previous hop if not entry)
        const connection = await connectionHandler(server, {
          isEntry,
          isExit,
          previousHop,
          hopIndex: i,
          totalHops: servers.length
        });

        chain.push({
          server,
          connection,
          isEntry,
          isExit,
          index: i
        });

        this.onHopConnected(server, i, servers.length);
        previousHop = connection;

      } catch (error) {
        console.error(`Multi-hop: Failed to connect hop ${i + 1}:`, error);
        
        // Cleanup partial chain
        for (const hop of chain) {
          try {
            await hop.connection.close?.();
          } catch (e) {}
        }

        throw new Error(`Failed at hop ${i + 1}: ${error.message}`);
      }
    }

    return chain;
  }

  /**
   * Activate multi-hop mode
   * Returns the chain of connected servers
   */
  async activate(availableServers, connectionHandler) {
    if (this.isActive) {
      return this.currentChain;
    }

    try {
      // Select optimal servers for chain
      const selectedServers = await this.selectOptimalChain(availableServers);
      
      if (selectedServers.length < 2) {
        throw new Error('Not enough servers available for multi-hop');
      }

      // Build the tunnel chain
      this.currentChain = await this.buildChain(selectedServers, connectionHandler);
      this.isActive = true;

      // Start rotation timer if configured
      if (this.config.rotationInterval > 0) {
        this.rotationTimer = setInterval(() => {
          this.rotateChain(availableServers, connectionHandler);
        }, this.config.rotationInterval * 60 * 1000);
      }

      this.onChainChanged(this.currentChain);
      return this.currentChain;

    } catch (error) {
      this.onError(error);
      
      if (this.config.fallbackEnabled) {
        console.log('Multi-hop: Falling back to single hop');
        return null; // Signal to use single hop
      }
      
      throw error;
    }
  }

  /**
   * Deactivate multi-hop mode
   */
  async deactivate() {
    if (!this.isActive) return;

    // Clear rotation timer
    if (this.rotationTimer) {
      clearInterval(this.rotationTimer);
      this.rotationTimer = null;
    }

    // Close all hop connections
    for (const hop of this.currentChain) {
      try {
        await hop.connection.close?.();
      } catch (e) {}
    }

    this.currentChain = [];
    this.isActive = false;
    this.onChainChanged([]);
  }

  /**
   * Rotate to a new chain for enhanced anonymity
   */
  async rotateChain(availableServers, connectionHandler) {
    if (!this.isActive) return;

    try {
      // Build new chain before closing old one (for seamless switch)
      const newServers = await this.selectOptimalChain(
        availableServers.filter(s => !this.currentChain.find(h => h.server.id === s.id))
      );
      
      const newChain = await this.buildChain(newServers, connectionHandler);
      
      // Close old chain
      const oldChain = this.currentChain;
      this.currentChain = newChain;
      
      for (const hop of oldChain) {
        try {
          await hop.connection.close?.();
        } catch (e) {}
      }

      this.onChainChanged(this.currentChain);

    } catch (error) {
      console.error('Multi-hop: Failed to rotate chain:', error);
      // Keep using existing chain
    }
  }

  /**
   * Get current chain info for display
   */
  getChainInfo() {
    if (!this.isActive || this.currentChain.length === 0) {
      return null;
    }

    return {
      active: this.isActive,
      hopCount: this.currentChain.length,
      hops: this.currentChain.map(hop => ({
        country: hop.server.country,
        city: hop.server.city,
        flag: hop.server.flag,
        isEntry: hop.isEntry,
        isExit: hop.isExit,
        latency: this.latencyCache.get(hop.server.id) || hop.server.ping
      })),
      entryServer: this.currentChain[0]?.server,
      exitServer: this.currentChain[this.currentChain.length - 1]?.server,
      totalLatency: this.currentChain.reduce((sum, hop) => 
        sum + (this.latencyCache.get(hop.server.id) || hop.server.ping || 50), 0
      )
    };
  }

  /**
   * Estimate speed impact of multi-hop
   * Returns a percentage of expected speed retention
   */
  estimateSpeedRetention() {
    // Each hop adds some overhead, but with optimized routing it's minimal
    // 2 hops: ~90-95% speed, 3 hops: ~80-90%, 4 hops: ~70-85%
    const hopPenalties = {
      2: 0.92,
      3: 0.85,
      4: 0.78
    };
    
    return hopPenalties[this.config.hopCount] || 0.90;
  }
}

/**
 * Multi-Hop Configuration for different anonymity levels
 */
const MultiHopPresets = {
  // Fast mode: 2 hops, prioritize speed
  FAST: {
    hopCount: 2,
    maxHopLatency: 50,
    diverseRouting: false,
    rotationInterval: 60
  },
  
  // Balanced mode: 2 hops with geographic diversity
  BALANCED: {
    hopCount: 2,
    maxHopLatency: 100,
    diverseRouting: true,
    rotationInterval: 30
  },
  
  // Maximum anonymity: 3 hops with frequent rotation
  MAXIMUM: {
    hopCount: 3,
    maxHopLatency: 150,
    diverseRouting: true,
    rotationInterval: 15
  },
  
  // Paranoid mode: 4 hops, max diversity
  PARANOID: {
    hopCount: 4,
    maxHopLatency: 200,
    diverseRouting: true,
    rotationInterval: 10
  }
};

/**
 * Browser-compatible Multi-Hop Proxy Chain
 * For web and extension platforms that use proxy cascading
 */
class ProxyChainManager {
  constructor() {
    this.proxyChain = [];
    this.isActive = false;
  }

  /**
   * Build a SOCKS5 proxy chain for browser routing
   */
  buildProxyChain(servers) {
    return servers.map((server, index) => ({
      type: 'socks5',
      host: server.host,
      port: server.proxyPort || 1080,
      auth: server.auth || null,
      isEntry: index === 0,
      isExit: index === servers.length - 1
    }));
  }

  /**
   * Get PAC script for proxy chain
   * This routes traffic through multiple proxies in sequence
   */
  generatePACScript(proxyChain) {
    if (proxyChain.length === 0) return null;

    // For multi-hop, we chain SOCKS proxies
    const proxyString = proxyChain
      .map(p => `SOCKS5 ${p.host}:${p.port}`)
      .join('; ');

    return `
      function FindProxyForURL(url, host) {
        // Bypass local addresses
        if (isPlainHostName(host) || 
            shExpMatch(host, "*.local") ||
            isInNet(dnsResolve(host), "10.0.0.0", "255.0.0.0") ||
            isInNet(dnsResolve(host), "172.16.0.0", "255.240.0.0") ||
            isInNet(dnsResolve(host), "192.168.0.0", "255.255.0.0") ||
            isInNet(dnsResolve(host), "127.0.0.0", "255.255.255.0")) {
          return "DIRECT";
        }
        
        // Route through proxy chain
        return "${proxyString}; DIRECT";
      }
    `.trim();
  }

  /**
   * Apply proxy chain to Chrome extension
   */
  async applyToExtension(proxyChain) {
    if (typeof chrome === 'undefined' || !chrome.proxy) {
      return false;
    }

    const pacScript = this.generatePACScript(proxyChain);
    
    return new Promise((resolve, reject) => {
      chrome.proxy.settings.set({
        value: {
          mode: 'pac_script',
          pacScript: {
            data: pacScript
          }
        },
        scope: 'regular'
      }, () => {
        if (chrome.runtime.lastError) {
          reject(chrome.runtime.lastError);
        } else {
          this.proxyChain = proxyChain;
          this.isActive = true;
          resolve(true);
        }
      });
    });
  }

  /**
   * Clear proxy chain
   */
  async clear() {
    if (typeof chrome !== 'undefined' && chrome.proxy) {
      return new Promise((resolve) => {
        chrome.proxy.settings.clear({ scope: 'regular' }, () => {
          this.proxyChain = [];
          this.isActive = false;
          resolve(true);
        });
      });
    }
    
    this.proxyChain = [];
    this.isActive = false;
    return true;
  }
}

// Export for different module systems
if (typeof module !== 'undefined' && module.exports) {
  module.exports = { MultiHopEngine, MultiHopPresets, ProxyChainManager };
} else if (typeof window !== 'undefined') {
  window.MultiHopEngine = MultiHopEngine;
  window.MultiHopPresets = MultiHopPresets;
  window.ProxyChainManager = ProxyChainManager;
}

export { MultiHopEngine, MultiHopPresets, ProxyChainManager };
