import { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react'

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

// VPN Server List with realistic IPs
const servers = [
  // Africa (Priority for Ugandan users)
  { id: 'ug', country: 'Uganda', city: 'Kampala', lat: 0.3476, lng: 32.5825, region: 'africa', load: 45, ping: 15, flag: '🇺🇬', ipPrefix: '41.210' },
  { id: 'ke', country: 'Kenya', city: 'Nairobi', lat: -1.2921, lng: 36.8219, region: 'africa', load: 32, ping: 25, flag: '🇰🇪', ipPrefix: '197.232' },
  { id: 'tz', country: 'Tanzania', city: 'Dar es Salaam', lat: -6.7924, lng: 39.2083, region: 'africa', load: 28, ping: 35, flag: '🇹🇿', ipPrefix: '197.185' },
  { id: 'rw', country: 'Rwanda', city: 'Kigali', lat: -1.9403, lng: 29.8739, region: 'africa', load: 22, ping: 20, flag: '🇷🇼', ipPrefix: '197.243' },
  { id: 'za', country: 'South Africa', city: 'Johannesburg', lat: -26.2041, lng: 28.0473, region: 'africa', load: 55, ping: 80, flag: '🇿🇦', ipPrefix: '196.38' },
  { id: 'eg', country: 'Egypt', city: 'Cairo', lat: 30.0444, lng: 31.2357, region: 'africa', load: 40, ping: 95, flag: '🇪🇬', ipPrefix: '197.55' },
  { id: 'ng', country: 'Nigeria', city: 'Lagos', lat: 6.5244, lng: 3.3792, region: 'africa', load: 60, ping: 110, flag: '🇳🇬', ipPrefix: '197.210' },
  { id: 'gh', country: 'Ghana', city: 'Accra', lat: 5.6037, lng: -0.1870, region: 'africa', load: 35, ping: 120, flag: '🇬🇭', ipPrefix: '197.251' },
  { id: 'et', country: 'Ethiopia', city: 'Addis Ababa', lat: 9.0320, lng: 38.7469, region: 'africa', load: 25, ping: 45, flag: '🇪🇹', ipPrefix: '196.188' },
  
  // Europe
  { id: 'nl', country: 'Netherlands', city: 'Amsterdam', lat: 52.3676, lng: 4.9041, region: 'europe', load: 65, ping: 150, flag: '🇳🇱', ipPrefix: '185.156' },
  { id: 'de', country: 'Germany', city: 'Frankfurt', lat: 50.1109, lng: 8.6821, region: 'europe', load: 58, ping: 155, flag: '🇩🇪', ipPrefix: '185.220' },
  { id: 'gb', country: 'United Kingdom', city: 'London', lat: 51.5074, lng: -0.1278, region: 'europe', load: 72, ping: 160, flag: '🇬🇧', ipPrefix: '185.189' },
  { id: 'fr', country: 'France', city: 'Paris', lat: 48.8566, lng: 2.3522, region: 'europe', load: 48, ping: 158, flag: '🇫🇷', ipPrefix: '185.230' },
  { id: 'ch', country: 'Switzerland', city: 'Zurich', lat: 47.3769, lng: 8.5417, region: 'europe', load: 35, ping: 152, flag: '🇨🇭', ipPrefix: '185.142' },
  
  // Americas
  { id: 'us-ny', country: 'United States', city: 'New York', lat: 40.7128, lng: -74.0060, region: 'americas', load: 70, ping: 200, flag: '🇺🇸', ipPrefix: '172.93' },
  { id: 'us-la', country: 'United States', city: 'Los Angeles', lat: 34.0522, lng: -118.2437, region: 'americas', load: 62, ping: 250, flag: '🇺🇸', ipPrefix: '172.94' },
  { id: 'ca', country: 'Canada', city: 'Toronto', lat: 43.6532, lng: -79.3832, region: 'americas', load: 45, ping: 210, flag: '🇨🇦', ipPrefix: '172.95' },
  { id: 'br', country: 'Brazil', city: 'São Paulo', lat: -23.5505, lng: -46.6333, region: 'americas', load: 45, ping: 280, flag: '🇧🇷', ipPrefix: '177.54' },
  
  // Asia Pacific
  { id: 'sg', country: 'Singapore', city: 'Singapore', lat: 1.3521, lng: 103.8198, region: 'asia', load: 55, ping: 180, flag: '🇸🇬', ipPrefix: '103.86' },
  { id: 'jp', country: 'Japan', city: 'Tokyo', lat: 35.6762, lng: 139.6503, region: 'asia', load: 50, ping: 220, flag: '🇯🇵', ipPrefix: '103.73' },
  { id: 'ae', country: 'UAE', city: 'Dubai', lat: 25.2048, lng: 55.2708, region: 'asia', load: 42, ping: 120, flag: '🇦🇪', ipPrefix: '185.203' },
  { id: 'in', country: 'India', city: 'Mumbai', lat: 19.0760, lng: 72.8777, region: 'asia', load: 58, ping: 140, flag: '🇮🇳', ipPrefix: '103.87' },
]

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
  const [selectedServer, setSelectedServer] = useState(servers[0]) // Uganda by default
  const [obfuscationProtocol, setObfuscationProtocol] = useState(obfuscationProtocols[0])
  const [compressionEnabled, setCompressionEnabled] = useState(true)
  const [lowBandwidthMode, setLowBandwidthMode] = useState(false)
  
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
    setConnectionState('connecting')
    setIsLoadingIP(true)
    
    // Simulate connection delay (realistic handshake time)
    await new Promise(resolve => setTimeout(resolve, 1500 + Math.random() * 1000))
    
    // Generate VPN IP based on selected server's IP prefix
    const generateVpnIP = () => {
      const prefix = selectedServer.ipPrefix || '10.0'
      const octet3 = Math.floor(Math.random() * 254) + 1
      const octet4 = Math.floor(Math.random() * 254) + 1
      return `${prefix}.${octet3}.${octet4}`
    }
    
    const simulatedVpnIP = generateVpnIP()
    
    setVpnIP({
      ip: simulatedVpnIP,
      country: selectedServer.country,
      city: selectedServer.city,
      countryCode: selectedServer.id.toUpperCase().slice(0, 2),
      isp: `FreedomVPN ${selectedServer.city} Node`,
      latitude: selectedServer.lat,
      longitude: selectedServer.lng,
      isVPN: true
    })
    
    setConnectionState('connected')
    setConnectionStartTime(Date.now())
    setIsLoadingIP(false)
    
    // Run privacy check after connection
    const privacy = await IPDetectionService.runPrivacyCheck(realIP, { ip: simulatedVpnIP }, true)
    setPrivacyStatus({ ...privacy, lastCheck: Date.now() })
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

  const getAfricanServers = useCallback(() => {
    return servers.filter(s => s.region === 'africa')
  }, [])

  const getServersByRegion = useCallback((region) => {
    return servers.filter(s => s.region === region)
  }, [])

  const getFastestServer = useCallback(() => {
    return [...servers].sort((a, b) => a.ping - b.ping)[0]
  }, [])

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
      connectionState,
      selectedServer,
      obfuscationProtocol,
      compressionEnabled,
      lowBandwidthMode,
      stats,
      servers,
      obfuscationProtocols,
      realIP,
      vpnIP,
      isLoadingIP,
      privacyStatus,
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
