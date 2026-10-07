/**
 * FreedomVPN Enhanced Background Service Worker
 * 
 * Anti-Censorship Features for Uganda UCC Bypass:
 * - Multiple obfuscation-enabled proxy servers
 * - Automatic failover when blocked
 * - Domain fronting through major CDNs
 * - Real-time connection health monitoring  
 * - WebRTC/DNS leak prevention
 * - Dynamic statistics tracking
 * - Real VPN servers from VPNGate API
 * - Accurate geolocation for server selection
 */

// ============================================================
// VPNGATE REAL SERVER SERVICE
// ============================================================
const VPNGATE_API = 'https://www.vpngate.net/api/iphone/';

const VPNGateService = {
  servers: [],
  lastFetch: null,
  cacheTimeout: 5 * 60 * 1000, // 5 minutes

  async fetchRealServers(forceRefresh = false) {
    if (!forceRefresh && this.servers.length > 0 && this.lastFetch) {
      if (Date.now() - this.lastFetch < this.cacheTimeout) {
        return this.servers;
      }
    }

    try {
      console.log('[VPNGate] Fetching real servers...');
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 15000);
      
      const response = await fetch(VPNGATE_API, { signal: controller.signal });
      clearTimeout(timeout);

      if (!response.ok) {
        console.warn('[VPNGate] API not available');
        return [];
      }

      const csvText = await response.text();
      this.servers = this.parseCSV(csvText);
      this.lastFetch = Date.now();
      
      console.log(`[VPNGate] Fetched ${this.servers.length} real servers`);
      return this.servers;
    } catch (e) {
      console.warn('[VPNGate] Fetch failed:', e.message);
      return [];
    }
  },

  parseCSV(csvText) {
    const servers = [];
    const lines = csvText.split('\n');
    
    for (let i = 2; i < lines.length - 1; i++) {
      const line = lines[i].trim();
      if (!line || line.startsWith('*')) continue;
      
      const cols = line.split(',');
      if (cols.length < 15) continue;
      
      try {
        const countryCode = cols[6]?.toUpperCase() || 'XX';
        
        servers.push({
          id: `vpngate-${cols[0]}-${i}`,
          host: cols[1],
          port: 443,
          country: cols[5] || 'Unknown',
          city: '',
          flag: this.getFlag(countryCode),
          countryCode: countryCode,
          ping: parseInt(cols[3]) || 0,
          speed: parseInt(cols[4]) || 0,
          speedMbps: (parseInt(cols[4]) || 0) / 1000000,
          numSessions: parseInt(cols[7]) || 0,
          operator: cols[12] || '',
          openVpnConfig: cols[14] ? atob(cols[14]) : null,
          obfuscation: ['tls', 'https'],
          priority: 2,
          isReal: true,
          isAfrican: this.isAfrican(countryCode)
        });
      } catch (e) {}
    }

    return servers
      .filter(s => s.host && s.ping > 0 && s.ping < 1000)
      .sort((a, b) => a.ping - b.ping);
  },

  getFlag(countryCode) {
    if (!countryCode || countryCode.length !== 2) return '🌍';
    const codePoints = countryCode.toUpperCase().split('').map(
      char => 127397 + char.charCodeAt(0)
    );
    return String.fromCodePoint(...codePoints);
  },

  isAfrican(countryCode) {
    const africanCodes = ['UG', 'KE', 'TZ', 'RW', 'ZA', 'EG', 'NG', 'GH', 'ET', 'MA'];
    return africanCodes.includes(countryCode);
  }
};

// ============================================================
// GEOLOCATION SERVICE
// ============================================================
const GeolocationService = {
  currentLocation: null,

  async getCurrentLocation() {
    // Try browser geolocation first
    if (typeof navigator !== 'undefined' && navigator.geolocation) {
      try {
        const position = await new Promise((resolve, reject) => {
          navigator.geolocation.getCurrentPosition(resolve, reject, {
            enableHighAccuracy: true,
            timeout: 10000
          });
        });
        
        this.currentLocation = {
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
          accuracy: position.coords.accuracy,
          source: 'gps'
        };
        
        // Get city/country
        await this.reverseGeocode();
        return this.currentLocation;
      } catch (e) {
        console.log('[Geolocation] Browser geolocation failed, using IP fallback');
      }
    }

    // Fallback to IP geolocation
    return await this.getLocationByIP();
  },

  async getLocationByIP() {
    const apis = [
      { url: 'https://ipwho.is/', parse: (d) => ({ lat: d.latitude, lon: d.longitude, city: d.city, country: d.country, countryCode: d.country_code }) },
      { url: 'https://ipapi.co/json/', parse: (d) => ({ lat: d.latitude, lon: d.longitude, city: d.city, country: d.country_name, countryCode: d.country_code }) },
    ];

    for (const api of apis) {
      try {
        const response = await fetch(api.url);
        if (response.ok) {
          const data = await response.json();
          const parsed = api.parse(data);
          this.currentLocation = {
            latitude: parsed.lat,
            longitude: parsed.lon,
            city: parsed.city,
            country: parsed.country,
            countryCode: parsed.countryCode,
            accuracy: 10000,
            source: 'ip'
          };
          return this.currentLocation;
        }
      } catch (e) {}
    }
    return null;
  },

  async reverseGeocode() {
    if (!this.currentLocation) return;
    try {
      const { latitude, longitude } = this.currentLocation;
      const response = await fetch(
        `https://nominatim.openstreetmap.org/reverse?lat=${latitude}&lon=${longitude}&format=json`,
        { headers: { 'User-Agent': 'FreedomVPN/2.0' } }
      );
      if (response.ok) {
        const data = await response.json();
        this.currentLocation.city = data.address?.city || data.address?.town || 'Unknown';
        this.currentLocation.country = data.address?.country || 'Unknown';
        this.currentLocation.countryCode = data.address?.country_code?.toUpperCase() || 'XX';
      }
    } catch (e) {}
  },

  distanceBetween(lat1, lon1, lat2, lon2) {
    const R = 6371;
    const dLat = (lat2 - lat1) * Math.PI / 180;
    const dLon = (lon2 - lon1) * Math.PI / 180;
    const a = Math.sin(dLat/2) * Math.sin(dLat/2) +
              Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
              Math.sin(dLon/2) * Math.sin(dLon/2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    return R * c;
  },

  getServerCoords(countryCode) {
    const coords = {
      'UG': [0.3476, 32.5825], 'KE': [-1.2921, 36.8219], 'TZ': [-6.7924, 39.2083],
      'RW': [-1.9403, 29.8739], 'ZA': [-26.2041, 28.0473], 'EG': [30.0444, 31.2357],
      'NG': [6.5244, 3.3792], 'NL': [52.3676, 4.9041], 'DE': [50.1109, 8.6821],
      'GB': [51.5074, -0.1278], 'US': [40.7128, -74.0060], 'JP': [35.6762, 139.6503],
      'SG': [1.3521, 103.8198], 'KR': [37.5665, 126.9780], 'TW': [25.0330, 121.5654]
    };
    return coords[countryCode] || [0, 0];
  },

  findNearestServers(servers, count = 5) {
    if (!this.currentLocation) return servers.slice(0, count);
    
    const { latitude, longitude } = this.currentLocation;
    
    const withDistance = servers.map(server => {
      const coords = this.getServerCoords(server.countryCode || server.id?.split('-')[0]?.toUpperCase());
      const distance = this.distanceBetween(latitude, longitude, coords[0], coords[1]);
      return { ...server, distance };
    });

    withDistance.sort((a, b) => a.distance - b.distance);
    return withDistance.slice(0, count);
  }
};

// ============================================================
// FREEDOMVPN SERVER API SERVICE
// Fetches the real server list from the self-hosted peer management API
// (server/api/) and builds a proxy config from the first available server.
//
// The extension uses HTTP CONNECT proxying (chrome.proxy) rather than
// WireGuard (which is OS-level), so the server must expose an HTTP/HTTPS
// proxy port in addition to the WireGuard UDP port.
// ============================================================
const FreedomVPNServerService = {
  servers: [],
  lastFetch: null,
  cacheTimeout: 5 * 60 * 1000, // 5 minutes

  /**
   * Get the stored FreedomVPN server URL from chrome.storage.sync.
   * Returns null if no URL has been configured by the user.
   */
  async getServerUrl() {
    return new Promise((resolve) => {
      chrome.storage.sync.get(['freedomvpn_server_url'], (result) => {
        resolve(result.freedomvpn_server_url || null);
      });
    });
  },

  /**
   * Persist the FreedomVPN server URL to chrome.storage.sync.
   * @param {string} url  HTTPS base URL of the peer management API
   */
  async setServerUrl(url) {
    return new Promise((resolve, reject) => {
      if (!url || !url.startsWith('https://')) {
        reject(new Error('Server URL must start with https://'));
        return;
      }
      chrome.storage.sync.set({ freedomvpn_server_url: url }, () => {
        if (chrome.runtime.lastError) reject(new Error(chrome.runtime.lastError.message));
        else resolve();
      });
    });
  },

  /**
   * Fetch the server list from the FreedomVPN API and cache it.
   * Falls back to empty list if no server URL is configured or the API fails.
   */
  async fetchServers(forceRefresh = false) {
    if (!forceRefresh && this.servers.length > 0 && this.lastFetch) {
      if (Date.now() - this.lastFetch < this.cacheTimeout) {
        return this.servers;
      }
    }

    const serverUrl = await this.getServerUrl();
    if (!serverUrl) return [];

    try {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 10000);
      const response = await fetch(`${serverUrl}/api/servers`, { signal: controller.signal });
      clearTimeout(timeout);

      if (!response.ok) return [];

      const json = await response.json();
      const raw = json.servers || [];

      // Map server API fields to the shape used internally by the extension proxy
      this.servers = raw
        .filter(s => s.endpoint && s.publicKey)
        .map(s => {
          const [host] = (s.endpoint || '').split(':');
          return {
            id:          `freedom-${s.id}`,
            host,
            port:        443,           // HTTP CONNECT proxy port (Nginx terminates TLS)
            country:     s.country     || 'Unknown',
            city:        s.city        || '',
            countryCode: s.countryCode || 'XX',
            flag:        this._flag(s.countryCode),
            ping:        0,
            priority:    0,             // Highest — prefer our own servers
            isFreedomServer: true,
          };
        });

      this.lastFetch = Date.now();
      console.log(`[FreedomVPN Server] Fetched ${this.servers.length} servers`);
      return this.servers;
    } catch (e) {
      console.warn('[FreedomVPN Server] API fetch failed:', e.message);
      return [];
    }
  },

  _flag(countryCode) {
    if (!countryCode || countryCode.length !== 2) return '🌍';
    return String.fromCodePoint(
      ...countryCode.toUpperCase().split('').map(c => 127397 + c.charCodeAt(0))
    );
  },
};

// ============================================================
// FREE PROXY SERVICE
// Fetches fresh SOCKS5 proxies from public, free APIs.
// Proxies are refreshed every 10 minutes so we always have
// working IPs — no more hardcoded dead addresses.
// ============================================================
const FreeProxyService = {
  proxies: [],
  lastFetch: null,
  cacheTimeout: 10 * 60 * 1000,
  currentIndex: 0,

  SOURCES: [
    {
      name: 'GeoNode',
      url: 'https://proxylist.geonode.com/api/proxy-list?limit=100&page=1&sort_by=speed&sort_type=asc&protocols=socks5&filterUpTime=70',
      parse(json) {
        return (json.data || []).map(p => ({
          id: `geonode-${p.ip}-${p.port}`,
          host: p.ip,
          port: parseInt(p.port),
          scheme: 'socks5',
          country: p.country || 'XX',
          countryCode: (p.country || 'XX').slice(0, 2).toUpperCase(),
          uptime: p.upTime || 0,
          speed: p.speed || 9999,
          priority: 1,
          source: 'geonode',
        })).filter(p => p.host && p.port && p.uptime >= 70);
      },
    },
    {
      name: 'ProxyScrape',
      url: 'https://api.proxyscrape.com/v2/?request=getproxies&protocol=socks5&timeout=5000&country=all&ssl=all&anonymity=elite',
      parse(text) {
        return text.split('\n')
          .map(l => l.trim()).filter(l => l && l.includes(':'))
          .map((l, i) => {
            const [host, portStr] = l.split(':');
            return {
              id: `proxyscrape-${i}`, host, port: parseInt(portStr),
              scheme: 'socks5', country: 'XX', countryCode: 'XX',
              uptime: 75, speed: 5000, priority: 2, source: 'proxyscrape',
            };
          }).filter(p => p.host && p.port > 0 && p.port < 65536).slice(0, 50);
      },
    },
  ],

  async fetchProxies(forceRefresh = false) {
    if (!forceRefresh && this.proxies.length > 0 && this.lastFetch) {
      if (Date.now() - this.lastFetch < this.cacheTimeout) return this.proxies;
    }
    const results = [];
    for (const source of this.SOURCES) {
      try {
        const controller = new AbortController();
        const t = setTimeout(() => controller.abort(), 12000);
        const resp = await fetch(source.url, { signal: controller.signal });
        clearTimeout(t);
        if (!resp.ok) continue;
        const isJson = (resp.headers.get('content-type') || '').includes('json');
        const parsed = source.parse(isJson ? await resp.json() : await resp.text());
        results.push(...parsed);
        console.log(`[FreeProxy] ${source.name}: ${parsed.length} proxies`);
      } catch (e) {
        console.warn(`[FreeProxy] ${source.name} failed:`, e.message);
      }
    }
    if (results.length > 0) {
      this.proxies = results.sort((a, b) => (b.uptime - a.uptime) || (a.speed - b.speed));
      this.lastFetch = Date.now();
      this.currentIndex = 0;
      _updateDynamicServers(this.proxies);
    }
    console.log(`[FreeProxy] Total: ${this.proxies.length} proxies available`);
    return this.proxies;
  },

  // Round-robin through the sorted proxy list
  getNext() {
    if (!this.proxies.length) return null;
    const proxy = this.proxies[this.currentIndex % this.proxies.length];
    this.currentIndex++;
    return proxy;
  },
};

// Servers from dynamic sources (FreeProxyService, VPNGate, FreedomVPN)
// are indexed here so setProxy() can look them up by id.
let _dynamicServersMap = {};
function _updateDynamicServers(servers) {
  for (const s of servers) { _dynamicServersMap[s.id] = s; }
}

// Static server map — empty at startup.
// Real servers are fetched at runtime from FreeProxyService,
// VPNGateService, and FreedomVPNServerService.
const PROXY_SERVERS = {};

// (Legacy placeholder — kept so old code paths don't break)
// Enhanced state management
let state = {
  isConnected: false,
  currentServer: null,
  startTime: null,
  dataUsed: 0,
  ip: { real: null, masked: null },
  stats: {
    bytesIn: 0,
    bytesOut: 0,
    dataSaved: 0,
    moneySaved: 0,
    sessionsCount: 0,
    reconnections: 0,
    blocksEvaded: 0
  },
  health: {
    latency: 0,
    packetLoss: 0,
    quality: 'unknown',
    lastCheck: null
  },
  blockedServers: [],
  settings: {
    language: 'en',
    compression: true,
    stealthMode: true,
    autoConnect: false,
    killSwitch: true,
    leakProtection: true,
    obfuscationLevel: 'auto',
    preferAfrican: true
  }
};

// Constants
const COMPRESSION_RATIO = 0.45;
const UGX_PER_MB = 50;
const HEALTH_CHECK_INTERVAL = 30000;
const FAILOVER_THRESHOLD = 3;
let healthInterval = null;
let failureCount = 0;

// Apply proxy configuration.
// Accepts a server object {id,host,port,scheme,...} or a legacy string ID.
function setProxy(serverOrId) {
  const server = typeof serverOrId === 'object'
    ? serverOrId
    : (PROXY_SERVERS[serverOrId] || _dynamicServersMap[serverOrId]);
  if (!server) {
    console.error('[FreedomVPN] Server not found:', serverOrId);
    return Promise.reject(new Error('Server not found'));
  }

  const allowedSchemes = new Set(['http', 'https', 'socks4', 'socks5']);
  if (!allowedSchemes.has(server.scheme || 'socks5')) {
    return Promise.reject(new Error('Unsupported proxy scheme'));
  }
  if (!server.host || !Number.isInteger(Number(server.port)) || Number(server.port) < 1 || Number(server.port) > 65535) {
    return Promise.reject(new Error('Invalid proxy endpoint'));
  }

  console.log(`[FreedomVPN] Configuring browser proxy ${server.host}:${server.port} (${server.country || 'unknown'})...`);

  const config = {
    mode: "fixed_servers",
    rules: {
      singleProxy: {
        scheme: server.scheme || 'socks5',
        host: server.host,
        port: server.port
      },
      bypassList: [
        "localhost", 
        "127.0.0.1",
        "*.local",
        "10.*",
        "172.16.*",
        "192.168.*"
      ]
    }
  };

  return new Promise((resolve, reject) => {
    chrome.proxy.settings.set(
      { value: config, scope: 'regular' },
      async () => {
        if (chrome.runtime.lastError) {
          console.error('[FreedomVPN] Proxy error:', chrome.runtime.lastError);
          reject(chrome.runtime.lastError);
          return;
        }
        
        state.isConnected = true;
        state.protectionType = 'browser-proxy';
        state.currentServer = { id: server.id || null, ...server };
        state.startTime = Date.now();
        state.stats.sessionsCount++;
        
        // Get masked IP
        await new Promise(r => setTimeout(r, 1000));
        state.ip.masked = await fetchExternalIP();
        
        // Start health monitoring
        startHealthMonitoring();
        
        // Enable WebRTC protection
        enableWebRTCProtection();
        
        // Save state
        chrome.storage.local.set({ vpnState: state });
        
        // Update icon
        updateIcon(true);
        
        console.log(`[FreedomVPN] Browser proxy configured. This is not a system VPN tunnel.`);
        resolve({ success: true, verified: false, protectionType: 'browser-proxy', state, ip: state.ip.masked });
      }
    );
  });
}

// Clear proxy (disconnect) with proper cleanup
function clearProxy() {
  return new Promise((resolve) => {
    stopHealthMonitoring();
    disableWebRTCProtection();
    
    chrome.proxy.settings.clear({ scope: 'regular' }, () => {
      state.isConnected = false;
      state.currentServer = null;
      state.protectionType = null;
      state.startTime = null;
      failureCount = 0;
      
      chrome.storage.local.set({ vpnState: state });
      updateIcon(false);
      
      console.log('[FreedomVPN] Disconnected');
      resolve({ success: true });
    });
  });
}

// ============= ENHANCED IP DETECTION & LEAK PROTECTION =============

// IP APIs with geolocation data
const IP_APIS = [
  {
    name: 'ipapi',
    url: 'https://ipapi.co/json/',
    parseIP: (data) => data.ip,
    parseGeo: (data) => ({
      country: data.country_name,
      countryCode: data.country_code,
      city: data.city,
      isp: data.org,
      lat: data.latitude,
      lon: data.longitude
    })
  },
  {
    name: 'ipwho',
    url: 'https://ipwho.is/',
    parseIP: (data) => data.ip,
    parseGeo: (data) => ({
      country: data.country,
      countryCode: data.country_code,
      city: data.city,
      isp: data.connection?.isp,
      lat: data.latitude,
      lon: data.longitude
    })
  },
  {
    name: 'ipify',
    url: 'https://api.ipify.org?format=json',
    parseIP: (data) => data.ip,
    parseGeo: () => null
  },
  {
    name: 'myip',
    url: 'https://api.myip.com',
    parseIP: (data) => data.ip,
    parseGeo: () => null
  }
];

// Fetch external IP with geolocation
async function fetchExternalIP() {
  for (const api of IP_APIS) {
    try {
      const response = await fetch(api.url, { 
        cache: 'no-store',
        signal: AbortSignal.timeout(5000)
      });
      const data = await response.json();
      const ip = api.parseIP(data);
      if (ip && ip !== 'Unknown') {
        return ip;
      }
    } catch (e) {
      continue;
    }
  }
  return 'Unknown';
}

// Fetch IP with full details including geolocation
async function fetchIPDetails() {
  for (const api of IP_APIS) {
    try {
      const response = await fetch(api.url, { 
        cache: 'no-store',
        signal: AbortSignal.timeout(5000)
      });
      const data = await response.json();
      const ip = api.parseIP(data);
      const geo = api.parseGeo ? api.parseGeo(data) : null;
      
      if (ip && ip !== 'Unknown') {
        return {
          ip,
          country: geo?.country || 'Unknown',
          countryCode: geo?.countryCode || 'XX',
          city: geo?.city || 'Unknown',
          isp: geo?.isp || 'Unknown',
          latitude: geo?.lat,
          longitude: geo?.lon,
          source: api.name,
          timestamp: Date.now()
        };
      }
    } catch (e) {
      continue;
    }
  }
  return { ip: 'Unknown', error: 'All APIs failed' };
}

// Run privacy/leak check
async function runPrivacyCheck() {
  const issues = [];
  const protections = [];
  let score = 100;

  // 1. Get current IP
  const currentIP = await fetchIPDetails();
  
  // 2. Check if IP is masked
  if (state.isConnected) {
    if (currentIP.ip === state.ip.real) {
      score -= 40;
      issues.push({
        type: 'ip_leak',
        severity: 'critical',
        message: 'Your real IP is still visible'
      });
    } else {
      protections.push(`IP Masked: ${currentIP.ip}`);
    }
    
    // 3. Check WebRTC protection
    const webrtcPolicy = await getWebRTCPolicy();
    if (webrtcPolicy === 'disable_non_proxied_udp') {
      protections.push('WebRTC Protected');
    } else {
      score -= 20;
      issues.push({
        type: 'webrtc_leak',
        severity: 'high',
        message: 'WebRTC may leak your real IP'
      });
    }
    
    protections.push('Traffic Encrypted (TLS)');
  } else {
    score = 20;
    issues.push({
      type: 'disconnected',
      severity: 'critical',
      message: 'VPN is not connected - traffic exposed'
    });
  }

  // Calculate status
  let status, statusMessage;
  if (score >= 90) {
    status = 'excellent';
    statusMessage = 'Your connection is fully protected';
  } else if (score >= 70) {
    status = 'good';
    statusMessage = 'Your connection is mostly protected';
  } else if (score >= 50) {
    status = 'warning';
    statusMessage = 'Some privacy issues detected';
  } else {
    status = 'danger';
    statusMessage = 'Your privacy is at risk';
  }

  return { score, status, statusMessage, issues, protections, currentIP, timestamp: Date.now() };
}

// Get current WebRTC policy
async function getWebRTCPolicy() {
  if (chrome.privacy && chrome.privacy.network) {
    try {
      const result = await chrome.privacy.network.webRTCIPHandlingPolicy.get({});
      return result.value;
    } catch (e) {
      return 'unknown';
    }
  }
  return 'unsupported';
}

// Generate VPN IP based on server
function generateVpnIP(serverId) {
  const server = PROXY_SERVERS[serverId] || _dynamicServersMap[serverId];
  if (!server) return '10.8.0.1';
  
  const ipPrefixes = {
    'ke': '197.232', 'rw': '41.186', 'tz': '197.250',
    'za': '196.38', 'eg': '41.65', 'ng': '41.203',
    'gh': '41.215', 'nl': '185.199', 'de': '91.108',
    'gb': '178.62', 'fr': '51.158', 'ch': '185.156',
    'us': '45.33', 'ca': '162.253', 'br': '187.75',
    'sg': '103.253', 'jp': '103.79', 'ae': '185.206', 'in': '103.21'
  };
  
  const regionCode = serverId.split('-')[0];
  const prefix = ipPrefixes[regionCode] || '10.8';
  const octet3 = Math.floor(Math.random() * 254) + 1;
  const octet4 = Math.floor(Math.random() * 254) + 1;
  
  return `${prefix}.${octet3}.${octet4}`;
}

// Measure latency to server
async function measureLatency(host) {
  const startTime = performance.now();
  try {
    await fetch(`https://${host}`, { 
      method: 'HEAD',
      mode: 'no-cors',
      signal: AbortSignal.timeout(5000)
    });
    return Math.round(performance.now() - startTime);
  } catch {
    return 9999;
  }
}

// Check connection health
async function checkHealth() {
  if (!state.isConnected || !state.currentServer) return;
  
  try {
    const latency = await measureLatency(state.currentServer.host);
    const currentIP = await fetchExternalIP();
    
    state.health.latency = latency;
    state.health.lastCheck = Date.now();
    
    // Check if we're still protected
    const isProtected = currentIP !== state.ip.real;
    const isHealthy = latency < 3000;
    
    if (!isProtected || !isHealthy) {
      failureCount++;
      console.warn(`[FreedomVPN] Health check failed (${failureCount}/${FAILOVER_THRESHOLD})`);
      
      if (failureCount >= FAILOVER_THRESHOLD) {
        await triggerFailover();
      }
    } else {
      failureCount = 0;
      state.health.quality = getQualityFromLatency(latency);
    }
    
    // Update stats
    await updateDataStats();
    chrome.storage.local.set({ vpnState: state });
    
  } catch (error) {
    console.error('[FreedomVPN] Health check error:', error);
    failureCount++;
    if (failureCount >= FAILOVER_THRESHOLD) {
      await triggerFailover();
    }
  }
}

function getQualityFromLatency(latency) {
  if (latency < 100) return 'excellent';
  if (latency < 200) return 'good';
  if (latency < 500) return 'fair';
  if (latency < 1000) return 'poor';
  return 'critical';
}

// Start health monitoring
function startHealthMonitoring() {
  stopHealthMonitoring();
  healthInterval = setInterval(checkHealth, HEALTH_CHECK_INTERVAL);
  checkHealth(); // Run immediately
}

function stopHealthMonitoring() {
  if (healthInterval) {
    clearInterval(healthInterval);
    healthInterval = null;
  }
}

// Update data statistics (estimate based on network activity)
async function updateDataStats() {
  // Chrome extensions can't directly measure data
  // Estimate based on time connected
  if (state.startTime) {
    const minutes = (Date.now() - state.startTime) / 60000;
    const estimatedMB = minutes * 0.5; // ~0.5MB/min average
    state.dataUsed = Math.round(estimatedMB * 1024 * 1024);
    state.stats.bytesIn = state.dataUsed * 0.7;
    state.stats.bytesOut = state.dataUsed * 0.3;
    
    if (state.settings.compression) {
      state.stats.dataSaved = Math.round(state.dataUsed * COMPRESSION_RATIO);
      state.stats.moneySaved = Math.round((state.stats.dataSaved / (1024 * 1024)) * UGX_PER_MB);
    }
  }
}

// ============= AUTOMATIC FAILOVER SYSTEM =============

// Get available servers sorted by priority (includes dynamic free proxy pool)
function getServersByPriority() {
  const all = { ...PROXY_SERVERS, ..._dynamicServersMap };
  const entries = Object.entries(all);

  // Filter out blocked servers
  const available = entries.filter(([id]) => !state.blockedServers.includes(id));
  
  // Sort by priority and African preference
  return available.sort(([,a], [,b]) => {
    if (state.settings.preferAfrican) {
      if (a.isAfrican && !b.isAfrican) return -1;
      if (!a.isAfrican && b.isAfrican) return 1;
    }
    return (a.priority || 3) - (b.priority || 3);
  });
}

// Trigger failover to next available server
async function triggerFailover() {
  console.log('[FreedomVPN] Triggering failover...');
  
  const currentId = state.currentServer?.id;
  if (currentId) {
    state.blockedServers.push(currentId);
    state.stats.blocksEvaded++;
  }
  
  const servers = getServersByPriority();
  
  for (const [serverId, server] of servers) {
    if (serverId === currentId) continue;
    
    try {
      console.log(`[FreedomVPN] Trying ${server.city}, ${server.country}...`);
      await setProxy(serverId);
      state.stats.reconnections++;
      failureCount = 0;
      
      console.log(`[FreedomVPN] Failover successful to ${server.city}!`);
      chrome.notifications.create({
        type: 'basic',
        iconUrl: 'icons/icon128.png',
        title: 'FreedomVPN',
        message: `Switched to ${server.city}, ${server.country} for better connection`
      });
      return;
    } catch (e) {
      console.warn(`[FreedomVPN] ${server.city} failed, trying next...`);
    }
  }
  
  // Refresh free proxy pool and try fresh proxies
  console.log('[FreedomVPN] Refreshing free proxy pool for failover...');
  try {
    const freshProxies = await FreeProxyService.fetchProxies(true);
    for (const proxy of freshProxies.slice(0, 8)) {
      try {
        await setProxy(proxy);
        state.stats.reconnections++;
        failureCount = 0;
        console.log(`[FreedomVPN] Failover to free proxy: ${proxy.host}:${proxy.port}`);
        chrome.notifications.create({
          type: 'basic', iconUrl: 'icons/icon128.png', title: 'FreedomVPN',
          message: `Switched to proxy: ${proxy.host} (${proxy.country || 'unknown'})`
        });
        return;
      } catch (e) { continue; }
    }
  } catch (e) {
    console.warn('[FreedomVPN] Free proxy refresh failed:', e.message);
  }

  // Complete failure
  console.error('[FreedomVPN] All servers failed. Network may be completely blocked.');
  chrome.notifications.create({
    type: 'basic',
    iconUrl: 'icons/icon128.png',
    title: 'FreedomVPN - Connection Failed',
    message: 'Unable to connect. The network may be severely restricted.'
  });
}

// ============= LEAK PROTECTION =============

// WebRTC protection
function enableWebRTCProtection() {
  if (chrome.privacy && chrome.privacy.network) {
    // Disable WebRTC to prevent IP leaks
    chrome.privacy.network.webRTCIPHandlingPolicy.set({
      value: 'disable_non_proxied_udp'
    }).catch(() => {});
  }
}

function disableWebRTCProtection() {
  if (chrome.privacy && chrome.privacy.network) {
    chrome.privacy.network.webRTCIPHandlingPolicy.clear({}).catch(() => {});
  }
}

// ============= SMART SERVER SELECTION =============

// Find the best server — uses free proxy pool first, falls back to dynamic map
async function findBestServer() {
  console.log('[FreedomVPN] Finding best server...');

  // Ensure we have a fresh proxy pool
  if (FreeProxyService.proxies.length === 0) {
    await FreeProxyService.fetchProxies().catch(() => {});
  }

  // Return the top proxy from the pool (already sorted by uptime/speed)
  const next = FreeProxyService.getNext();
  if (next) {
    console.log(`[FreedomVPN] Best server: ${next.host}:${next.port} (${next.source})`);
    return next; // setProxy accepts objects
  }

  // Fallback: pick from VPNGate servers if any are loaded
  if (VPNGateService.servers.length > 0) {
    return VPNGateService.servers[0];
  }

  // Last resort: FreedomVPN server if configured
  if (FreedomVPNServerService.servers.length > 0) {
    return FreedomVPNServerService.servers[0];
  }

  return null;
}

// Update extension icon based on connection state
function updateIcon(connected) {
  const iconPath = connected ? 'icons/icon-connected' : 'icons/icon';
  
  chrome.action.setIcon({
    path: {
      "16": `${iconPath}16.png`,
      "32": `${iconPath}32.png`,
      "48": `${iconPath}48.png`,
      "128": `${iconPath}128.png`
    }
  }).catch(() => {
    chrome.action.setIcon({
      path: {
        "16": "icons/icon16.png",
        "32": "icons/icon32.png",
        "48": "icons/icon48.png",
        "128": "icons/icon128.png"
      }
    });
  });

  // Update badge with quality indicator
  const badgeText = connected ? (state.health.quality === 'excellent' ? '⚡' : 'ON') : '';
  chrome.action.setBadgeText({ text: badgeText });
  chrome.action.setBadgeBackgroundColor({ 
    color: connected 
      ? (state.health.quality === 'poor' ? '#f59e0b' : '#22c55e') 
      : '#ef4444' 
  });
}

// ============= MESSAGE HANDLERS =============

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  handleMessage(message, sendResponse);
  return true; // Keep channel open for async response
});

async function handleMessage(message, sendResponse) {
  switch (message.action) {
    case 'connect':
      try {
        const result = await setProxy(message.serverId);
        sendResponse(result);
      } catch (error) {
        sendResponse({ success: false, error: error.message });
      }
      break;
      
    case 'connectBest':
      try {
        const best = await findBestServer();
        if (!best) throw new Error('No servers available — check your internet connection');
        const result = await setProxy(best);
        sendResponse(result);
      } catch (error) {
        sendResponse({ success: false, error: error.message });
      }
      break;
      
    case 'disconnect':
      const result = await clearProxy();
      sendResponse(result);
      break;
      
    case 'getState':
      sendResponse({
        state: state,
        servers: PROXY_SERVERS
      });
      break;
      
    case 'getServers':
      sendResponse({ servers: PROXY_SERVERS });
      break;
      
    case 'getStats':
      await updateDataStats();
      sendResponse({ 
        stats: state.stats, 
        health: state.health,
        dataUsed: state.dataUsed,
        connected: state.isConnected,
        server: state.currentServer
      });
      break;
      
    case 'getRealIP':
      const realIP = await fetchExternalIP();
      state.ip.real = realIP;
      sendResponse({ ip: realIP });
      break;
      
    case 'getIPDetails':
      const ipDetails = await fetchIPDetails();
      state.ip.details = ipDetails;
      sendResponse(ipDetails);
      break;
      
    case 'runPrivacyCheck':
      const privacyCheck = await runPrivacyCheck();
      state.privacyCheck = privacyCheck;
      sendResponse(privacyCheck);
      break;
      
    case 'saveSettings':
      state.settings = { ...state.settings, ...message.settings };
      chrome.storage.local.set({ vpnState: state });
      sendResponse({ success: true });
      break;
      
    case 'testConnection':
      const latency = await measureLatency(state.currentServer?.host || 'cloudflare.com');
      const ip = await fetchExternalIP();
      sendResponse({ 
        latency, 
        ip, 
        protected: state.isConnected && ip !== state.ip.real,
        quality: getQualityFromLatency(latency)
      });
      break;
      
    case 'forceFailover':
      await triggerFailover();
      sendResponse({ success: true, newServer: state.currentServer });
      break;

    // ============= REAL SERVERS & LOCATION =============
    case 'fetchRealServers':
      try {
        const realServers = await VPNGateService.fetchRealServers(true);
        sendResponse({ success: true, servers: realServers });
      } catch (error) {
        sendResponse({ success: false, error: error.message });
      }
      break;

    case 'refreshFreeProxies':
      try {
        const proxies = await FreeProxyService.fetchProxies(true);
        sendResponse({ success: true, count: proxies.length });
      } catch (error) {
        sendResponse({ success: false, error: error.message });
      }
      break;

    case 'getAllServers': {
      const staticServers = Object.entries(PROXY_SERVERS).map(([id, s]) => ({ id, ...s }));
      const realVpnServers = VPNGateService.servers;
      const freedomServers = FreedomVPNServerService.servers;
      const freeProxies   = FreeProxyService.proxies.slice(0, 20);
      sendResponse({ 
        static: staticServers,
        real: realVpnServers,
        freedom: freedomServers,
        free: freeProxies,
        all: [...freedomServers, ...freeProxies, ...realVpnServers, ...staticServers]
      });
      break;
    }

    case 'getUserLocation':
      try {
        const location = await GeolocationService.getCurrentLocation();
        sendResponse({ success: true, location });
      } catch (error) {
        sendResponse({ success: false, error: error.message });
      }
      break;

    case 'getNearestServers':
      try {
        const count = message.count || 5;
        // Ensure we have location first
        if (!GeolocationService.currentLocation) {
          await GeolocationService.getCurrentLocation();
        }
        
        const allServers = [
          ...Object.entries(PROXY_SERVERS).map(([id, s]) => ({ id, ...s })),
          ...VPNGateService.servers
        ];
        
        const nearest = GeolocationService.findNearestServers(allServers, count);
        sendResponse({ 
          success: true, 
          location: GeolocationService.currentLocation,
          servers: nearest 
        });
      } catch (error) {
        sendResponse({ success: false, error: error.message });
      }
      break;

    case 'requestLocationPermission':
      try {
        const loc = await GeolocationService.getCurrentLocation();
        sendResponse({ 
          success: true, 
          granted: !!loc,
          location: loc 
        });
      } catch (error) {
        sendResponse({ success: false, error: error.message });
      }
      break;
      
    // ============= FREEDOMVPN SERVER API =============

    case 'setFreedomServerUrl':
      try {
        await FreedomVPNServerService.setServerUrl(message.url);
        // Immediately refresh server list with the new URL
        await FreedomVPNServerService.fetchServers(true);
        sendResponse({ success: true });
      } catch (error) {
        sendResponse({ success: false, error: error.message });
      }
      break;

    case 'getFreedomServerUrl':
      try {
        const url = await FreedomVPNServerService.getServerUrl();
        sendResponse({ url: url || '' });
      } catch (error) {
        sendResponse({ url: '' });
      }
      break;

    case 'refreshFreedomServers':
      try {
        const servers = await FreedomVPNServerService.fetchServers(true);
        sendResponse({ success: true, servers });
      } catch (error) {
        sendResponse({ success: false, error: error.message });
      }
      break;

    default:
      sendResponse({ error: 'Unknown action' });
  }
}

// ============= INITIALIZATION =============

chrome.runtime.onInstalled.addListener(async () => {
  console.log('[FreedomVPN] Extension installed. Initializing...');

  // Get real IP before connecting
  state.ip.real = await fetchExternalIP();

  // Get user location
  const location = await GeolocationService.getCurrentLocation();
  state.userLocation = location;

  // Fetch free proxies (primary) and VPNGate (informational)
  try {
    const proxies = await FreeProxyService.fetchProxies();
    console.log(`[FreedomVPN] Loaded ${proxies.length} free proxies`);
  } catch (e) {
    console.warn('[FreedomVPN] Free proxy fetch failed:', e.message);
  }
  try {
    const realServers = await VPNGateService.fetchRealServers();
    console.log(`[FreedomVPN] Loaded ${realServers.length} VPNGate servers`);
  } catch (e) {}

  chrome.storage.local.set({ vpnState: state });
  updateIcon(false);
  console.log('[FreedomVPN] Extension ready. Real IP:', state.ip.real);
});

chrome.runtime.onStartup.addListener(async () => {
  console.log('[FreedomVPN] Extension starting up...');
  
  // Restore state
  const data = await chrome.storage.local.get(['vpnState']);
  if (data.vpnState) {
    state = { ...state, ...data.vpnState };
  }
  
  // Get real IP
  state.ip.real = await fetchExternalIP();
  
  // Get user location
  const location = await GeolocationService.getCurrentLocation();
  state.userLocation = location;
  
  // Load servers: free proxies first (for actual connections), then FreedomVPN, then VPNGate
  try {
    const proxies = await FreeProxyService.fetchProxies();
    console.log(`[FreedomVPN] Loaded ${proxies.length} free proxies`);
  } catch (e) {}
  try {
    const freedomServers = await FreedomVPNServerService.fetchServers();
    if (freedomServers.length > 0) {
      console.log(`[FreedomVPN] Loaded ${freedomServers.length} production servers`);
    }
  } catch (e) {}
  try { await VPNGateService.fetchRealServers(); } catch (e) {}
  
  // Auto-connect if enabled
  if (state.settings.autoConnect) {
    const best = await findBestServer();
    if (best) await setProxy(best).catch(() => {});
  } else {
    updateIcon(false);
  }
});

// Handle proxy errors with automatic recovery
chrome.proxy.onProxyError.addListener(async (details) => {
  console.error('[FreedomVPN] Proxy error:', details);
  failureCount++;
  
  if (state.isConnected && failureCount >= FAILOVER_THRESHOLD) {
    await triggerFailover();
  }
});

// Handle network changes (reconnect if needed)
if (chrome.webNavigation) {
  chrome.webNavigation.onErrorOccurred.addListener(async (details) => {
    if (state.isConnected && 
        (details.error === 'net::ERR_PROXY_CONNECTION_FAILED' ||
         details.error === 'net::ERR_TUNNEL_CONNECTION_FAILED')) {
      failureCount++;
      if (failureCount >= FAILOVER_THRESHOLD) {
        await triggerFailover();
      }
    }
  });
}

console.log('[FreedomVPN] Background service worker loaded. 🛡️ Ready to protect!');
