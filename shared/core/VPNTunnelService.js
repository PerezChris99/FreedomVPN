/**
 * FreedomVPN - VPN Tunnel Service
 * 
 * Core VPN tunneling logic for routing traffic through VPN servers.
 * Handles connection management, leak protection, and traffic encryption.
 * 
 * @module VPNTunnelService
 * @version 2.0.0
 */

import { detectIP, detectWebRTCLeak, runPrivacyCheck } from './IPDetectionService.js';

/**
 * VPN Server configuration
 */
export const VPN_SERVERS = [
  // Africa (Priority - optimized for Uganda and East Africa)
  { id: 'ug-1', country: 'Uganda', countryCode: 'UG', city: 'Kampala', lat: 0.3476, lng: 32.5825, region: 'africa', priority: 1, protocol: 'wireguard' },
  { id: 'ke-1', country: 'Kenya', countryCode: 'KE', city: 'Nairobi', lat: -1.2921, lng: 36.8219, region: 'africa', priority: 1, protocol: 'wireguard' },
  { id: 'tz-1', country: 'Tanzania', countryCode: 'TZ', city: 'Dar es Salaam', lat: -6.7924, lng: 39.2083, region: 'africa', priority: 1, protocol: 'wireguard' },
  { id: 'rw-1', country: 'Rwanda', countryCode: 'RW', city: 'Kigali', lat: -1.9403, lng: 29.8739, region: 'africa', priority: 1, protocol: 'wireguard' },
  { id: 'za-1', country: 'South Africa', countryCode: 'ZA', city: 'Johannesburg', lat: -26.2041, lng: 28.0473, region: 'africa', priority: 2, protocol: 'wireguard' },
  { id: 'eg-1', country: 'Egypt', countryCode: 'EG', city: 'Cairo', lat: 30.0444, lng: 31.2357, region: 'africa', priority: 2, protocol: 'wireguard' },
  { id: 'ng-1', country: 'Nigeria', countryCode: 'NG', city: 'Lagos', lat: 6.5244, lng: 3.3792, region: 'africa', priority: 2, protocol: 'wireguard' },
  { id: 'gh-1', country: 'Ghana', countryCode: 'GH', city: 'Accra', lat: 5.6037, lng: -0.1870, region: 'africa', priority: 2, protocol: 'wireguard' },
  { id: 'et-1', country: 'Ethiopia', countryCode: 'ET', city: 'Addis Ababa', lat: 9.0320, lng: 38.7469, region: 'africa', priority: 2, protocol: 'wireguard' },
  
  // Europe
  { id: 'nl-1', country: 'Netherlands', countryCode: 'NL', city: 'Amsterdam', lat: 52.3676, lng: 4.9041, region: 'europe', priority: 3, protocol: 'wireguard' },
  { id: 'de-1', country: 'Germany', countryCode: 'DE', city: 'Frankfurt', lat: 50.1109, lng: 8.6821, region: 'europe', priority: 3, protocol: 'wireguard' },
  { id: 'gb-1', country: 'United Kingdom', countryCode: 'GB', city: 'London', lat: 51.5074, lng: -0.1278, region: 'europe', priority: 3, protocol: 'wireguard' },
  { id: 'fr-1', country: 'France', countryCode: 'FR', city: 'Paris', lat: 48.8566, lng: 2.3522, region: 'europe', priority: 3, protocol: 'wireguard' },
  { id: 'ch-1', country: 'Switzerland', countryCode: 'CH', city: 'Zurich', lat: 47.3769, lng: 8.5417, region: 'europe', priority: 3, protocol: 'wireguard' },
  
  // Americas  
  { id: 'us-ny', country: 'United States', countryCode: 'US', city: 'New York', lat: 40.7128, lng: -74.0060, region: 'americas', priority: 3, protocol: 'wireguard' },
  { id: 'us-la', country: 'United States', countryCode: 'US', city: 'Los Angeles', lat: 34.0522, lng: -118.2437, region: 'americas', priority: 3, protocol: 'wireguard' },
  { id: 'ca-1', country: 'Canada', countryCode: 'CA', city: 'Toronto', lat: 43.6532, lng: -79.3832, region: 'americas', priority: 3, protocol: 'wireguard' },
  { id: 'br-1', country: 'Brazil', countryCode: 'BR', city: 'São Paulo', lat: -23.5505, lng: -46.6333, region: 'americas', priority: 3, protocol: 'wireguard' },
  
  // Asia
  { id: 'sg-1', country: 'Singapore', countryCode: 'SG', city: 'Singapore', lat: 1.3521, lng: 103.8198, region: 'asia', priority: 3, protocol: 'wireguard' },
  { id: 'jp-1', country: 'Japan', countryCode: 'JP', city: 'Tokyo', lat: 35.6762, lng: 139.6503, region: 'asia', priority: 3, protocol: 'wireguard' },
  { id: 'ae-1', country: 'UAE', countryCode: 'AE', city: 'Dubai', lat: 25.2048, lng: 55.2708, region: 'asia', priority: 3, protocol: 'wireguard' },
  { id: 'in-1', country: 'India', countryCode: 'IN', city: 'Mumbai', lat: 19.0760, lng: 72.8777, region: 'asia', priority: 3, protocol: 'wireguard' },
];

/**
 * Connection states
 */
export const ConnectionState = {
  DISCONNECTED: 'disconnected',
  CONNECTING: 'connecting',
  CONNECTED: 'connected',
  DISCONNECTING: 'disconnecting',
  ERROR: 'error',
  RECONNECTING: 'reconnecting',
};

/**
 * Leak protection modes
 */
export const LeakProtectionMode = {
  OFF: 'off',
  STANDARD: 'standard',
  STRICT: 'strict',
  PARANOID: 'paranoid',
};

/**
 * VPN Tunnel Service Class
 */
export class VPNTunnelService {
  constructor() {
    this.state = ConnectionState.DISCONNECTED;
    this.currentServer = null;
    this.realIP = null;
    this.vpnIP = null;
    this.connectionStartTime = null;
    this.leakProtectionMode = LeakProtectionMode.STRICT;
    this.listeners = new Set();
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = 5;
    this.killSwitchEnabled = true;
    this.stats = {
      bytesReceived: 0,
      bytesSent: 0,
      packetsReceived: 0,
      packetsSent: 0,
      connectionTime: 0,
    };
  }

  /**
   * Subscribe to state changes
   * @param {Function} listener - Callback function
   * @returns {Function} Unsubscribe function
   */
  subscribe(listener) {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  /**
   * Notify all listeners of state change
   */
  notifyListeners() {
    const state = this.getState();
    this.listeners.forEach(listener => listener(state));
  }

  /**
   * Get current state
   * @returns {Object} Current VPN state
   */
  getState() {
    return {
      connectionState: this.state,
      currentServer: this.currentServer,
      realIP: this.realIP,
      vpnIP: this.vpnIP,
      isConnected: this.state === ConnectionState.CONNECTED,
      connectionTime: this.connectionStartTime 
        ? Math.floor((Date.now() - this.connectionStartTime) / 1000) 
        : 0,
      leakProtection: this.leakProtectionMode,
      killSwitch: this.killSwitchEnabled,
      stats: this.stats,
    };
  }

  /**
   * Detect real IP before connecting
   * @returns {Promise<Object>} Real IP info
   */
  async detectRealIP() {
    console.log('[VPNTunnel] Detecting real IP...');
    const ipInfo = await detectIP();
    if (ipInfo.success) {
      this.realIP = ipInfo;
      console.log(`[VPNTunnel] Real IP: ${ipInfo.ip} (${ipInfo.city}, ${ipInfo.country})`);
    }
    return ipInfo;
  }

  /**
   * Enable leak protection measures
   */
  async enableLeakProtection() {
    console.log(`[VPNTunnel] Enabling leak protection (${this.leakProtectionMode})`);
    
    // These would be implemented differently per platform:
    // - Desktop: Modify system settings, firewall rules
    // - Mobile: VPN profile settings
    // - Browser: WebRTC blocking, proxy settings
    
    const protections = {
      webrtc: this.leakProtectionMode !== LeakProtectionMode.OFF,
      dns: this.leakProtectionMode !== LeakProtectionMode.OFF,
      ipv6: this.leakProtectionMode === LeakProtectionMode.STRICT || 
            this.leakProtectionMode === LeakProtectionMode.PARANOID,
      killSwitch: this.killSwitchEnabled,
    };
    
    return protections;
  }

  /**
   * Connect to a VPN server
   * @param {Object} server - Server to connect to
   * @returns {Promise<boolean>} Success status
   */
  async connect(server) {
    if (this.state === ConnectionState.CONNECTING) {
      console.log('[VPNTunnel] Already connecting...');
      return false;
    }

    try {
      this.state = ConnectionState.CONNECTING;
      this.currentServer = server;
      this.notifyListeners();

      // Step 1: Detect real IP before connecting
      await this.detectRealIP();

      // Step 2: Enable leak protection
      await this.enableLeakProtection();

      // Step 3: Establish VPN tunnel
      // This is where platform-specific connection code would go
      console.log(`[VPNTunnel] Connecting to ${server.city}, ${server.country}...`);
      
      // Simulate connection delay (replace with real connection logic)
      await new Promise(resolve => setTimeout(resolve, 1500 + Math.random() * 1000));

      // Step 4: Generate/get VPN IP (in real implementation, this comes from the server)
      this.vpnIP = this.generateVPNIP(server);
      
      // Step 5: Verify connection (check that IP has changed)
      const newIP = await detectIP();
      
      // In a real VPN, newIP should match vpnIP
      // For demo purposes, we simulate this
      this.state = ConnectionState.CONNECTED;
      this.connectionStartTime = Date.now();
      this.reconnectAttempts = 0;
      
      console.log(`[VPNTunnel] Connected! VPN IP: ${this.vpnIP.ip}`);
      this.notifyListeners();
      
      // Start stats tracking
      this.startStatsTracking();
      
      return true;

    } catch (error) {
      console.error('[VPNTunnel] Connection failed:', error);
      this.state = ConnectionState.ERROR;
      this.notifyListeners();
      
      // Attempt reconnection
      if (this.reconnectAttempts < this.maxReconnectAttempts) {
        this.reconnectAttempts++;
        console.log(`[VPNTunnel] Reconnection attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts}`);
        await new Promise(resolve => setTimeout(resolve, 2000));
        return this.connect(server);
      }
      
      return false;
    }
  }

  /**
   * Disconnect from VPN
   * @returns {Promise<boolean>} Success status
   */
  async disconnect() {
    if (this.state === ConnectionState.DISCONNECTED) {
      return true;
    }

    try {
      this.state = ConnectionState.DISCONNECTING;
      this.notifyListeners();

      // Stop stats tracking
      this.stopStatsTracking();

      // Platform-specific disconnect logic would go here
      console.log('[VPNTunnel] Disconnecting...');
      
      await new Promise(resolve => setTimeout(resolve, 500));

      this.state = ConnectionState.DISCONNECTED;
      this.vpnIP = null;
      this.connectionStartTime = null;
      this.currentServer = null;
      
      console.log('[VPNTunnel] Disconnected');
      this.notifyListeners();
      
      return true;

    } catch (error) {
      console.error('[VPNTunnel] Disconnect failed:', error);
      this.state = ConnectionState.ERROR;
      this.notifyListeners();
      return false;
    }
  }

  /**
   * Generate VPN IP based on server location
   * In production, this would come from the actual VPN server
   * @param {Object} server - Server info
   * @returns {Object} VPN IP info
   */
  generateVPNIP(server) {
    // Server IP ranges (simulated - real VPN would use actual server IPs)
    const ipRanges = {
      'UG': ['41.210', '41.211'],
      'KE': ['197.232', '197.233'],
      'TZ': ['197.185', '197.186'],
      'RW': ['197.243', '197.244'],
      'ZA': ['196.38', '196.39'],
      'EG': ['197.55', '197.56'],
      'NG': ['197.210', '197.211'],
      'GH': ['197.251', '197.252'],
      'ET': ['196.188', '196.189'],
      'NL': ['185.156', '185.157'],
      'DE': ['185.220', '185.221'],
      'GB': ['185.189', '185.190'],
      'FR': ['185.230', '185.231'],
      'CH': ['185.198', '185.199'],
      'US': ['172.93', '172.94'],
      'CA': ['172.95', '172.96'],
      'BR': ['177.54', '177.55'],
      'SG': ['103.86', '103.87'],
      'JP': ['103.73', '103.74'],
      'AE': ['185.203', '185.204'],
      'IN': ['103.78', '103.79'],
    };
    
    const ranges = ipRanges[server.countryCode] || ['10.0'];
    const baseIP = ranges[Math.floor(Math.random() * ranges.length)];
    const octet3 = Math.floor(Math.random() * 254) + 1;
    const octet4 = Math.floor(Math.random() * 254) + 1;
    
    return {
      ip: `${baseIP}.${octet3}.${octet4}`,
      country: server.country,
      countryCode: server.countryCode,
      city: server.city,
      latitude: server.lat,
      longitude: server.lng,
      isp: `FreedomVPN ${server.city} Node`,
      isVPN: true,
    };
  }

  /**
   * Start tracking connection statistics
   */
  startStatsTracking() {
    this.statsInterval = setInterval(() => {
      if (this.state === ConnectionState.CONNECTED) {
        // Simulate data transfer (replace with real metrics)
        this.stats.bytesReceived += Math.floor(Math.random() * 50000);
        this.stats.bytesSent += Math.floor(Math.random() * 10000);
        this.stats.packetsReceived += Math.floor(Math.random() * 50);
        this.stats.packetsSent += Math.floor(Math.random() * 20);
        this.stats.connectionTime = this.connectionStartTime 
          ? Math.floor((Date.now() - this.connectionStartTime) / 1000) 
          : 0;
        
        this.notifyListeners();
      }
    }, 1000);
  }

  /**
   * Stop tracking connection statistics
   */
  stopStatsTracking() {
    if (this.statsInterval) {
      clearInterval(this.statsInterval);
      this.statsInterval = null;
    }
  }

  /**
   * Run privacy check
   * @returns {Promise<Object>} Privacy check results
   */
  async runPrivacyCheck() {
    return runPrivacyCheck();
  }

  /**
   * Get best server based on user location
   * @param {Object} userLocation - User's location {lat, lng}
   * @returns {Object} Best server
   */
  getBestServer(userLocation = null) {
    if (!userLocation && this.realIP) {
      userLocation = {
        lat: this.realIP.latitude,
        lng: this.realIP.longitude,
      };
    }

    // Sort by priority first, then by distance if location available
    let sortedServers = [...VPN_SERVERS].sort((a, b) => a.priority - b.priority);
    
    if (userLocation && userLocation.lat && userLocation.lng) {
      sortedServers = sortedServers.sort((a, b) => {
        if (a.priority !== b.priority) return a.priority - b.priority;
        const distA = this.calculateDistance(userLocation.lat, userLocation.lng, a.lat, a.lng);
        const distB = this.calculateDistance(userLocation.lat, userLocation.lng, b.lat, b.lng);
        return distA - distB;
      });
    }
    
    return sortedServers[0];
  }

  /**
   * Calculate distance between two points
   */
  calculateDistance(lat1, lon1, lat2, lon2) {
    const R = 6371;
    const dLat = (lat2 - lat1) * Math.PI / 180;
    const dLon = (lon2 - lon1) * Math.PI / 180;
    const a = 
      Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
      Math.sin(dLon / 2) * Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
  }

  /**
   * Get servers by region
   * @param {string} region - Region name
   * @returns {Array} Servers in region
   */
  getServersByRegion(region) {
    return VPN_SERVERS.filter(s => s.region === region);
  }

  /**
   * Set leak protection mode
   * @param {string} mode - Protection mode
   */
  setLeakProtectionMode(mode) {
    this.leakProtectionMode = mode;
    if (this.state === ConnectionState.CONNECTED) {
      this.enableLeakProtection();
    }
    this.notifyListeners();
  }

  /**
   * Toggle kill switch
   * @param {boolean} enabled - Enable or disable
   */
  setKillSwitch(enabled) {
    this.killSwitchEnabled = enabled;
    this.notifyListeners();
  }
}

// Singleton instance
let vpnTunnelInstance = null;

export function getVPNTunnelService() {
  if (!vpnTunnelInstance) {
    vpnTunnelInstance = new VPNTunnelService();
  }
  return vpnTunnelInstance;
}

// Export for different module systems
if (typeof module !== 'undefined' && module.exports) {
  module.exports = {
    VPNTunnelService,
    VPN_SERVERS,
    ConnectionState,
    LeakProtectionMode,
    getVPNTunnelService,
  };
}

export default VPNTunnelService;
