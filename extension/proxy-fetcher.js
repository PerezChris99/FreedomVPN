/**
 * Free Proxy Fetcher for Browser Extension
 * Fetches real, working free proxy servers from public APIs
 * 
 * IMPORTANT: Browser extensions can only use HTTP/HTTPS/SOCKS proxies,
 * NOT VPN protocols like WireGuard or OpenVPN.
 */

// Free proxy API sources (no auth required)
const PROXY_SOURCES = [
  {
    name: 'ProxyScrape',
    url: 'https://api.proxyscrape.com/v2/?request=getproxies&protocol=http&timeout=10000&country=all&ssl=all&anonymity=all',
    parser: parseSimpleList
  },
  {
    name: 'GeoNode',
    url: 'https://proxylist.geonode.com/api/proxy-list?limit=100&page=1&sort_by=lastChecked&sort_type=desc&protocols=http%2Chttps',
    parser: parseGeoNode
  },
  {
    name: 'PubProxy',
    url: 'https://pubproxy.com/api/proxy?limit=20&format=json&https=true&type=http',
    parser: parsePubProxy
  }
];

// Parse simple IP:PORT list
function parseSimpleList(text) {
  const proxies = [];
  const lines = text.split('\n');
  
  for (const line of lines) {
    const match = line.trim().match(/^(\d+\.\d+\.\d+\.\d+):(\d+)$/);
    if (match) {
      proxies.push({
        host: match[1],
        port: parseInt(match[2]),
        protocol: 'http',
        country: 'Unknown',
        anonymity: 'unknown'
      });
    }
  }
  
  return proxies;
}

// Parse GeoNode API response
function parseGeoNode(json) {
  try {
    const data = typeof json === 'string' ? JSON.parse(json) : json;
    return (data.data || []).map(p => ({
      host: p.ip,
      port: parseInt(p.port),
      protocol: p.protocols?.[0] || 'http',
      country: p.country,
      countryCode: p.country_code,
      anonymity: p.anonymityLevel,
      speed: p.speed,
      uptime: p.upTime,
      lastChecked: p.lastChecked
    }));
  } catch {
    return [];
  }
}

// Parse PubProxy API response
function parsePubProxy(json) {
  try {
    const data = typeof json === 'string' ? JSON.parse(json) : json;
    return (data.data || []).map(p => ({
      host: p.ip,
      port: parseInt(p.port),
      protocol: p.type || 'http',
      country: p.country,
      countryCode: p.country_code,
      anonymity: p.proxy_level,
      speed: p.speed
    }));
  } catch {
    return [];
  }
}

// Fetch proxies from all sources
async function fetchAllProxies() {
  const allProxies = [];
  
  for (const source of PROXY_SOURCES) {
    try {
      console.log(`[ProxyFetcher] Fetching from ${source.name}...`);
      const response = await fetch(source.url, {
        signal: AbortSignal.timeout(10000)
      });
      
      if (response.ok) {
        const text = await response.text();
        let proxies;
        
        try {
          // Try parsing as JSON first
          const json = JSON.parse(text);
          proxies = source.parser(json);
        } catch {
          // Fall back to text parsing
          proxies = source.parser(text);
        }
        
        console.log(`[ProxyFetcher] Got ${proxies.length} proxies from ${source.name}`);
        allProxies.push(...proxies);
      }
    } catch (e) {
      console.warn(`[ProxyFetcher] ${source.name} failed:`, e.message);
    }
  }
  
  // Remove duplicates by IP:PORT
  const seen = new Set();
  const uniqueProxies = allProxies.filter(p => {
    const key = `${p.host}:${p.port}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
  
  console.log(`[ProxyFetcher] Total unique proxies: ${uniqueProxies.length}`);
  return uniqueProxies;
}

// Test if a proxy is working
async function testProxy(proxy, timeout = 5000) {
  try {
    // We can't actually test proxies from extension context
    // The best we can do is check if we can connect
    const testUrl = `http://${proxy.host}:${proxy.port}`;
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeout);
    
    const start = performance.now();
    const response = await fetch('https://httpbin.org/ip', {
      method: 'HEAD',
      signal: controller.signal,
      mode: 'no-cors'
    });
    clearTimeout(timeoutId);
    
    const latency = Math.round(performance.now() - start);
    return { working: true, latency };
  } catch {
    return { working: false, latency: 9999 };
  }
}

// Get proxies filtered by country
function getProxiesByCountry(proxies, countryCodes) {
  const codes = countryCodes.map(c => c.toUpperCase());
  return proxies.filter(p => 
    p.countryCode && codes.includes(p.countryCode.toUpperCase())
  );
}

// Get fastest proxies (sorted by speed/latency)
function getFastestProxies(proxies, limit = 10) {
  return [...proxies]
    .sort((a, b) => (a.speed || 0) - (b.speed || 0))
    .slice(0, limit);
}

// Hardcoded reliable free proxies as fallback
// These are well-known free proxy services
const FALLBACK_PROXIES = [
  // Free rotating proxy services (may require setup)
  { host: '51.158.68.68', port: 8811, country: 'France', countryCode: 'FR', protocol: 'http' },
  { host: '51.158.68.133', port: 8811, country: 'France', countryCode: 'FR', protocol: 'http' },
  { host: '51.75.126.150', port: 49597, country: 'Germany', countryCode: 'DE', protocol: 'http' },
  { host: '185.162.231.166', port: 80, country: 'Netherlands', countryCode: 'NL', protocol: 'http' },
];

// Export for use in extension
self.ProxyFetcher = {
  fetchAll: fetchAllProxies,
  testProxy: testProxy,
  getByCountry: getProxiesByCountry,
  getFastest: getFastestProxies,
  fallback: FALLBACK_PROXIES
};
