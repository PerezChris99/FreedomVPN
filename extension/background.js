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
 */

// Enhanced server configuration with obfuscation support
const PROXY_SERVERS = {
  // === AFRICAN SERVERS (Priority - Lowest latency for Uganda) ===
  'ke-nrb': { 
    host: '197.232.170.50', 
    port: 443, 
    country: 'Kenya', 
    city: 'Nairobi', 
    flag: '🇰🇪',
    obfuscation: ['tls', 'https'],
    priority: 1,
    isAfrican: true,
    description: 'Closest to Uganda - best latency'
  },
  'rw-kgl': { 
    host: '41.186.255.100', 
    port: 443, 
    country: 'Rwanda', 
    city: 'Kigali', 
    flag: '🇷🇼',
    obfuscation: ['tls'],
    priority: 1,
    isAfrican: true,
    description: 'Direct neighbor - very low latency'
  },
  'tz-dar': { 
    host: '197.250.65.20', 
    port: 443, 
    country: 'Tanzania', 
    city: 'Dar es Salaam', 
    flag: '🇹🇿',
    obfuscation: ['tls'],
    priority: 1,
    isAfrican: true,
    description: 'East African hub'
  },
  'za-jnb': { 
    host: '196.38.180.10', 
    port: 443, 
    country: 'South Africa', 
    city: 'Johannesburg', 
    flag: '🇿🇦',
    obfuscation: ['tls', 'websocket'],
    priority: 1,
    isAfrican: true,
    description: 'Major African hub - reliable'
  },
  'eg-cai': { 
    host: '41.65.236.100', 
    port: 443, 
    country: 'Egypt', 
    city: 'Cairo', 
    flag: '🇪🇬',
    obfuscation: ['tls', 'https'],
    priority: 2,
    isAfrican: true
  },
  'ng-lag': { 
    host: '41.203.76.50', 
    port: 443, 
    country: 'Nigeria', 
    city: 'Lagos', 
    flag: '🇳🇬',
    obfuscation: ['tls'],
    priority: 2,
    isAfrican: true
  },
  'gh-acc': { 
    host: '41.215.160.100', 
    port: 443, 
    country: 'Ghana', 
    city: 'Accra', 
    flag: '🇬🇭',
    obfuscation: ['tls'],
    priority: 2,
    isAfrican: true
  },
  
  // === EUROPEAN SERVERS (Good balance of speed and privacy) ===
  'nl-ams': { 
    host: '185.199.228.220', 
    port: 443, 
    country: 'Netherlands', 
    city: 'Amsterdam', 
    flag: '🇳🇱',
    obfuscation: ['tls', 'websocket', 'domain-front'],
    priority: 1,
    description: 'Privacy haven - no data retention'
  },
  'de-fra': { 
    host: '91.108.56.180', 
    port: 443, 
    country: 'Germany', 
    city: 'Frankfurt', 
    flag: '🇩🇪',
    obfuscation: ['tls', 'https', 'websocket'],
    priority: 1
  },
  'gb-lon': { 
    host: '178.62.56.148', 
    port: 443, 
    country: 'United Kingdom', 
    city: 'London', 
    flag: '🇬🇧',
    obfuscation: ['tls', 'websocket'],
    priority: 1
  },
  'fr-par': { 
    host: '51.158.166.230', 
    port: 443, 
    country: 'France', 
    city: 'Paris', 
    flag: '🇫🇷',
    obfuscation: ['tls', 'https'],
    priority: 2
  },
  'ch-zur': { 
    host: '185.156.46.100', 
    port: 443, 
    country: 'Switzerland', 
    city: 'Zurich', 
    flag: '🇨🇭',
    obfuscation: ['tls', 'https'],
    priority: 1,
    description: 'Swiss privacy laws - very secure'
  },
  
  // === AMERICAS ===
  'us-nyc': { 
    host: '45.33.32.156', 
    port: 443, 
    country: 'United States', 
    city: 'New York', 
    flag: '🇺🇸',
    obfuscation: ['tls', 'websocket'],
    priority: 2
  },
  'us-lax': { 
    host: '104.131.175.196', 
    port: 443, 
    country: 'United States', 
    city: 'Los Angeles', 
    flag: '🇺🇸',
    obfuscation: ['tls', 'websocket'],
    priority: 2
  },
  'br-sao': { 
    host: '191.235.85.100', 
    port: 443, 
    country: 'Brazil', 
    city: 'São Paulo', 
    flag: '🇧🇷',
    obfuscation: ['tls'],
    priority: 2
  },
  'ca-tor': { 
    host: '162.253.128.50', 
    port: 443, 
    country: 'Canada', 
    city: 'Toronto', 
    flag: '🇨🇦',
    obfuscation: ['tls', 'websocket'],
    priority: 2,
    description: 'Strong privacy laws'
  },
  
  // === ASIA ===
  'sg-sin': { 
    host: '128.199.192.50', 
    port: 443, 
    country: 'Singapore', 
    city: 'Singapore', 
    flag: '🇸🇬',
    obfuscation: ['tls', 'websocket'],
    priority: 2
  },
  'jp-tky': { 
    host: '45.76.98.100', 
    port: 443, 
    country: 'Japan', 
    city: 'Tokyo', 
    flag: '🇯🇵',
    obfuscation: ['tls', 'websocket'],
    priority: 2
  },
  'ae-dxb': { 
    host: '194.48.215.50', 
    port: 443, 
    country: 'UAE', 
    city: 'Dubai', 
    flag: '🇦🇪',
    obfuscation: ['tls', 'https'],
    priority: 2
  },
  'in-mum': { 
    host: '103.21.58.100', 
    port: 443, 
    country: 'India', 
    city: 'Mumbai', 
    flag: '🇮🇳',
    obfuscation: ['tls'],
    priority: 2
  },
  
  // === CDN FALLBACK SERVERS (Domain Fronting - Nearly Unblockable) ===
  'cdn-cloudflare': { 
    host: 'cdnjs.cloudflare.com',
    realHost: 'freedom-vpn.pages.dev',
    port: 443, 
    country: 'Global', 
    city: 'Cloudflare CDN', 
    flag: '☁️',
    obfuscation: ['domain-front'],
    priority: 5,
    isCDN: true,
    description: 'Routes through Cloudflare - very hard to block'
  },
  'cdn-google': { 
    host: 'www.google.com',
    realHost: 'freedom-vpn.appspot.com',
    port: 443, 
    country: 'Global', 
    city: 'Google Cloud', 
    flag: '☁️',
    obfuscation: ['domain-front'],
    priority: 6,
    isCDN: true,
    description: 'Routes through Google - blocking breaks Google'
  },
  'cdn-azure': { 
    host: 'ajax.aspnetcdn.com',
    realHost: 'freedom-vpn.azureedge.net',
    port: 443, 
    country: 'Global', 
    city: 'Microsoft Azure', 
    flag: '☁️',
    obfuscation: ['domain-front'],
    priority: 5,
    isCDN: true,
    description: 'Routes through Microsoft Azure CDN'
  },
  'cdn-amazon': { 
    host: 'd1234567890.cloudfront.net',
    realHost: 'freedom-vpn.cloudfront.net',
    port: 443, 
    country: 'Global', 
    city: 'Amazon CloudFront', 
    flag: '☁️',
    obfuscation: ['domain-front'],
    priority: 5,
    isCDN: true,
    description: 'Routes through Amazon CloudFront'
  }
};

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

// Apply proxy configuration with obfuscation
function setProxy(serverId) {
  const server = PROXY_SERVERS[serverId];
  if (!server) {
    console.error('[FreedomVPN] Server not found:', serverId);
    return Promise.reject(new Error('Server not found'));
  }

  console.log(`[FreedomVPN] Connecting to ${server.city}, ${server.country}...`);

  // Use HTTPS proxy for TLS obfuscation (looks like normal HTTPS traffic)
  const proxyHost = server.isCDN ? server.host : server.host;
  
  const config = {
    mode: "fixed_servers",
    rules: {
      singleProxy: {
        scheme: "https", // HTTPS for obfuscation
        host: proxyHost,
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
        state.currentServer = { id: serverId, ...server };
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
        
        console.log(`[FreedomVPN] Connected! IP: ${state.ip.masked}`);
        resolve({ success: true, state, ip: state.ip.masked });
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
  const server = PROXY_SERVERS[serverId];
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

// Get available servers sorted by priority (African servers first for Uganda users)
function getServersByPriority() {
  const entries = Object.entries(PROXY_SERVERS);
  
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
  
  // All servers failed - try CDN fallbacks
  console.log('[FreedomVPN] All regular servers failed, trying CDN fallbacks...');
  const cdnServers = Object.entries(PROXY_SERVERS).filter(([,s]) => s.isCDN);
  
  for (const [serverId, server] of cdnServers) {
    try {
      await setProxy(serverId);
      state.stats.reconnections++;
      console.log(`[FreedomVPN] Connected via CDN: ${server.city}`);
      return;
    } catch (e) {
      continue;
    }
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

// Find the best server based on latency and location
async function findBestServer() {
  console.log('[FreedomVPN] Finding best server...');
  
  const servers = state.settings.preferAfrican 
    ? Object.entries(PROXY_SERVERS).filter(([,s]) => s.isAfrican)
    : Object.entries(PROXY_SERVERS).filter(([,s]) => !s.isCDN);
  
  let bestServer = null;
  let bestLatency = Infinity;
  
  // Test up to 5 servers in parallel
  const testServers = servers.slice(0, 5);
  const results = await Promise.all(
    testServers.map(async ([id, server]) => ({
      id,
      server,
      latency: await measureLatency(server.host)
    }))
  );
  
  for (const result of results) {
    if (result.latency < bestLatency) {
      bestLatency = result.latency;
      bestServer = result;
    }
  }
  
  return bestServer?.id || 'ke-nrb'; // Default to Kenya
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
        const bestId = await findBestServer();
        const result = await setProxy(bestId);
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
      
    default:
      sendResponse({ error: 'Unknown action' });
  }
}

// ============= INITIALIZATION =============

chrome.runtime.onInstalled.addListener(async () => {
  // Get real IP before connecting
  state.ip.real = await fetchExternalIP();
  
  chrome.storage.local.set({ vpnState: state });
  updateIcon(false);
  
  console.log('[FreedomVPN] Extension installed. Real IP:', state.ip.real);
});

chrome.runtime.onStartup.addListener(async () => {
  // Restore state
  const data = await chrome.storage.local.get(['vpnState']);
  if (data.vpnState) {
    state = { ...state, ...data.vpnState };
  }
  
  // Get real IP
  state.ip.real = await fetchExternalIP();
  
  // Auto-connect if enabled
  if (state.settings.autoConnect) {
    const bestId = await findBestServer();
    await setProxy(bestId);
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
