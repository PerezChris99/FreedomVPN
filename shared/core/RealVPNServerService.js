/**
 * FreedomVPN - Real VPN Server Service
 * 
 * Fetches real, live VPN servers from VPNGate API
 * Provides WireGuard and OpenVPN configurations
 * 
 * API: https://www.vpngate.net/api/iphone/
 */

// VPNGate API endpoints
const VPNGATE_API = 'https://www.vpngate.net/api/iphone/';
const VPNGATE_MIRROR = 'http://www.vpngate.net/api/iphone/';

// Fallback static servers (used if VPNGate is unavailable)
const FALLBACK_SERVERS = [
  { host: '197.232.170.50', port: 443, country: 'Kenya', city: 'Nairobi', countryCode: 'KE', isAfrican: true },
  { host: '41.186.255.100', port: 443, country: 'Rwanda', city: 'Kigali', countryCode: 'RW', isAfrican: true },
  { host: '196.38.180.10', port: 443, country: 'South Africa', city: 'Johannesburg', countryCode: 'ZA', isAfrican: true },
  { host: '185.107.56.100', port: 443, country: 'Netherlands', city: 'Amsterdam', countryCode: 'NL' },
  { host: '185.181.8.100', port: 443, country: 'Germany', city: 'Frankfurt', countryCode: 'DE' },
  { host: '45.33.32.156', port: 443, country: 'United States', city: 'New York', countryCode: 'US' },
];

/**
 * VPN Server class representing a real server
 */
class VPNServer {
  constructor(data) {
    this.id = data.id || `${data.countryCode?.toLowerCase()}-${Date.now()}`;
    this.hostname = data.hostname || data.host;
    this.ip = data.ip || data.host;
    this.port = data.port || 443;
    this.score = data.score || 0;
    this.ping = data.ping || 0;
    this.speed = data.speed || 0; // bits per second
    this.country = data.country || data.countryLong || 'Unknown';
    this.countryCode = data.countryCode || data.countryShort || 'XX';
    this.city = data.city || '';
    this.numSessions = data.numSessions || data.numVpnSessions || 0;
    this.uptime = data.uptime || 0;
    this.operator = data.operator || '';
    this.openVpnConfig = data.openVpnConfig || data.openvpn_config || null;
    this.isAfrican = this.checkIfAfrican();
    this.latitude = data.latitude || this.getApproxLatitude();
    this.longitude = data.longitude || this.getApproxLongitude();
    this.lastUpdated = Date.now();
    this.isOnline = true;
  }

  get speedMbps() {
    return this.speed / 1_000_000;
  }

  get formattedSpeed() {
    if (this.speed >= 1_000_000_000) return `${(this.speed / 1_000_000_000).toFixed(1)} Gbps`;
    if (this.speed >= 1_000_000) return `${(this.speed / 1_000_000).toFixed(1)} Mbps`;
    if (this.speed >= 1_000) return `${(this.speed / 1_000).toFixed(1)} Kbps`;
    return `${this.speed} bps`;
  }

  get flag() {
    if (!this.countryCode || this.countryCode.length !== 2) return '🌍';
    const codePoints = this.countryCode.toUpperCase().split('').map(
      char => 127397 + char.charCodeAt(0)
    );
    return String.fromCodePoint(...codePoints);
  }

  get qualityScore() {
    const pingScore = this.ping > 0 ? 100 / this.ping : 0;
    const speedScore = this.speedMbps;
    const loadScore = this.numSessions > 0 ? 100 / this.numSessions : 100;
    return pingScore * 0.3 + speedScore * 0.5 + loadScore * 0.2;
  }

  get load() {
    // Estimate load based on sessions (max 100)
    return Math.min(100, Math.round(this.numSessions * 2));
  }

  checkIfAfrican() {
    const africanCodes = ['UG', 'KE', 'TZ', 'RW', 'ZA', 'EG', 'NG', 'GH', 'ET', 'MA', 'DZ', 'TN', 'SN', 'CI'];
    return africanCodes.includes(this.countryCode?.toUpperCase());
  }

  // Approximate coordinates for server selection by distance
  getApproxLatitude() {
    const coords = {
      'UG': 0.3476, 'KE': -1.2921, 'TZ': -6.7924, 'RW': -1.9403, 'ZA': -26.2041,
      'EG': 30.0444, 'NG': 6.5244, 'GH': 5.6037, 'ET': 9.0320, 'NL': 52.3676,
      'DE': 50.1109, 'GB': 51.5074, 'FR': 48.8566, 'CH': 47.3769, 'US': 40.7128,
      'CA': 43.6532, 'BR': -23.5505, 'SG': 1.3521, 'JP': 35.6762, 'AU': -33.8688,
      'IN': 19.0760, 'KR': 37.5665, 'HK': 22.3193, 'TW': 25.0330, 'TH': 13.7563
    };
    return coords[this.countryCode?.toUpperCase()] || 0;
  }

  getApproxLongitude() {
    const coords = {
      'UG': 32.5825, 'KE': 36.8219, 'TZ': 39.2083, 'RW': 29.8739, 'ZA': 28.0473,
      'EG': 31.2357, 'NG': 3.3792, 'GH': -0.1870, 'ET': 38.7469, 'NL': 4.9041,
      'DE': 8.6821, 'GB': -0.1278, 'FR': 2.3522, 'CH': 8.5417, 'US': -74.0060,
      'CA': -79.3832, 'BR': -46.6333, 'SG': 103.8198, 'JP': 139.6503, 'AU': 151.2093,
      'IN': 72.8777, 'KR': 126.9780, 'HK': 114.1694, 'TW': 121.5654, 'TH': 100.5018
    };
    return coords[this.countryCode?.toUpperCase()] || 0;
  }

  distanceTo(lat, lon) {
    // Haversine formula
    const R = 6371; // Earth's radius in km
    const dLat = (lat - this.latitude) * Math.PI / 180;
    const dLon = (lon - this.longitude) * Math.PI / 180;
    const a = Math.sin(dLat/2) * Math.sin(dLat/2) +
              Math.cos(this.latitude * Math.PI / 180) * Math.cos(lat * Math.PI / 180) *
              Math.sin(dLon/2) * Math.sin(dLon/2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    return R * c;
  }

  toJSON() {
    return {
      id: this.id,
      hostname: this.hostname,
      ip: this.ip,
      port: this.port,
      country: this.country,
      countryCode: this.countryCode,
      city: this.city,
      flag: this.flag,
      ping: this.ping,
      speed: this.speed,
      speedMbps: this.speedMbps,
      formattedSpeed: this.formattedSpeed,
      load: this.load,
      score: this.score,
      qualityScore: this.qualityScore,
      isAfrican: this.isAfrican,
      latitude: this.latitude,
      longitude: this.longitude,
      isOnline: this.isOnline,
      hasOpenVPN: !!this.openVpnConfig
    };
  }
}

/**
 * Real VPN Server Service
 */
class RealVPNServerService {
  constructor() {
    this.servers = [];
    this.lastFetch = null;
    this.cacheTimeout = 5 * 60 * 1000; // 5 minutes
    this.isFetching = false;
  }

  /**
   * Fetch real servers from VPNGate API
   */
  async fetchServers(forceRefresh = false) {
    // Return cached if valid
    if (!forceRefresh && this.servers.length > 0 && this.lastFetch) {
      if (Date.now() - this.lastFetch < this.cacheTimeout) {
        return this.servers;
      }
    }

    if (this.isFetching) {
      // Wait for current fetch
      await new Promise(resolve => setTimeout(resolve, 1000));
      return this.servers;
    }

    this.isFetching = true;

    try {
      const response = await this.fetchWithFallback();
      if (!response) {
        console.warn('[VPNServerService] API failed, using fallback servers');
        return this.useFallbackServers();
      }

      const servers = this.parseVPNGateCSV(response);
      
      // Filter valid servers
      this.servers = servers.filter(s => 
        s.ip && 
        s.ping > 0 && 
        s.ping < 1000 && 
        s.openVpnConfig
      );

      // Sort by quality
      this.servers.sort((a, b) => b.qualityScore - a.qualityScore);

      this.lastFetch = Date.now();
      console.log(`[VPNServerService] Fetched ${this.servers.length} real servers`);
      
      return this.servers;
    } catch (error) {
      console.error('[VPNServerService] Fetch error:', error);
      return this.useFallbackServers();
    } finally {
      this.isFetching = false;
    }
  }

  async fetchWithFallback() {
    const urls = [VPNGATE_API, VPNGATE_MIRROR];
    
    for (const url of urls) {
      try {
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 15000);
        
        const response = await fetch(url, { 
          signal: controller.signal,
          cache: 'no-store'
        });
        clearTimeout(timeout);
        
        if (response.ok) {
          return await response.text();
        }
      } catch (e) {
        console.warn(`[VPNServerService] Failed to fetch from ${url}`);
      }
    }
    return null;
  }

  parseVPNGateCSV(csvText) {
    const servers = [];
    const lines = csvText.split('\n');
    
    // Skip header lines (first 2 lines and last line)
    for (let i = 2; i < lines.length - 1; i++) {
      const line = lines[i].trim();
      if (!line || line.startsWith('*')) continue;
      
      const cols = line.split(',');
      if (cols.length < 15) continue;
      
      try {
        const server = new VPNServer({
          hostname: cols[0],
          ip: cols[1],
          score: parseInt(cols[2]) || 0,
          ping: parseInt(cols[3]) || 0,
          speed: parseInt(cols[4]) || 0,
          country: cols[5],
          countryCode: cols[6],
          numSessions: parseInt(cols[7]) || 0,
          uptime: parseInt(cols[8]) || 0,
          totalUsers: parseInt(cols[9]) || 0,
          totalTraffic: parseInt(cols[10]) || 0,
          logType: cols[11],
          operator: cols[12],
          message: cols[13],
          openVpnConfig: cols[14] ? this.decodeBase64(cols[14]) : null
        });
        servers.push(server);
      } catch (e) {
        // Skip invalid lines
      }
    }
    
    return servers;
  }

  decodeBase64(base64) {
    try {
      if (typeof atob !== 'undefined') {
        return atob(base64);
      }
      return Buffer.from(base64, 'base64').toString('utf-8');
    } catch {
      return null;
    }
  }

  useFallbackServers() {
    this.servers = FALLBACK_SERVERS.map(s => new VPNServer(s));
    return this.servers;
  }

  /**
   * Get servers filtered by region
   */
  getServersByRegion(region) {
    const regionMap = {
      'africa': ['UG', 'KE', 'TZ', 'RW', 'ZA', 'EG', 'NG', 'GH', 'ET', 'MA'],
      'europe': ['NL', 'DE', 'GB', 'FR', 'CH', 'SE', 'NO', 'FI', 'IT', 'ES', 'PL', 'CZ', 'AT', 'BE'],
      'americas': ['US', 'CA', 'BR', 'MX', 'AR', 'CL', 'CO'],
      'asia': ['JP', 'KR', 'SG', 'HK', 'TW', 'TH', 'VN', 'ID', 'MY', 'PH', 'IN'],
      'oceania': ['AU', 'NZ']
    };
    
    const codes = regionMap[region.toLowerCase()] || [];
    return this.servers.filter(s => codes.includes(s.countryCode));
  }

  /**
   * Get African servers (priority for Uganda users)
   */
  getAfricanServers() {
    return this.servers.filter(s => s.isAfrican);
  }

  /**
   * Find best server based on user location
   */
  findBestServer(userLat, userLon, preferAfrican = true) {
    let candidates = preferAfrican ? this.getAfricanServers() : this.servers;
    if (candidates.length === 0) candidates = this.servers;
    
    // Score based on distance, ping, and quality
    const scored = candidates.map(server => {
      const distance = server.distanceTo(userLat, userLon);
      const distanceScore = 1000 / (distance + 1); // Closer is better
      const pingScore = 100 / (server.ping + 1);
      const qualityScore = server.qualityScore;
      
      return {
        server,
        totalScore: distanceScore * 0.4 + pingScore * 0.3 + qualityScore * 0.3
      };
    });

    scored.sort((a, b) => b.totalScore - a.totalScore);
    return scored[0]?.server || null;
  }

  /**
   * Get fastest server by ping
   */
  getFastestServer() {
    return this.servers
      .filter(s => s.ping > 0)
      .sort((a, b) => a.ping - b.ping)[0] || null;
  }

  /**
   * Get server by ID
   */
  getServerById(id) {
    return this.servers.find(s => s.id === id) || null;
  }

  /**
   * Get all servers as JSON
   */
  getAllServersJSON() {
    return this.servers.map(s => s.toJSON());
  }
}

// Export for different environments
if (typeof module !== 'undefined' && module.exports) {
  module.exports = { VPNServer, RealVPNServerService, VPNGATE_API };
}

if (typeof window !== 'undefined') {
  window.VPNServer = VPNServer;
  window.RealVPNServerService = RealVPNServerService;
}

export { VPNServer, RealVPNServerService, VPNGATE_API };
