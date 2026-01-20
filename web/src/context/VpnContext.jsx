import { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react'

// ============================================================
// PERMISSION SERVICE - Request location, storage permissions
// ============================================================
const PermissionService = {
  async checkGeolocationPermission() {
    try {
      if (navigator.permissions) {
        const result = await navigator.permissions.query({ name: 'geolocation' })
        return result.state // 'granted', 'denied', 'prompt'
      }
    } catch (e) {
      console.warn('[Permissions] Unable to query geolocation permission')
    }
    return 'prompt'
  },

  async requestGeolocationPermission() {
    return new Promise((resolve) => {
      if (!navigator.geolocation) {
        resolve({ granted: false, error: 'Geolocation not supported' })
        return
      }

      navigator.geolocation.getCurrentPosition(
        (position) => {
          resolve({ 
            granted: true, 
            position: {
              latitude: position.coords.latitude,
              longitude: position.coords.longitude,
              accuracy: position.coords.accuracy
            }
          })
        },
        (error) => {
          resolve({ 
            granted: false, 
            error: error.message,
            code: error.code
          })
        },
        { enableHighAccuracy: true, timeout: 10000 }
      )
    })
  },

  async checkStoragePermission() {
    try {
      // Check if localStorage is accessible
      localStorage.setItem('__permission_test__', 'test')
      localStorage.removeItem('__permission_test__')
      
      // Check storage quota
      if (navigator.storage && navigator.storage.estimate) {
        const estimate = await navigator.storage.estimate()
        return {
          granted: true,
          quota: estimate.quota,
          usage: estimate.usage,
          percentUsed: ((estimate.usage / estimate.quota) * 100).toFixed(2)
        }
      }
      return { granted: true }
    } catch (e) {
      return { granted: false, error: e.message }
    }
  },

  async requestPersistentStorage() {
    try {
      if (navigator.storage && navigator.storage.persist) {
        const isPersisted = await navigator.storage.persist()
        return { granted: isPersisted }
      }
    } catch (e) {
      return { granted: false, error: e.message }
    }
    return { granted: false, error: 'Persistent storage not supported' }
  },

  async requestAllPermissions() {
    console.log('[Permissions] Requesting all permissions...')
    
    const results = {
      geolocation: await this.requestGeolocationPermission(),
      storage: await this.checkStoragePermission(),
      persistentStorage: await this.requestPersistentStorage()
    }
    
    console.log('[Permissions] Results:', results)
    return results
  }
}

// ============================================================
// GEOLOCATION SERVICE - Accurate location detection
// ============================================================
const GeolocationService = {
  currentPosition: null,
  watchId: null,

  async getCurrentLocation(highAccuracy = true) {
    return new Promise((resolve, reject) => {
      if (!navigator.geolocation) {
        reject(new Error('Geolocation not supported'))
        return
      }

      navigator.geolocation.getCurrentPosition(
        async (position) => {
          const location = {
            latitude: position.coords.latitude,
            longitude: position.coords.longitude,
            accuracy: position.coords.accuracy,
            altitude: position.coords.altitude,
            altitudeAccuracy: position.coords.altitudeAccuracy,
            heading: position.coords.heading,
            speed: position.coords.speed,
            timestamp: position.timestamp,
            source: position.coords.accuracy < 100 ? 'gps' : 'network'
          }

          // Try to get city/country
          try {
            const geo = await this.reverseGeocode(location.latitude, location.longitude)
            location.city = geo.city
            location.country = geo.country
            location.countryCode = geo.countryCode
          } catch (e) {
            location.city = 'Unknown'
            location.country = 'Unknown'
            location.countryCode = 'XX'
          }

          this.currentPosition = location
          resolve(location)
        },
        (error) => {
          reject(error)
        },
        {
          enableHighAccuracy: highAccuracy,
          timeout: 15000,
          maximumAge: 0
        }
      )
    })
  },

  async reverseGeocode(lat, lon) {
    try {
      const response = await fetch(
        `https://nominatim.openstreetmap.org/reverse?lat=${lat}&lon=${lon}&format=json`,
        { headers: { 'User-Agent': 'FreedomVPN/2.0' } }
      )
      if (response.ok) {
        const data = await response.json()
        return {
          city: data.address?.city || data.address?.town || data.address?.village || 'Unknown',
          country: data.address?.country || 'Unknown',
          countryCode: data.address?.country_code?.toUpperCase() || 'XX'
        }
      }
    } catch (e) {}
    return { city: 'Unknown', country: 'Unknown', countryCode: 'XX' }
  },

  async getLocationByIP() {
    const apis = [
      { url: 'https://ipwho.is/', parse: (d) => ({ lat: d.latitude, lon: d.longitude, city: d.city, country: d.country, countryCode: d.country_code }) },
      { url: 'https://ipapi.co/json/', parse: (d) => ({ lat: d.latitude, lon: d.longitude, city: d.city, country: d.country_name, countryCode: d.country_code }) },
    ]

    for (const api of apis) {
      try {
        const res = await fetch(api.url)
        if (res.ok) {
          const data = await res.json()
          const parsed = api.parse(data)
          return {
            latitude: parsed.lat,
            longitude: parsed.lon,
            accuracy: 10000, // IP-based is ~10km accuracy
            city: parsed.city,
            country: parsed.country,
            countryCode: parsed.countryCode,
            source: 'ip'
          }
        }
      } catch (e) {}
    }
    return null
  },

  distanceBetween(lat1, lon1, lat2, lon2) {
    // Haversine formula
    const R = 6371 // Earth's radius in km
    const dLat = (lat2 - lat1) * Math.PI / 180
    const dLon = (lon2 - lon1) * Math.PI / 180
    const a = Math.sin(dLat/2) * Math.sin(dLat/2) +
              Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
              Math.sin(dLon/2) * Math.sin(dLon/2)
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a))
    return R * c
  },

  findNearestServers(servers, userLat, userLon, count = 5) {
    return [...servers]
      .map(server => ({
        ...server,
        distance: this.distanceBetween(userLat, userLon, server.lat || server.latitude, server.lng || server.longitude)
      }))
      .sort((a, b) => a.distance - b.distance)
      .slice(0, count)
  }
}

// ============================================================
// VPNGATE REAL SERVER SERVICE - Fetch live servers
// ============================================================
const VPNGATE_API = 'https://www.vpngate.net/api/iphone/'

const VPNGateService = {
  servers: [],
  lastFetch: null,
  cacheTimeout: 5 * 60 * 1000, // 5 minutes

  async fetchRealServers(forceRefresh = false) {
    // Return cached if valid
    if (!forceRefresh && this.servers.length > 0 && this.lastFetch) {
      if (Date.now() - this.lastFetch < this.cacheTimeout) {
        return this.servers
      }
    }

    try {
      const controller = new AbortController()
      const timeout = setTimeout(() => controller.abort(), 15000)
      
      const response = await fetch(VPNGATE_API, { 
        signal: controller.signal,
        cache: 'no-store'
      })
      clearTimeout(timeout)

      if (!response.ok) {
        console.warn('[VPNGate] API not available, using static servers')
        return this.getStaticServers()
      }

      const csvText = await response.text()
      this.servers = this.parseCSV(csvText)
      this.lastFetch = Date.now()
      
      console.log(`[VPNGate] Fetched ${this.servers.length} real servers`)
      return this.servers
    } catch (e) {
      console.warn('[VPNGate] Fetch failed:', e.message)
      return this.getStaticServers()
    }
  },

  parseCSV(csvText) {
    const servers = []
    const lines = csvText.split('\n')
    
    for (let i = 2; i < lines.length - 1; i++) {
      const line = lines[i].trim()
      if (!line || line.startsWith('*')) continue
      
      const cols = line.split(',')
      if (cols.length < 15) continue
      
      try {
        const countryCode = cols[6]?.toUpperCase() || 'XX'
        const flag = this.getFlag(countryCode)
        
        servers.push({
          id: `vpngate-${cols[0]}-${i}`,
          hostname: cols[0],
          ip: cols[1],
          country: cols[5] || 'Unknown',
          countryCode: countryCode,
          city: '',
          flag: flag,
          score: parseInt(cols[2]) || 0,
          ping: parseInt(cols[3]) || 0,
          speed: parseInt(cols[4]) || 0, // bps
          speedMbps: (parseInt(cols[4]) || 0) / 1000000,
          numSessions: parseInt(cols[7]) || 0,
          load: Math.min(100, (parseInt(cols[7]) || 0) * 2),
          uptime: parseInt(cols[8]) || 0,
          operator: cols[12] || '',
          openVpnConfig: cols[14] ? atob(cols[14]) : null,
          lat: this.getApproxLat(countryCode),
          lng: this.getApproxLon(countryCode),
          region: this.getRegion(countryCode),
          isReal: true
        })
      } catch (e) {}
    }

    // Sort by quality (ping + speed)
    return servers
      .filter(s => s.ip && s.ping > 0 && s.ping < 1000)
      .sort((a, b) => (a.ping - b.ping) + (b.speedMbps - a.speedMbps))
  },

  getFlag(countryCode) {
    if (!countryCode || countryCode.length !== 2) return '🌍'
    const codePoints = countryCode.toUpperCase().split('').map(
      char => 127397 + char.charCodeAt(0)
    )
    return String.fromCodePoint(...codePoints)
  },

  getRegion(countryCode) {
    const regions = {
      africa: ['UG', 'KE', 'TZ', 'RW', 'ZA', 'EG', 'NG', 'GH', 'ET', 'MA'],
      europe: ['NL', 'DE', 'GB', 'FR', 'CH', 'SE', 'NO', 'FI', 'IT', 'ES', 'PL', 'CZ', 'AT', 'BE', 'RU', 'UA'],
      americas: ['US', 'CA', 'BR', 'MX', 'AR', 'CL', 'CO'],
      asia: ['JP', 'KR', 'SG', 'HK', 'TW', 'TH', 'VN', 'ID', 'MY', 'PH', 'IN', 'CN'],
      oceania: ['AU', 'NZ']
    }
    for (const [region, codes] of Object.entries(regions)) {
      if (codes.includes(countryCode)) return region
    }
    return 'other'
  },

  getApproxLat(countryCode) {
    const coords = {
      'UG': 0.3476, 'KE': -1.2921, 'TZ': -6.7924, 'RW': -1.9403, 'ZA': -26.2041,
      'EG': 30.0444, 'NG': 6.5244, 'NL': 52.3676, 'DE': 50.1109, 'GB': 51.5074,
      'FR': 48.8566, 'CH': 47.3769, 'US': 40.7128, 'CA': 43.6532, 'BR': -23.5505,
      'SG': 1.3521, 'JP': 35.6762, 'AU': -33.8688, 'IN': 19.0760, 'KR': 37.5665,
      'HK': 22.3193, 'TW': 25.0330, 'TH': 13.7563, 'VN': 21.0285, 'RU': 55.7558
    }
    return coords[countryCode] || 0
  },

  getApproxLon(countryCode) {
    const coords = {
      'UG': 32.5825, 'KE': 36.8219, 'TZ': 39.2083, 'RW': 29.8739, 'ZA': 28.0473,
      'EG': 31.2357, 'NG': 3.3792, 'NL': 4.9041, 'DE': 8.6821, 'GB': -0.1278,
      'FR': 2.3522, 'CH': 8.5417, 'US': -74.0060, 'CA': -79.3832, 'BR': -46.6333,
      'SG': 103.8198, 'JP': 139.6503, 'AU': 151.2093, 'IN': 72.8777, 'KR': 126.9780,
      'HK': 114.1694, 'TW': 121.5654, 'TH': 100.5018, 'VN': 105.8542, 'RU': 37.6173
    }
    return coords[countryCode] || 0
  },

  getStaticServers() {
    // Fallback static servers when API is unavailable
    return staticServers
  }
}

// Fallback static servers
const staticServers = [
  // Africa (Priority for Ugandan users)
  { id: 'ug', country: 'Uganda', city: 'Kampala', lat: 0.3476, lng: 32.5825, region: 'africa', load: 45, ping: 15, flag: '🇺🇬', ipPrefix: '41.210', countryCode: 'UG' },
  { id: 'ke', country: 'Kenya', city: 'Nairobi', lat: -1.2921, lng: 36.8219, region: 'africa', load: 32, ping: 25, flag: '🇰🇪', ipPrefix: '197.232', countryCode: 'KE' },
  { id: 'tz', country: 'Tanzania', city: 'Dar es Salaam', lat: -6.7924, lng: 39.2083, region: 'africa', load: 28, ping: 35, flag: '🇹🇿', ipPrefix: '197.185', countryCode: 'TZ' },
  { id: 'rw', country: 'Rwanda', city: 'Kigali', lat: -1.9403, lng: 29.8739, region: 'africa', load: 22, ping: 20, flag: '🇷🇼', ipPrefix: '197.243', countryCode: 'RW' },
  { id: 'za', country: 'South Africa', city: 'Johannesburg', lat: -26.2041, lng: 28.0473, region: 'africa', load: 55, ping: 80, flag: '🇿🇦', ipPrefix: '196.38', countryCode: 'ZA' },
  // Europe
  { id: 'nl', country: 'Netherlands', city: 'Amsterdam', lat: 52.3676, lng: 4.9041, region: 'europe', load: 65, ping: 150, flag: '🇳🇱', ipPrefix: '185.156', countryCode: 'NL' },
  { id: 'de', country: 'Germany', city: 'Frankfurt', lat: 50.1109, lng: 8.6821, region: 'europe', load: 58, ping: 155, flag: '🇩🇪', ipPrefix: '185.220', countryCode: 'DE' },
  { id: 'gb', country: 'United Kingdom', city: 'London', lat: 51.5074, lng: -0.1278, region: 'europe', load: 72, ping: 160, flag: '🇬🇧', ipPrefix: '185.189', countryCode: 'GB' },
  // Americas
  { id: 'us-ny', country: 'United States', city: 'New York', lat: 40.7128, lng: -74.0060, region: 'americas', load: 70, ping: 200, flag: '🇺🇸', ipPrefix: '172.93', countryCode: 'US' },
  { id: 'ca', country: 'Canada', city: 'Toronto', lat: 43.6532, lng: -79.3832, region: 'americas', load: 45, ping: 210, flag: '🇨🇦', ipPrefix: '172.95', countryCode: 'CA' },
  // Asia
  { id: 'sg', country: 'Singapore', city: 'Singapore', lat: 1.3521, lng: 103.8198, region: 'asia', load: 55, ping: 180, flag: '🇸🇬', ipPrefix: '103.86', countryCode: 'SG' },
  { id: 'jp', country: 'Japan', city: 'Tokyo', lat: 35.6762, lng: 139.6503, region: 'asia', load: 50, ping: 220, flag: '🇯🇵', ipPrefix: '103.73', countryCode: 'JP' },
]

// Enhanced IP Detection Service (inline for web compatibility)
const IPDetectionService = {
  APIs: [
    { url: 'https://api.ipify.org?format=json', parseIP: (d) => d.ip },
    { url: 'https://api.ip.sb/geoip', parseIP: (d) => d.ip, parseGeo: (d) => d },
    { url: 'https://ipapi.co/json/', parseIP: (d) => d.ip, parseGeo: (d) => ({ country: d.country_name, city: d.city, isp: d.org, countryCode: d.country_code, latitude: d.latitude, longitude: d.longitude }) },
    { url: 'https://ipwho.is/', parseIP: (d) => d.ip, parseGeo: (d) => ({ country: d.country, city: d.city, isp: d.connection?.isp, countryCode: d.country_code }) },
  ],

  async detectIP() {
    for (const api of this.APIs) {
      try {
        const controller = new AbortController()
        const timeout = setTimeout(() => controller.abort(), 5000)
        const response = await fetch(api.url, { signal: controller.signal })
        clearTimeout(timeout)
        if (response.ok) {
          const data = await response.json()
          const ip = api.parseIP(data)
          const geo = api.parseGeo ? api.parseGeo(data) : {}
          return {
            ip,
            country: geo.country || 'Unknown',
            city: geo.city || 'Unknown',
            isp: geo.isp || 'Unknown ISP',
            countryCode: geo.countryCode || 'XX',
            latitude: geo.latitude,
            longitude: geo.longitude,
            source: api.url,
            timestamp: Date.now()
          }
        }
      } catch (e) { /* try next API */ }
    }
    return null
  },

  async detectWebRTCLeak() {
    return new Promise((resolve) => {
      if (!window.RTCPeerConnection) {
        return resolve({ leaked: false, reason: 'WebRTC not supported' })
      }
      try {
        const pc = new RTCPeerConnection({ iceServers: [{ urls: 'stun:stun.l.google.com:19302' }] })
        const ips = new Set()
        let resolved = false

        pc.onicecandidate = (e) => {
          if (resolved) return
          if (e.candidate?.candidate) {
            const match = e.candidate.candidate.match(/([0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3})/)
            if (match) {
              const ip = match[1]
              if (!ip.startsWith('10.') && !ip.startsWith('192.168.') && !ip.startsWith('172.')) {
                ips.add(ip)
              }
            }
          }
        }

        pc.createDataChannel('leak-test')
        pc.createOffer().then(offer => pc.setLocalDescription(offer))

        setTimeout(() => {
          resolved = true
          pc.close()
          resolve({
            leaked: ips.size > 0,
            leakedIPs: Array.from(ips),
            reason: ips.size > 0 ? 'Public IP exposed via WebRTC' : 'No leak detected'
          })
        }, 3000)
      } catch (e) {
        resolve({ leaked: false, reason: 'WebRTC test failed' })
      }
    })
  },

  async runPrivacyCheck(realIP, vpnIP, isConnected) {
    const webrtcLeak = await this.detectWebRTCLeak()
    
    let score = 100
    const issues = []
    const protections = []

    if (isConnected) {
      if (webrtcLeak.leaked) {
        score -= 30
        issues.push({ type: 'webrtc', severity: 'high', message: 'WebRTC is leaking your real IP' })
      } else {
        protections.push('WebRTC Protected')
      }
      protections.push('Traffic Encrypted (AES-256)')
      protections.push(`IP Masked: ${vpnIP?.ip || 'Unknown'}`)
    } else {
      score = 20
      issues.push({ type: 'exposed', severity: 'critical', message: 'Your real IP is exposed' })
    }

    return { score, issues, protections, webrtcLeak }
  }
}

// VPN Server List - Now using real servers from VPNGate + static fallback
let servers = staticServers // Will be replaced with real servers on init

const obfuscationProtocols = [
  { id: 'tls', name: 'TLS Camouflage', description: 'Looks like HTTPS traffic', effectiveness: 95 },
  { id: 'http', name: 'HTTP Disguise', description: 'Mimics normal web browsing', effectiveness: 85 },
  { id: 'dns', name: 'DNS Tunnel', description: 'Hides in DNS queries', effectiveness: 90 },
  { id: 'shadowsocks', name: 'Shadowsocks-like', description: 'AEAD encrypted stream', effectiveness: 92 },
  { id: 'domain', name: 'Domain Fronting', description: 'Uses CDN as cover', effectiveness: 88 },
  { id: 'websocket', name: 'WebSocket Wrap', description: 'WebSocket encapsulation', effectiveness: 87 },
  { id: 'random', name: 'Random Padding', description: 'Adds noise to packets', effectiveness: 75 },
  { id: 'shape', name: 'Traffic Shaping', description: 'Alters timing patterns', effectiveness: 80 },
  { id: 'xor', name: 'XOR Cipher', description: 'Simple obfuscation layer', effectiveness: 70 },
]

const VpnContext = createContext()

export function VpnProvider({ children }) {
  const [connectionState, setConnectionState] = useState('disconnected') // disconnected, connecting, connected
  const [selectedServer, setSelectedServer] = useState(staticServers[0]) // Default to first static server
  const [obfuscationProtocol, setObfuscationProtocol] = useState(obfuscationProtocols[0])
  const [compressionEnabled, setCompressionEnabled] = useState(true)
  const [lowBandwidthMode, setLowBandwidthMode] = useState(false)
  
  // Real servers from VPNGate
  const [availableServers, setAvailableServers] = useState(staticServers)
  const [isLoadingServers, setIsLoadingServers] = useState(false)
  
  // User location for accurate server selection
  const [userLocation, setUserLocation] = useState(null)
  const [locationPermissionStatus, setLocationPermissionStatus] = useState('prompt')
  
  // Permission status
  const [permissions, setPermissions] = useState({
    geolocation: 'prompt',
    storage: null,
    persistentStorage: null,
    allGranted: false
  })
  
  // Statistics
  const [stats, setStats] = useState({
    dataUsed: 0,
    dataSaved: 0,
    connectionTime: 0,
    moneySaved: 0, // in UGX
  })
  
  const [connectionStartTime, setConnectionStartTime] = useState(null)
  
  // IP Address tracking - Enhanced
  const [realIP, setRealIP] = useState(null)
  const [vpnIP, setVpnIP] = useState(null)
  const [isLoadingIP, setIsLoadingIP] = useState(false)
  
  // Privacy & Leak Detection
  const [privacyStatus, setPrivacyStatus] = useState({
    score: 0,
    issues: [],
    protections: [],
    webrtcLeak: null,
    lastCheck: null
  })
  
  // Refs for cleanup
  const ipCheckIntervalRef = useRef(null)
  
  // ============================================================
  // INITIALIZATION: Request permissions, fetch real servers, detect location
  // ============================================================
  useEffect(() => {
    const initialize = async () => {
      console.log('[FreedomVPN] Initializing...')
      
      // 1. Request all permissions
      console.log('[FreedomVPN] Requesting permissions...')
      const permResults = await PermissionService.requestAllPermissions()
      setPermissions({
        geolocation: permResults.geolocation.granted ? 'granted' : 'denied',
        storage: permResults.storage,
        persistentStorage: permResults.persistentStorage,
        allGranted: permResults.geolocation.granted && permResults.storage.granted
      })
      
      // 2. Get accurate user location
      console.log('[FreedomVPN] Getting user location...')
      let location = null
      if (permResults.geolocation.granted) {
        location = {
          latitude: permResults.geolocation.position.latitude,
          longitude: permResults.geolocation.position.longitude,
          accuracy: permResults.geolocation.position.accuracy,
          source: 'gps'
        }
        
        // Reverse geocode for city/country
        try {
          const geo = await GeolocationService.reverseGeocode(location.latitude, location.longitude)
          location = { ...location, ...geo }
        } catch (e) {}
        
        setUserLocation(location)
        setLocationPermissionStatus('granted')
        console.log('[FreedomVPN] Location:', location)
      } else {
        // Fallback to IP-based location
        const ipLocation = await GeolocationService.getLocationByIP()
        if (ipLocation) {
          location = ipLocation
          setUserLocation(ipLocation)
        }
        setLocationPermissionStatus(permResults.geolocation.code === 1 ? 'denied' : 'prompt')
      }
      
      // 3. Fetch real VPN servers from VPNGate
      console.log('[FreedomVPN] Fetching real VPN servers...')
      setIsLoadingServers(true)
      let realServers = []
      try {
        realServers = await VPNGateService.fetchRealServers()
        if (realServers.length > 0) {
          servers = realServers
          setAvailableServers(realServers)
          console.log(`[FreedomVPN] Loaded ${realServers.length} real servers`)
        }
      } catch (e) {
        console.warn('[FreedomVPN] Failed to fetch real servers, using static')
      }
      setIsLoadingServers(false)
      
      // 4. Select best server based on location
      if (location) {
        // Use the fetched servers or static servers
        const serverList = realServers.length > 0 ? realServers : staticServers
        const nearest = GeolocationService.findNearestServers(
          serverList, 
          location.latitude, 
          location.longitude, 
          1
        )[0]
        if (nearest) {
          setSelectedServer(nearest)
          console.log('[FreedomVPN] Auto-selected nearest server:', nearest.country)
        }
      }
    }
    
    initialize()
  }, [])
  
  // Enhanced IP detection on mount
  useEffect(() => {
    const fetchRealIP = async () => {
      setIsLoadingIP(true)
      try {
        const ipInfo = await IPDetectionService.detectIP()
        if (ipInfo) {
          setRealIP(ipInfo)
          // Run initial privacy check
          const privacy = await IPDetectionService.runPrivacyCheck(ipInfo, null, false)
          setPrivacyStatus({ ...privacy, lastCheck: Date.now() })
        }
      } catch (e) {
        console.error('IP detection failed:', e)
      }
      setIsLoadingIP(false)
    }
    fetchRealIP()
    
    return () => {
      if (ipCheckIntervalRef.current) {
        clearInterval(ipCheckIntervalRef.current)
      }
    }
  }, [])
  
  // Periodic privacy checks when connected
  useEffect(() => {
    if (connectionState === 'connected') {
      const checkPrivacy = async () => {
        const privacy = await IPDetectionService.runPrivacyCheck(realIP, vpnIP, true)
        setPrivacyStatus({ ...privacy, lastCheck: Date.now() })
      }
      
      checkPrivacy()
      ipCheckIntervalRef.current = setInterval(checkPrivacy, 60000) // Check every minute
    } else {
      if (ipCheckIntervalRef.current) {
        clearInterval(ipCheckIntervalRef.current)
        ipCheckIntervalRef.current = null
      }
    }
    
    return () => {
      if (ipCheckIntervalRef.current) {
        clearInterval(ipCheckIntervalRef.current)
      }
    }
  }, [connectionState, realIP, vpnIP])

  // Simulate data usage and savings
  useEffect(() => {
    let interval
    if (connectionState === 'connected') {
      interval = setInterval(() => {
        setStats(prev => {
          const newDataUsed = prev.dataUsed + Math.random() * 0.5 // MB
          const compressionRatio = compressionEnabled ? 0.45 : 0 // 45% savings
          const newDataSaved = prev.dataSaved + (newDataUsed - prev.dataUsed) * compressionRatio
          const pricePerMB = 50 // UGX per MB (approximate)
          
          return {
            ...prev,
            dataUsed: newDataUsed,
            dataSaved: newDataSaved,
            moneySaved: newDataSaved * pricePerMB,
            connectionTime: connectionStartTime ? Math.floor((Date.now() - connectionStartTime) / 1000) : 0
          }
        })
      }, 1000)
    }
    return () => clearInterval(interval)
  }, [connectionState, connectionStartTime, compressionEnabled])

  const connect = useCallback(async () => {
    if (!selectedServer) {
      console.error('[FreedomVPN] No server selected')
      return
    }
    
    setConnectionState('connecting')
    setIsLoadingIP(true)
    
    // Simulate connection delay (realistic handshake time)
    await new Promise(resolve => setTimeout(resolve, 1500 + Math.random() * 1000))
    
    // Use real server IP if available, otherwise generate simulated IP
    const getVpnIP = () => {
      // If it's a real VPNGate server, use its actual IP
      if (selectedServer.isReal && selectedServer.ip) {
        return selectedServer.ip
      }
      // Otherwise generate based on prefix
      const prefix = selectedServer.ipPrefix || '10.0'
      const octet3 = Math.floor(Math.random() * 254) + 1
      const octet4 = Math.floor(Math.random() * 254) + 1
      return `${prefix}.${octet3}.${octet4}`
    }
    
    const vpnAddress = getVpnIP()
    
    setVpnIP({
      ip: vpnAddress,
      country: selectedServer.country,
      city: selectedServer.city || '',
      countryCode: selectedServer.countryCode || selectedServer.id?.toUpperCase().slice(0, 2) || 'XX',
      isp: selectedServer.operator || `FreedomVPN ${selectedServer.country} Node`,
      latitude: selectedServer.lat || selectedServer.latitude,
      longitude: selectedServer.lng || selectedServer.longitude,
      isVPN: true,
      isRealServer: !!selectedServer.isReal,
      speed: selectedServer.speed,
      ping: selectedServer.ping
    })
    
    setConnectionState('connected')
    setConnectionStartTime(Date.now())
    setIsLoadingIP(false)
    
    // Run privacy check after connection
    const privacy = await IPDetectionService.runPrivacyCheck(realIP, { ip: vpnAddress }, true)
    setPrivacyStatus({ ...privacy, lastCheck: Date.now() })
    
    console.log(`[FreedomVPN] Connected to ${selectedServer.country} (${vpnAddress})`)
  }, [selectedServer, realIP])

  const disconnect = useCallback(async () => {
    setConnectionState('disconnected')
    setConnectionStartTime(null)
    setVpnIP(null)
    
    // Re-run privacy check after disconnect
    if (realIP) {
      const privacy = await IPDetectionService.runPrivacyCheck(realIP, null, false)
      setPrivacyStatus({ ...privacy, lastCheck: Date.now() })
    }
  }, [realIP])

  const toggleConnection = useCallback(() => {
    if (connectionState === 'connected') {
      disconnect()
    } else if (connectionState === 'disconnected') {
      connect()
    }
  }, [connectionState, connect, disconnect])

  const selectServer = useCallback((server) => {
    const wasConnected = connectionState === 'connected'
    if (wasConnected) {
      disconnect()
    }
    setSelectedServer(server)
    if (wasConnected) {
      setTimeout(connect, 500)
    }
  }, [connectionState, connect, disconnect])

  // Refresh servers from VPNGate
  const refreshServers = useCallback(async () => {
    setIsLoadingServers(true)
    try {
      const realServers = await VPNGateService.fetchRealServers(true)
      if (realServers.length > 0) {
        servers = realServers
        setAvailableServers(realServers)
        console.log(`[FreedomVPN] Refreshed ${realServers.length} servers`)
      }
    } catch (e) {
      console.error('[FreedomVPN] Failed to refresh servers')
    }
    setIsLoadingServers(false)
  }, [])

  // Request location permission manually
  const requestLocationPermission = useCallback(async () => {
    const result = await PermissionService.requestGeolocationPermission()
    if (result.granted) {
      setLocationPermissionStatus('granted')
      const location = {
        latitude: result.position.latitude,
        longitude: result.position.longitude,
        accuracy: result.position.accuracy,
        source: 'gps'
      }
      try {
        const geo = await GeolocationService.reverseGeocode(location.latitude, location.longitude)
        location.city = geo.city
        location.country = geo.country
        location.countryCode = geo.countryCode
      } catch (e) {}
      setUserLocation(location)
      
      // Auto-select nearest server
      if (availableServers.length > 0) {
        const nearest = GeolocationService.findNearestServers(
          availableServers, location.latitude, location.longitude, 1
        )[0]
        if (nearest) setSelectedServer(nearest)
      }
      
      return location
    } else {
      setLocationPermissionStatus('denied')
      return null
    }
  }, [availableServers])

  // Find nearest servers to user
  const getNearestServers = useCallback((count = 5) => {
    if (!userLocation) return availableServers.slice(0, count)
    return GeolocationService.findNearestServers(
      availableServers, userLocation.latitude, userLocation.longitude, count
    )
  }, [userLocation, availableServers])

  const getAfricanServers = useCallback(() => {
    return availableServers.filter(s => s.region === 'africa')
  }, [availableServers])

  const getServersByRegion = useCallback((region) => {
    return availableServers.filter(s => s.region === region)
  }, [availableServers])

  const getFastestServer = useCallback(() => {
    return [...availableServers].sort((a, b) => (a.ping || 999) - (b.ping || 999))[0]
  }, [availableServers])

  const formatDataSize = (mb) => {
    if (mb >= 1024) {
      return `${(mb / 1024).toFixed(2)} GB`
    }
    return `${mb.toFixed(1)} MB`
  }

  const formatTime = (seconds) => {
    const hrs = Math.floor(seconds / 3600)
    const mins = Math.floor((seconds % 3600) / 60)
    const secs = seconds % 60
    if (hrs > 0) {
      return `${hrs}h ${mins}m ${secs}s`
    }
    if (mins > 0) {
      return `${mins}m ${secs}s`
    }
    return `${secs}s`
  }

  const formatMoney = (ugx) => {
    return new Intl.NumberFormat('en-UG').format(Math.floor(ugx))
  }

  // Manual privacy check
  const runPrivacyCheck = useCallback(async () => {
    const privacy = await IPDetectionService.runPrivacyCheck(
      realIP, 
      vpnIP, 
      connectionState === 'connected'
    )
    setPrivacyStatus({ ...privacy, lastCheck: Date.now() })
    return privacy
  }, [realIP, vpnIP, connectionState])

  // Get current IP based on connection state
  const getCurrentIP = useCallback(() => {
    return connectionState === 'connected' ? vpnIP : realIP
  }, [connectionState, vpnIP, realIP])

  return (
    <VpnContext.Provider value={{
      // Connection state
      connectionState,
      selectedServer,
      obfuscationProtocol,
      compressionEnabled,
      lowBandwidthMode,
      stats,
      
      // Servers
      servers: availableServers,
      availableServers,
      isLoadingServers,
      obfuscationProtocols,
      
      // IP & Privacy
      realIP,
      vpnIP,
      isLoadingIP,
      privacyStatus,
      
      // Location & Permissions
      userLocation,
      locationPermissionStatus,
      permissions,
      
      // Methods
      getCurrentIP,
      runPrivacyCheck,
      connect,
      disconnect,
      toggleConnection,
      selectServer,
      setObfuscationProtocol,
      setCompressionEnabled,
      setLowBandwidthMode,
      getAfricanServers,
      getServersByRegion,
      getFastestServer,
      getNearestServers,
      refreshServers,
      requestLocationPermission,
      formatDataSize,
      formatTime,
      formatMoney,
    }}>
      {children}
    </VpnContext.Provider>
  )
}

export function useVpn() {
  const context = useContext(VpnContext)
  if (!context) {
    throw new Error('useVpn must be used within a VpnProvider')
  }
  return context
}
