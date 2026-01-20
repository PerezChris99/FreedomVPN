/**
 * VPN Gate Integration for Browser Extension
 * Fetches real, working free proxy servers from VPN Gate
 */

const VPNGATE_API = 'https://www.vpngate.net/api/iphone/';
const CORS_PROXIES = [
  'https://corsproxy.io/?',
  'https://api.allorigins.win/raw?url=',
  'https://cors-anywhere.herokuapp.com/'
];

// Parse VPN Gate CSV data
function parseVpnGateCSV(csvData) {
  const lines = csvData.split('\n');
  const servers = [];
  
  // Skip header lines (first 2 lines are headers)
  for (let i = 2; i < lines.length; i++) {
    const line = lines[i].trim();
    if (!line || line.startsWith('*')) continue;
    
    const parts = line.split(',');
    if (parts.length < 15) continue;
    
    const server = {
      hostname: parts[0],
      ip: parts[1],
      score: parseInt(parts[2]) || 0,
      ping: parseInt(parts[3]) || 999,
      speed: parseInt(parts[4]) || 0, // bps
      country: parts[5],
      countryCode: parts[6],
      sessions: parseInt(parts[7]) || 0,
      uptime: parseInt(parts[8]) || 0,
      totalUsers: parseInt(parts[9]) || 0,
      totalTraffic: parts[10],
      logType: parts[11],
      operator: parts[12],
      message: parts[13],
      openVpnConfig: parts[14] // Base64 encoded
    };
    
    // Calculate a priority score (higher is better)
    // We want fast servers with low ping
    const speedMbps = server.speed / 1000000;
    const pingFactor = Math.max(1, 500 - server.ping) / 500;
    server.priority = (speedMbps * pingFactor * server.score) / 1000;
    
    servers.push(server);
  }
  
  // Sort by priority (best first)
  return servers.sort((a, b) => b.priority - a.priority);
}

// Fetch VPN Gate servers
async function fetchVpnGateServers() {
  // Try direct fetch first (may work in extension context)
  try {
    const response = await fetch(VPNGATE_API, {
      cache: 'no-store',
      signal: AbortSignal.timeout(10000)
    });
    if (response.ok) {
      const text = await response.text();
      return parseVpnGateCSV(text);
    }
  } catch (e) {
    console.log('[VPNGate] Direct fetch failed, trying CORS proxy...');
  }
  
  // Try CORS proxies
  for (const proxy of CORS_PROXIES) {
    try {
      const url = proxy + encodeURIComponent(VPNGATE_API);
      const response = await fetch(url, {
        cache: 'no-store',
        signal: AbortSignal.timeout(15000)
      });
      if (response.ok) {
        const text = await response.text();
        if (text.includes('vpn_servers')) {
          return parseVpnGateCSV(text);
        }
      }
    } catch (e) {
      console.log(`[VPNGate] CORS proxy ${proxy} failed`);
      continue;
    }
  }
  
  throw new Error('Could not fetch VPN Gate servers');
}

// Get best servers for a specific country
function getServersByCountry(servers, countryCode) {
  return servers.filter(s => 
    s.countryCode.toLowerCase() === countryCode.toLowerCase()
  );
}

// Get best African servers (closest to Uganda)
function getAfricanServers(servers) {
  const africanCodes = ['ZA', 'EG', 'KE', 'TZ', 'NG', 'GH', 'RW', 'UG'];
  return servers.filter(s => 
    africanCodes.includes(s.countryCode.toUpperCase())
  );
}

// Get European servers (good for privacy)
function getEuropeanServers(servers) {
  const euCodes = ['NL', 'DE', 'GB', 'FR', 'CH', 'SE', 'NO', 'DK', 'FI'];
  return servers.filter(s => 
    euCodes.includes(s.countryCode.toUpperCase())
  );
}

// Convert VPN Gate server to proxy config
// NOTE: VPN Gate provides OpenVPN configs, not HTTP proxies
// For browser extension, we need a different approach
function serverToProxyConfig(server) {
  return {
    id: `vpngate-${server.ip.replace(/\./g, '-')}`,
    host: server.ip,
    port: 443, // VPN Gate uses various ports
    country: server.country,
    countryCode: server.countryCode,
    speed: server.speed,
    ping: server.ping,
    score: server.score,
    priority: server.priority,
    operator: server.operator,
    // Flag emoji based on country code
    flag: getCountryFlag(server.countryCode)
  };
}

// Get country flag emoji
function getCountryFlag(countryCode) {
  const code = countryCode.toUpperCase();
  // Convert country code to flag emoji
  const offset = 127397; // Regional indicator offset
  const chars = [...code].map(c => String.fromCodePoint(c.charCodeAt(0) + offset));
  return chars.join('');
}

// Export for use in background.js
if (typeof module !== 'undefined' && module.exports) {
  module.exports = {
    fetchVpnGateServers,
    getServersByCountry,
    getAfricanServers,
    getEuropeanServers,
    serverToProxyConfig,
    parseVpnGateCSV
  };
}

// Make available globally for extension
self.VPNGate = {
  fetchServers: fetchVpnGateServers,
  getByCountry: getServersByCountry,
  getAfrican: getAfricanServers,
  getEuropean: getEuropeanServers,
  toProxyConfig: serverToProxyConfig
};
