import { createContext, useContext, useState, useEffect, useCallback } from 'react'
import { getPublicIP } from '../services/ipService'

// Server data matching Android AfricanServerPriority.kt
const servers = [
  // Africa (Priority)
  { id: 'ug', country: 'Uganda', city: 'Kampala', lat: 0.3476, lng: 32.5825, region: 'africa', load: 45, ping: 15, flag: '🇺🇬' },
  { id: 'ke', country: 'Kenya', city: 'Nairobi', lat: -1.2921, lng: 36.8219, region: 'africa', load: 32, ping: 25, flag: '🇰🇪' },
  { id: 'tz', country: 'Tanzania', city: 'Dar es Salaam', lat: -6.7924, lng: 39.2083, region: 'africa', load: 28, ping: 35, flag: '🇹🇿' },
  { id: 'rw', country: 'Rwanda', city: 'Kigali', lat: -1.9403, lng: 29.8739, region: 'africa', load: 22, ping: 20, flag: '🇷🇼' },
  { id: 'za', country: 'South Africa', city: 'Johannesburg', lat: -26.2041, lng: 28.0473, region: 'africa', load: 55, ping: 80, flag: '🇿🇦' },
  { id: 'eg', country: 'Egypt', city: 'Cairo', lat: 30.0444, lng: 31.2357, region: 'africa', load: 40, ping: 95, flag: '🇪🇬' },
  { id: 'ng', country: 'Nigeria', city: 'Lagos', lat: 6.5244, lng: 3.3792, region: 'africa', load: 60, ping: 110, flag: '🇳🇬' },
  { id: 'gh', country: 'Ghana', city: 'Accra', lat: 5.6037, lng: -0.1870, region: 'africa', load: 35, ping: 120, flag: '🇬🇭' },
  { id: 'et', country: 'Ethiopia', city: 'Addis Ababa', lat: 9.0320, lng: 38.7469, region: 'africa', load: 25, ping: 45, flag: '🇪🇹' },
  
  // Europe
  { id: 'nl', country: 'Netherlands', city: 'Amsterdam', lat: 52.3676, lng: 4.9041, region: 'europe', load: 65, ping: 150, flag: '🇳🇱' },
  { id: 'de', country: 'Germany', city: 'Frankfurt', lat: 50.1109, lng: 8.6821, region: 'europe', load: 58, ping: 155, flag: '🇩🇪' },
  { id: 'gb', country: 'United Kingdom', city: 'London', lat: 51.5074, lng: -0.1278, region: 'europe', load: 72, ping: 160, flag: '🇬🇧' },
  { id: 'fr', country: 'France', city: 'Paris', lat: 48.8566, lng: 2.3522, region: 'europe', load: 48, ping: 158, flag: '🇫🇷' },
  
  // Americas
  { id: 'us-ny', country: 'United States', city: 'New York', lat: 40.7128, lng: -74.0060, region: 'americas', load: 70, ping: 200, flag: '🇺🇸' },
  { id: 'us-la', country: 'United States', city: 'Los Angeles', lat: 34.0522, lng: -118.2437, region: 'americas', load: 62, ping: 250, flag: '🇺🇸' },
  { id: 'br', country: 'Brazil', city: 'São Paulo', lat: -23.5505, lng: -46.6333, region: 'americas', load: 45, ping: 280, flag: '🇧🇷' },
  
  // Asia
  { id: 'sg', country: 'Singapore', city: 'Singapore', lat: 1.3521, lng: 103.8198, region: 'asia', load: 55, ping: 180, flag: '🇸🇬' },
  { id: 'jp', country: 'Japan', city: 'Tokyo', lat: 35.6762, lng: 139.6503, region: 'asia', load: 50, ping: 220, flag: '🇯🇵' },
  { id: 'ae', country: 'UAE', city: 'Dubai', lat: 25.2048, lng: 55.2708, region: 'asia', load: 42, ping: 120, flag: '🇦🇪' },
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
  
  // IP Address tracking
  const [realIP, setRealIP] = useState(null)
  const [vpnIP, setVpnIP] = useState(null)
  const [isLoadingIP, setIsLoadingIP] = useState(false)
  
  // Fetch real IP on mount
  useEffect(() => {
    const fetchRealIP = async () => {
      setIsLoadingIP(true)
      const ipInfo = await getPublicIP()
      setRealIP(ipInfo)
      setIsLoadingIP(false)
    }
    fetchRealIP()
  }, [])

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
    
    // Simulate connection delay
    await new Promise(resolve => setTimeout(resolve, 2000 + Math.random() * 1000))
    
    // Generate a simulated VPN IP based on the selected server
    // In a real VPN, this would be the actual exit node IP
    const serverIPRanges = {
      'ug': { base: '41.210', range: [1, 254] },
      'ke': { base: '197.232', range: [1, 254] },
      'tz': { base: '197.185', range: [1, 254] },
      'rw': { base: '197.243', range: [1, 254] },
      'za': { base: '196.38', range: [1, 254] },
      'eg': { base: '197.55', range: [1, 254] },
      'ng': { base: '197.210', range: [1, 254] },
      'gh': { base: '197.251', range: [1, 254] },
      'et': { base: '196.188', range: [1, 254] },
      'nl': { base: '185.156', range: [1, 254] },
      'de': { base: '185.220', range: [1, 254] },
      'gb': { base: '185.189', range: [1, 254] },
      'fr': { base: '185.230', range: [1, 254] },
      'us-ny': { base: '172.93', range: [1, 254] },
      'us-la': { base: '172.94', range: [1, 254] },
      'br': { base: '177.54', range: [1, 254] },
      'sg': { base: '103.86', range: [1, 254] },
      'jp': { base: '103.73', range: [1, 254] },
      'ae': { base: '185.203', range: [1, 254] },
    }
    
    const serverRange = serverIPRanges[selectedServer.id] || { base: '10.0', range: [1, 254] }
    const randomOctet1 = Math.floor(Math.random() * 254) + 1
    const randomOctet2 = Math.floor(Math.random() * 254) + 1
    const simulatedVpnIP = `${serverRange.base}.${randomOctet1}.${randomOctet2}`
    
    setVpnIP({
      ip: simulatedVpnIP,
      country: selectedServer.country,
      city: selectedServer.city,
      countryCode: selectedServer.id.toUpperCase().slice(0, 2),
      isp: `FreedomVPN ${selectedServer.city} Node`,
    })
    
    setConnectionState('connected')
    setConnectionStartTime(Date.now())
    setIsLoadingIP(false)
  }, [selectedServer])

  const disconnect = useCallback(() => {
    setConnectionState('disconnected')
    setConnectionStartTime(null)
    setVpnIP(null)
  }, [])

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
      getCurrentIP,
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
