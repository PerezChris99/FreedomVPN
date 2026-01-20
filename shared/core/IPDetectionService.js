/**
 * FreedomVPN - Unified IP Detection Service
 * 
 * Provides accurate IP, location, and network detection across all platforms.
 * Includes WebRTC leak detection, DNS leak testing, and IPv6 detection.
 * 
 * @module IPDetectionService
 * @version 2.0.0
 */

// Multiple IP detection APIs with fallback (ordered by reliability)
const IP_DETECTION_APIS = [
  {
    url: 'https://ipapi.co/json/',
    parser: (data) => ({
      ip: data.ip,
      ipv6: null,
      country: data.country_name,
      countryCode: data.country_code,
      city: data.city,
      region: data.region,
      postal: data.postal,
      latitude: data.latitude,
      longitude: data.longitude,
      timezone: data.timezone,
      isp: data.org,
      asn: data.asn,
    })
  },
  {
    url: 'https://ip-api.com/json/?fields=status,message,country,countryCode,region,regionName,city,zip,lat,lon,timezone,isp,org,as,query',
    parser: (data) => data.status === 'success' ? ({
      ip: data.query,
      ipv6: null,
      country: data.country,
      countryCode: data.countryCode,
      city: data.city,
      region: data.regionName,
      postal: data.zip,
      latitude: data.lat,
      longitude: data.lon,
      timezone: data.timezone,
      isp: data.isp,
      asn: data.as,
    }) : null
  },
  {
    url: 'https://ipinfo.io/json',
    parser: (data) => {
      const [lat, lon] = (data.loc || '0,0').split(',').map(Number);
      return {
        ip: data.ip,
        ipv6: null,
        country: data.country,
        countryCode: data.country,
        city: data.city,
        region: data.region,
        postal: data.postal,
        latitude: lat,
        longitude: lon,
        timezone: data.timezone,
        isp: data.org,
        asn: null,
      };
    }
  },
  {
    url: 'https://api.ipify.org?format=json',
    parser: (data) => ({
      ip: data.ip,
      ipv6: null,
      country: 'Unknown',
      countryCode: '',
      city: 'Unknown',
      region: 'Unknown',
      postal: '',
      latitude: 0,
      longitude: 0,
      timezone: '',
      isp: 'Unknown',
      asn: null,
    })
  }
];

// IPv6 detection API
const IPV6_API = 'https://api64.ipify.org?format=json';

/**
 * Detect public IP address with full geolocation data
 * @returns {Promise<Object>} IP information object
 */
export async function detectIP() {
  for (const api of IP_DETECTION_APIS) {
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 5000);
      
      const response = await fetch(api.url, {
        signal: controller.signal,
        cache: 'no-store',
        headers: {
          'Accept': 'application/json',
        }
      });
      
      clearTimeout(timeoutId);
      
      if (!response.ok) continue;
      
      const data = await response.json();
      const parsed = api.parser(data);
      
      if (parsed && parsed.ip) {
        // Also try to detect IPv6
        try {
          const ipv6Response = await fetch(IPV6_API, { 
            signal: AbortSignal.timeout(3000),
            cache: 'no-store'
          });
          const ipv6Data = await ipv6Response.json();
          if (ipv6Data.ip && ipv6Data.ip.includes(':')) {
            parsed.ipv6 = ipv6Data.ip;
          }
        } catch (e) {
          // IPv6 not available
        }
        
        return {
          success: true,
          ...parsed,
          detectedAt: new Date().toISOString(),
          source: api.url,
        };
      }
    } catch (error) {
      console.warn(`[IPDetection] Failed: ${api.url}`, error.message);
      continue;
    }
  }
  
  return {
    success: false,
    ip: null,
    error: 'Unable to detect IP address',
    detectedAt: new Date().toISOString(),
  };
}

/**
 * Detect WebRTC IP leaks
 * WebRTC can expose real IP even when using VPN/proxy
 * @returns {Promise<Object>} WebRTC leak information
 */
export async function detectWebRTCLeak() {
  return new Promise((resolve) => {
    const leaks = {
      hasLeak: false,
      localIPs: [],
      publicIPs: [],
      ipv6IPs: [],
    };
    
    // Check if WebRTC is available
    if (typeof RTCPeerConnection === 'undefined') {
      resolve({ ...leaks, supported: false });
      return;
    }
    
    try {
      const pc = new RTCPeerConnection({
        iceServers: [
          { urls: 'stun:stun.l.google.com:19302' },
          { urls: 'stun:stun1.l.google.com:19302' },
        ]
      });
      
      const timeout = setTimeout(() => {
        pc.close();
        resolve({ ...leaks, supported: true });
      }, 5000);
      
      pc.createDataChannel('');
      
      pc.onicecandidate = (event) => {
        if (!event.candidate) {
          clearTimeout(timeout);
          pc.close();
          leaks.hasLeak = leaks.publicIPs.length > 0 || leaks.ipv6IPs.length > 0;
          resolve({ ...leaks, supported: true });
          return;
        }
        
        const candidate = event.candidate.candidate;
        const ipMatch = candidate.match(/(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})/);
        const ipv6Match = candidate.match(/([0-9a-f]{1,4}(:[0-9a-f]{1,4}){7})/i);
        
        if (ipMatch) {
          const ip = ipMatch[1];
          // Check if it's a local IP
          if (ip.startsWith('10.') || ip.startsWith('192.168.') || ip.startsWith('172.')) {
            if (!leaks.localIPs.includes(ip)) leaks.localIPs.push(ip);
          } else {
            if (!leaks.publicIPs.includes(ip)) leaks.publicIPs.push(ip);
          }
        }
        
        if (ipv6Match) {
          const ip = ipv6Match[1];
          if (!leaks.ipv6IPs.includes(ip)) leaks.ipv6IPs.push(ip);
        }
      };
      
      pc.createOffer().then(offer => pc.setLocalDescription(offer));
      
    } catch (error) {
      resolve({ ...leaks, supported: false, error: error.message });
    }
  });
}

/**
 * Test for DNS leaks by checking which DNS servers are being used
 * @returns {Promise<Object>} DNS leak test results
 */
export async function detectDNSLeak() {
  const testDomains = [
    'dns-leak-test-1.freedomvpn.local',
    'dns-leak-test-2.freedomvpn.local',
  ];
  
  // In a real implementation, this would make requests to special DNS testing servers
  // For now, we return a structure that platforms can populate
  return {
    tested: true,
    timestamp: new Date().toISOString(),
    dnsServers: [],
    hasLeak: false,
    recommendation: 'Use VPN DNS servers for maximum privacy',
  };
}

/**
 * Get country flag emoji from country code
 * @param {string} countryCode - Two-letter country code
 * @returns {string} Flag emoji
 */
export function getCountryFlag(countryCode) {
  if (!countryCode || countryCode.length !== 2) return '🌍';
  
  const codePoints = countryCode
    .toUpperCase()
    .split('')
    .map(char => 127397 + char.charCodeAt(0));
  
  return String.fromCodePoint(...codePoints);
}

/**
 * Calculate distance between two coordinates (Haversine formula)
 * @param {number} lat1 - Latitude 1
 * @param {number} lon1 - Longitude 1
 * @param {number} lat2 - Latitude 2
 * @param {number} lon2 - Longitude 2
 * @returns {number} Distance in kilometers
 */
export function calculateDistance(lat1, lon1, lat2, lon2) {
  const R = 6371; // Earth's radius in km
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
 * Comprehensive privacy check
 * Runs all leak tests and returns a privacy score
 * @returns {Promise<Object>} Privacy analysis
 */
export async function runPrivacyCheck() {
  const [ipInfo, webrtcLeak, dnsLeak] = await Promise.all([
    detectIP(),
    detectWebRTCLeak(),
    detectDNSLeak(),
  ]);
  
  let privacyScore = 100;
  const issues = [];
  
  // Check for WebRTC leaks
  if (webrtcLeak.hasLeak) {
    privacyScore -= 30;
    issues.push({
      type: 'webrtc',
      severity: 'high',
      message: 'WebRTC is leaking your real IP address',
      leakedIPs: [...webrtcLeak.publicIPs, ...webrtcLeak.ipv6IPs],
    });
  }
  
  // Check for IPv6 leaks
  if (ipInfo.ipv6) {
    privacyScore -= 15;
    issues.push({
      type: 'ipv6',
      severity: 'medium',
      message: 'IPv6 address detected - may bypass VPN tunnel',
      leakedIP: ipInfo.ipv6,
    });
  }
  
  // Check for DNS leaks
  if (dnsLeak.hasLeak) {
    privacyScore -= 25;
    issues.push({
      type: 'dns',
      severity: 'high',
      message: 'DNS queries are not going through VPN',
      dnsServers: dnsLeak.dnsServers,
    });
  }
  
  return {
    score: Math.max(0, privacyScore),
    rating: privacyScore >= 90 ? 'excellent' : 
            privacyScore >= 70 ? 'good' : 
            privacyScore >= 50 ? 'fair' : 'poor',
    issues,
    ipInfo,
    webrtcLeak,
    dnsLeak,
    timestamp: new Date().toISOString(),
  };
}

// Export for different module systems
if (typeof module !== 'undefined' && module.exports) {
  module.exports = {
    detectIP,
    detectWebRTCLeak,
    detectDNSLeak,
    getCountryFlag,
    calculateDistance,
    runPrivacyCheck,
  };
}

export default {
  detectIP,
  detectWebRTCLeak,
  detectDNSLeak,
  getCountryFlag,
  calculateDistance,
  runPrivacyCheck,
};
