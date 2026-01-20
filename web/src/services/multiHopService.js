/**
 * Multi-Hop Service for FreedomVPN Web App
 * 
 * Provides server-bouncing through proxy chains for enhanced anonymity.
 * For browser-based routing only (not system-wide).
 */

// Server configuration (mirrored from shared/config.js for build compatibility)
const SERVERS = [
  // Africa (Priority - closest to Uganda)
  { id: 'ug', country: 'Uganda', city: 'Kampala', region: 'africa', flag: '🇺🇬', ping: 15 },
  { id: 'ke', country: 'Kenya', city: 'Nairobi', region: 'africa', flag: '🇰🇪', ping: 25 },
  { id: 'tz', country: 'Tanzania', city: 'Dar es Salaam', region: 'africa', flag: '🇹🇿', ping: 35 },
  { id: 'rw', country: 'Rwanda', city: 'Kigali', region: 'africa', flag: '🇷🇼', ping: 30 },
  { id: 'za', country: 'South Africa', city: 'Johannesburg', region: 'africa', flag: '🇿🇦', ping: 80 },
  { id: 'eg', country: 'Egypt', city: 'Cairo', region: 'africa', flag: '🇪🇬', ping: 90 },
  { id: 'ng', country: 'Nigeria', city: 'Lagos', region: 'africa', flag: '🇳🇬', ping: 100 },
  { id: 'gh', country: 'Ghana', city: 'Accra', region: 'africa', flag: '🇬🇭', ping: 95 },
  // Europe
  { id: 'nl', country: 'Netherlands', city: 'Amsterdam', region: 'europe', flag: '🇳🇱', ping: 120 },
  { id: 'de', country: 'Germany', city: 'Frankfurt', region: 'europe', flag: '🇩🇪', ping: 130 },
  { id: 'gb', country: 'United Kingdom', city: 'London', region: 'europe', flag: '🇬🇧', ping: 140 },
  { id: 'fr', country: 'France', city: 'Paris', region: 'europe', flag: '🇫🇷', ping: 135 },
  { id: 'ch', country: 'Switzerland', city: 'Zurich', region: 'europe', flag: '🇨🇭', ping: 130 },
  // Americas
  { id: 'us-ny', country: 'United States', city: 'New York', region: 'americas', flag: '🇺🇸', ping: 180 },
  { id: 'us-la', country: 'United States', city: 'Los Angeles', region: 'americas', flag: '🇺🇸', ping: 200 },
  { id: 'ca', country: 'Canada', city: 'Toronto', region: 'americas', flag: '🇨🇦', ping: 185 },
  { id: 'br', country: 'Brazil', city: 'São Paulo', region: 'americas', flag: '🇧🇷', ping: 220 },
  // Asia
  { id: 'sg', country: 'Singapore', city: 'Singapore', region: 'asia', flag: '🇸🇬', ping: 160 },
  { id: 'jp', country: 'Japan', city: 'Tokyo', region: 'asia', flag: '🇯🇵', ping: 170 },
  { id: 'ae', country: 'UAE', city: 'Dubai', region: 'asia', flag: '🇦🇪', ping: 110 },
];

// Preset configurations
export const MultiHopPresets = {
  FAST: {
    hopCount: 2,
    diverseRouting: false,
    rotationMinutes: 60,
    label: 'Fast',
    description: '2 hops, ~90% speed',
    speedRetention: 0.90
  },
  BALANCED: {
    hopCount: 2,
    diverseRouting: true,
    rotationMinutes: 30,
    label: 'Balanced',
    description: '2 diverse hops, ~88% speed',
    speedRetention: 0.88
  },
  MAXIMUM: {
    hopCount: 3,
    diverseRouting: true,
    rotationMinutes: 15,
    label: 'Maximum',
    description: '3 hops, ~80% speed',
    speedRetention: 0.80
  },
  PARANOID: {
    hopCount: 4,
    diverseRouting: true,
    rotationMinutes: 10,
    label: 'Paranoid',
    description: '4 hops, ~70% speed',
    speedRetention: 0.70
  }
};

class MultiHopService {
  constructor() {
    this.isEnabled = false;
    this.currentChain = [];
    this.preset = MultiHopPresets.BALANCED;
    this.rotationTimer = null;
    this.listeners = new Set();
  }

  /**
   * Subscribe to multi-hop state changes
   */
  subscribe(callback) {
    this.listeners.add(callback);
    return () => this.listeners.delete(callback);
  }

  /**
   * Notify all listeners of state change
   */
  notify() {
    const state = this.getState();
    this.listeners.forEach(cb => cb(state));
  }

  /**
   * Get current state
   */
  getState() {
    return {
      isEnabled: this.isEnabled,
      chain: this.currentChain,
      preset: this.preset,
      hopCount: this.currentChain.length,
      entryServer: this.currentChain[0] || null,
      exitServer: this.currentChain[this.currentChain.length - 1] || null,
      speedRetention: this.preset.speedRetention
    };
  }

  /**
   * Set preset level
   */
  setPreset(presetKey) {
    if (MultiHopPresets[presetKey]) {
      this.preset = MultiHopPresets[presetKey];
      
      // If enabled, rebuild chain with new hop count
      if (this.isEnabled) {
        this.buildChain();
      }
      
      this.notify();
    }
  }

  /**
   * Select optimal servers for multi-hop chain
   */
  selectOptimalServers(hopCount, diverseRouting) {
    const serverList = Object.entries(SERVERS).map(([id, server]) => ({
      id,
      ...server
    }));
    
    // Sort by ping/latency (lower is better)
    serverList.sort((a, b) => (a.ping || 100) - (b.ping || 100));
    
    const chain = [];
    const usedRegions = new Set();
    
    for (let i = 0; i < hopCount && serverList.length > 0; i++) {
      let selected = null;
      
      if (diverseRouting && usedRegions.size > 0) {
        // Find server in different region
        const index = serverList.findIndex(s => !usedRegions.has(s.region));
        if (index !== -1) {
          selected = serverList.splice(index, 1)[0];
        }
      }
      
      // Fallback to fastest
      if (!selected && serverList.length > 0) {
        selected = serverList.shift();
      }
      
      if (selected) {
        chain.push({
          ...selected,
          isEntry: i === 0,
          isExit: i === hopCount - 1,
          hopIndex: i
        });
        usedRegions.add(selected.region);
      }
    }
    
    return chain;
  }

  /**
   * Build the multi-hop chain
   */
  buildChain() {
    this.currentChain = this.selectOptimalServers(
      this.preset.hopCount,
      this.preset.diverseRouting
    );
    console.log('[MultiHop] Chain built:', this.currentChain.map(h => h.country).join(' → '));
  }

  /**
   * Activate multi-hop mode - INSTANT
   */
  activate() {
    if (this.isEnabled) return true;
    
    console.log('[MultiHop] Activating with preset:', this.preset.label);
    
    // Build chain instantly
    this.buildChain();
    
    if (this.currentChain.length < 2) {
      console.error('[MultiHop] Not enough servers for multi-hop');
      return false;
    }
    
    this.isEnabled = true;
    
    // Start rotation timer
    this.startRotation();
    
    this.notify();
    
    console.log('[MultiHop] Activated! Route:', 
      this.currentChain.map(h => `${h.flag} ${h.city}`).join(' → ')
    );
    
    return true;
  }

  /**
   * Deactivate multi-hop mode
   */
  deactivate() {
    console.log('[MultiHop] Deactivating...');
    
    this.isEnabled = false;
    this.currentChain = [];
    
    // Stop rotation
    if (this.rotationTimer) {
      clearInterval(this.rotationTimer);
      this.rotationTimer = null;
    }
    
    this.notify();
  }

  /**
   * Toggle multi-hop mode
   */
  toggle() {
    if (this.isEnabled) {
      this.deactivate();
      return false;
    } else {
      return this.activate();
    }
  }

  /**
   * Rotate to new chain
   */
  rotate() {
    if (!this.isEnabled) return;
    
    console.log('[MultiHop] Rotating chain...');
    
    // Exclude current servers
    const currentIds = new Set(this.currentChain.map(h => h.id));
    const availableServers = Object.entries(SERVERS)
      .filter(([id]) => !currentIds.has(id))
      .map(([id, server]) => ({ id, ...server }));
    
    if (availableServers.length >= this.preset.hopCount) {
      this.buildChain();
      this.notify();
    }
  }

  /**
   * Start periodic rotation
   */
  startRotation() {
    if (this.rotationTimer) {
      clearInterval(this.rotationTimer);
    }
    
    this.rotationTimer = setInterval(() => {
      this.rotate();
    }, this.preset.rotationMinutes * 60 * 1000);
  }

  /**
   * Get chain display string
   */
  getChainDisplay() {
    if (!this.isEnabled || this.currentChain.length === 0) {
      return null;
    }
    
    return this.currentChain.map(h => `${h.flag}`).join(' → ');
  }

  /**
   * Get exit server (your visible IP location)
   */
  getExitServer() {
    return this.currentChain[this.currentChain.length - 1] || null;
  }
}

// Singleton instance
export const multiHopService = new MultiHopService();

export default multiHopService;
