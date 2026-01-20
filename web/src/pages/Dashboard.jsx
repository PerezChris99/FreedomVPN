import { motion } from 'framer-motion'
import { useVpn } from '../context/VpnContext'
import { useLanguage } from '../context/LanguageContext'
import { useStealth } from '../context/StealthContext'
import { 
  Shield, ShieldCheck, ShieldX, Wifi, Globe, Zap, TrendingDown, 
  Clock, DollarSign, AlertTriangle, MapPin, Server, Loader2, 
  Copy, Check, Layers, ChevronRight, Download, Lock, Eye, Activity
} from 'lucide-react'
import { useState, useEffect } from 'react'
import multiHopService, { MultiHopPresets } from '../services/multiHopService'

export default function Dashboard() {
  const { 
    connectionState, 
    selectedServer, 
    toggleConnection, 
    stats, 
    formatDataSize, 
    formatTime, 
    formatMoney,
    compressionEnabled,
    obfuscationProtocol,
    realIP,
    vpnIP,
    isLoadingIP,
    getCurrentIP
  } = useVpn()
  const { t } = useLanguage()
  const { stealthMode, activatePanic } = useStealth()
  const [copiedIP, setCopiedIP] = useState(false)
  const [showPlatformWarning, setShowPlatformWarning] = useState(true)
  
  // Multi-Hop State
  const [multiHopState, setMultiHopState] = useState(multiHopService.getState())
  const [showPresets, setShowPresets] = useState(false)
  
  useEffect(() => {
    const unsubscribe = multiHopService.subscribe(setMultiHopState)
    return unsubscribe
  }, [])

  const isConnected = connectionState === 'connected'
  const isConnecting = connectionState === 'connecting'
  const currentIP = getCurrentIP()
  
  const copyIP = () => {
    if (currentIP?.ip) {
      navigator.clipboard.writeText(currentIP.ip)
      setCopiedIP(true)
      setTimeout(() => setCopiedIP(false), 2000)
    }
  }

  return (
    <div className="min-h-screen p-4 sm:p-6 lg:p-8">
      {/* Platform Warning - Collapsible */}
      {showPlatformWarning && (
        <motion.div 
          className="bg-amber-500/10 border border-amber-500/30 rounded-xl p-4 mb-6"
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
        >
          <div className="flex items-start gap-3">
            <AlertTriangle className="w-5 h-5 text-amber-400 flex-shrink-0 mt-0.5" />
            <div className="flex-1">
              <p className="text-amber-400 font-medium text-sm">Web Demo Mode</p>
              <p className="text-slate-400 text-xs mt-1">
                This is a UI demo. Download desktop or mobile app for real VPN protection.
              </p>
            </div>
            <button
              onClick={() => setShowPlatformWarning(false)}
              className="text-slate-500 hover:text-white text-xs"
            >
              ✕
            </button>
          </div>
        </motion.div>
      )}

      {/* Page Header */}
      <div className="mb-6 lg:mb-8">
        <div className="lg:hidden text-center">
          <h1 className="text-xl font-bold text-white">{t('appName')}</h1>
          <p className="text-slate-400 text-xs">{t('tagline')}</p>
        </div>
        <div className="hidden lg:block">
          <h1 className="text-2xl font-bold text-white">Dashboard</h1>
          <p className="text-slate-400 text-sm">Monitor your connection and privacy status</p>
        </div>
      </div>

      {/* Main Grid - 3 Column Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-4 lg:gap-6">
        
        {/* LEFT COLUMN - Connection Control */}
        <div className="lg:col-span-4 space-y-4">
          
          {/* Connection Card */}
          <motion.div 
            className="bg-slate-800/50 backdrop-blur-sm rounded-2xl p-6 border border-slate-700/50"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
          >
            {/* Status Badge */}
            <div className="flex justify-center mb-6">
              <div className={`inline-flex items-center gap-2 px-4 py-2 rounded-full text-sm font-medium ${
                isConnected 
                  ? 'bg-green-500/20 text-green-400 border border-green-500/30' 
                  : isConnecting
                  ? 'bg-yellow-500/20 text-yellow-400 border border-yellow-500/30'
                  : 'bg-red-500/20 text-red-400 border border-red-500/30'
              }`}>
                <span className={`w-2 h-2 rounded-full ${
                  isConnected ? 'bg-green-400 animate-pulse' : 
                  isConnecting ? 'bg-yellow-400 animate-pulse' : 'bg-red-400'
                }`} />
                {isConnecting ? t('connecting') : isConnected ? t('connected') : t('disconnected')}
              </div>
            </div>

            {/* Connect Button */}
            <div className="flex justify-center mb-6">
              <motion.button
                onClick={toggleConnection}
                disabled={isConnecting}
                whileHover={{ scale: 1.03 }}
                whileTap={{ scale: 0.97 }}
                className={`w-28 h-28 sm:w-32 sm:h-32 rounded-full flex items-center justify-center transition-all shadow-xl ${
                  isConnected 
                    ? 'bg-gradient-to-br from-green-500 to-emerald-600 shadow-green-500/30' 
                    : isConnecting
                    ? 'bg-gradient-to-br from-yellow-500 to-orange-500 shadow-yellow-500/30'
                    : 'bg-gradient-to-br from-slate-600 to-slate-700 hover:from-green-500 hover:to-emerald-600'
                }`}
              >
                {isConnecting ? (
                  <Loader2 className="w-12 h-12 text-white animate-spin" />
                ) : isConnected ? (
                  <ShieldCheck className="w-14 h-14 text-white" />
                ) : (
                  <ShieldX className="w-14 h-14 text-white" />
                )}
              </motion.button>
            </div>

            <p className="text-center text-slate-400 text-sm">
              {isConnected ? 'Tap to disconnect' : 'Tap to connect'}
            </p>
          </motion.div>

          {/* Selected Server Card */}
          <motion.div 
            className="bg-slate-800/50 backdrop-blur-sm rounded-2xl p-4 border border-slate-700/50"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.05 }}
          >
            <div className="flex items-center gap-4">
              <span className="text-3xl">{selectedServer.flag}</span>
              <div className="flex-1 min-w-0">
                <p className="text-white font-medium truncate">{selectedServer.country}</p>
                <p className="text-slate-400 text-sm">{selectedServer.city}</p>
              </div>
              <div className="text-right">
                <p className="text-green-400 font-medium">{selectedServer.ping}ms</p>
                <p className="text-slate-500 text-xs">{selectedServer.load}% load</p>
              </div>
            </div>
          </motion.div>

          {/* Security Features Card */}
          <motion.div 
            className="bg-slate-800/50 backdrop-blur-sm rounded-2xl p-4 border border-slate-700/50"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.1 }}
          >
            <h3 className="text-white font-medium text-sm mb-4">Security Features</h3>
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Lock className="w-4 h-4 text-blue-400" />
                  <span className="text-slate-300 text-sm">Encryption</span>
                </div>
                <span className="text-green-400 text-xs font-medium">AES-256</span>
              </div>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Globe className="w-4 h-4 text-purple-400" />
                  <span className="text-slate-300 text-sm">{t('obfuscation')}</span>
                </div>
                <span className="text-green-400 text-xs">{obfuscationProtocol.name}</span>
              </div>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Zap className="w-4 h-4 text-yellow-400" />
                  <span className="text-slate-300 text-sm">{t('compression')}</span>
                </div>
                <span className={`text-xs ${compressionEnabled ? 'text-green-400' : 'text-slate-500'}`}>
                  {compressionEnabled ? 'ON' : 'OFF'}
                </span>
              </div>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Shield className="w-4 h-4 text-cyan-400" />
                  <span className="text-slate-300 text-sm">{t('stealthMode')}</span>
                </div>
                <span className={`text-xs ${stealthMode ? 'text-green-400' : 'text-slate-500'}`}>
                  {stealthMode ? 'ON' : 'OFF'}
                </span>
              </div>
            </div>
          </motion.div>
        </div>

        {/* MIDDLE COLUMN - IP & Stats */}
        <div className="lg:col-span-4 space-y-4">
          
          {/* IP Address Card */}
          <motion.div 
            className={`bg-slate-800/50 backdrop-blur-sm rounded-2xl p-4 border ${
              isConnected ? 'border-green-500/30' : 'border-red-500/30'
            }`}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.15 }}
          >
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2">
                <Globe className={`w-5 h-5 ${isConnected ? 'text-green-400' : 'text-red-400'}`} />
                <span className="text-white font-medium text-sm">
                  {isConnected ? 'VPN IP Address' : 'Your Real IP'}
                </span>
              </div>
              {isLoadingIP && <Loader2 className="w-4 h-4 text-slate-400 animate-spin" />}
            </div>

            {currentIP ? (
              <div className="space-y-3">
                {/* IP Display */}
                <div className="flex items-center justify-between bg-slate-900/50 rounded-xl p-3">
                  <div className="flex items-center gap-3">
                    <div className={`w-10 h-10 rounded-lg flex items-center justify-center ${
                      isConnected ? 'bg-green-500/20' : 'bg-red-500/20'
                    }`}>
                      <Server className={`w-5 h-5 ${isConnected ? 'text-green-400' : 'text-red-400'}`} />
                    </div>
                    <div>
                      <p className={`font-mono text-lg font-bold ${isConnected ? 'text-green-400' : 'text-white'}`}>
                        {currentIP.ip}
                      </p>
                      <p className="text-slate-500 text-xs">
                        {isConnected ? '🛡️ Protected' : '⚠️ Exposed'}
                      </p>
                    </div>
                  </div>
                  <button
                    onClick={copyIP}
                    className="p-2 hover:bg-slate-700/50 rounded-lg transition-colors"
                  >
                    {copiedIP ? <Check className="w-4 h-4 text-green-400" /> : <Copy className="w-4 h-4 text-slate-400" />}
                  </button>
                </div>

                {/* Location Grid */}
                <div className="grid grid-cols-2 gap-2">
                  <div className="bg-slate-900/30 rounded-xl p-3">
                    <div className="flex items-center gap-1.5 text-slate-500 mb-1">
                      <MapPin className="w-3.5 h-3.5" />
                      <span className="text-xs">Location</span>
                    </div>
                    <p className="text-white text-sm font-medium truncate">{currentIP.city}, {currentIP.country}</p>
                  </div>
                  <div className="bg-slate-900/30 rounded-xl p-3">
                    <div className="flex items-center gap-1.5 text-slate-500 mb-1">
                      <Wifi className="w-3.5 h-3.5" />
                      <span className="text-xs">ISP</span>
                    </div>
                    <p className="text-white text-sm font-medium truncate">{currentIP.isp}</p>
                  </div>
                </div>

                {/* Protected Notice */}
                {isConnected && realIP && (
                  <div className="bg-green-500/10 border border-green-500/20 rounded-xl p-3">
                    <div className="flex items-center gap-2">
                      <Eye className="w-4 h-4 text-green-400" />
                      <p className="text-green-400 text-xs">
                        Real IP <span className="font-mono line-through text-red-400/70">{realIP.ip}</span> is hidden
                      </p>
                    </div>
                  </div>
                )}
              </div>
            ) : (
              <div className="flex items-center justify-center py-8">
                <Loader2 className="w-6 h-6 text-slate-400 animate-spin" />
                <span className="ml-2 text-slate-400 text-sm">Detecting IP...</span>
              </div>
            )}
          </motion.div>

          {/* Stats Grid - Only when connected */}
          {isConnected && (
            <motion.div 
              className="grid grid-cols-2 gap-3"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.2 }}
            >
              <div className="bg-slate-800/50 backdrop-blur-sm rounded-xl p-3 border border-slate-700/50">
                <div className="flex items-center gap-2 mb-2">
                  <Activity className="w-4 h-4 text-blue-400" />
                  <span className="text-slate-400 text-xs">Data Used</span>
                </div>
                <p className="text-white font-bold">{formatDataSize(stats.dataUsed)}</p>
              </div>
              <div className="bg-slate-800/50 backdrop-blur-sm rounded-xl p-3 border border-slate-700/50">
                <div className="flex items-center gap-2 mb-2">
                  <TrendingDown className="w-4 h-4 text-green-400" />
                  <span className="text-slate-400 text-xs">{t('dataSaved')}</span>
                </div>
                <p className="text-green-400 font-bold">{formatDataSize(stats.dataSaved)}</p>
              </div>
              <div className="bg-slate-800/50 backdrop-blur-sm rounded-xl p-3 border border-slate-700/50">
                <div className="flex items-center gap-2 mb-2">
                  <Clock className="w-4 h-4 text-purple-400" />
                  <span className="text-slate-400 text-xs">{t('timeSaved')}</span>
                </div>
                <p className="text-white font-bold">{formatTime(stats.connectionTime)}</p>
              </div>
              <div className="bg-slate-800/50 backdrop-blur-sm rounded-xl p-3 border border-slate-700/50">
                <div className="flex items-center gap-2 mb-2">
                  <DollarSign className="w-4 h-4 text-yellow-400" />
                  <span className="text-slate-400 text-xs">{t('moneySaved')}</span>
                </div>
                <p className="text-yellow-400 font-bold text-sm">{formatMoney(stats.moneySaved)} UGX</p>
              </div>
            </motion.div>
          )}
        </div>

        {/* RIGHT COLUMN - Multi-Hop & Actions */}
        <div className="lg:col-span-4 space-y-4">
          
          {/* Multi-Hop Card */}
          <motion.div 
            className={`rounded-2xl p-4 border transition-all ${
              multiHopState.isEnabled 
                ? 'bg-purple-500/10 border-purple-500/30' 
                : 'bg-slate-800/50 border-slate-700/50'
            }`}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.25 }}
          >
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-3">
                <div className={`w-10 h-10 rounded-xl flex items-center justify-center ${
                  multiHopState.isEnabled ? 'bg-purple-500' : 'bg-slate-700'
                }`}>
                  <Layers className="w-5 h-5 text-white" />
                </div>
                <div>
                  <p className="text-white font-medium">Multi-Hop</p>
                  <p className="text-slate-400 text-xs">Route through multiple servers</p>
                </div>
              </div>
              <button
                onClick={() => multiHopService.toggle()}
                className={`relative w-12 h-7 rounded-full transition-all ${
                  multiHopState.isEnabled ? 'bg-purple-500' : 'bg-slate-600'
                }`}
              >
                <span className={`absolute top-1 w-5 h-5 bg-white rounded-full shadow transition-transform ${
                  multiHopState.isEnabled ? 'translate-x-6' : 'translate-x-1'
                }`} />
              </button>
            </div>

            {/* Chain Display */}
            {multiHopState.isEnabled && multiHopState.chain.length > 0 && (
              <div className="bg-black/20 rounded-xl p-3 mb-3">
                <div className="flex items-center justify-between text-xs mb-2">
                  <span className="text-slate-400">Route</span>
                  <span className="text-purple-400">{Math.round(multiHopState.speedRetention * 100)}% speed</span>
                </div>
                <div className="flex items-center gap-1 overflow-x-auto pb-1">
                  <span className="text-green-400 text-xs flex-shrink-0">You</span>
                  {multiHopState.chain.map((hop) => (
                    <div key={hop.id} className="flex items-center gap-1 flex-shrink-0">
                      <ChevronRight className="w-3 h-3 text-slate-500" />
                      <span className="text-sm bg-slate-700/50 px-2 py-0.5 rounded">{hop.flag}</span>
                    </div>
                  ))}
                  <ChevronRight className="w-3 h-3 text-slate-500 flex-shrink-0" />
                  <Globe className="w-3 h-3 text-blue-400 flex-shrink-0" />
                </div>
              </div>
            )}

            {/* Preset Selector */}
            <button
              onClick={() => setShowPresets(!showPresets)}
              className="w-full flex items-center justify-between p-2.5 bg-slate-800/50 rounded-lg hover:bg-slate-700/50 transition-colors"
            >
              <span className="text-slate-400 text-xs">
                Preset: <span className="text-white font-medium">{multiHopState.preset.label}</span>
              </span>
              <ChevronRight className={`w-4 h-4 text-slate-400 transition-transform ${showPresets ? 'rotate-90' : ''}`} />
            </button>

            {showPresets && (
              <div className="mt-2 space-y-1 max-h-48 overflow-y-auto">
                {Object.entries(MultiHopPresets).map(([key, preset]) => (
                  <button
                    key={key}
                    onClick={() => {
                      multiHopService.setPreset(key)
                      setShowPresets(false)
                    }}
                    className={`w-full p-2.5 rounded-lg text-left transition-colors ${
                      multiHopState.preset.label === preset.label
                        ? 'bg-purple-500/20 border border-purple-500/40'
                        : 'bg-slate-800/30 hover:bg-slate-700/50'
                    }`}
                  >
                    <span className="text-white text-sm font-medium">{preset.label}</span>
                  </button>
                ))}
              </div>
            )}
          </motion.div>

          {/* Panic Button */}
          {stealthMode && (
            <motion.button
              onClick={activatePanic}
              className="w-full bg-red-500/10 border border-red-500/30 rounded-2xl p-4 flex items-center justify-center gap-3 text-red-400 hover:bg-red-500/20 transition-colors"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.3 }}
            >
              <AlertTriangle className="w-5 h-5" />
              <span className="font-medium">{t('panicButton')}</span>
            </motion.button>
          )}

          {/* Download Apps CTA */}
          <motion.div
            className="bg-gradient-to-br from-green-500/10 to-emerald-500/5 border border-green-500/20 rounded-2xl p-4"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.35 }}
          >
            <p className="text-white font-medium text-sm mb-2">Get Full Protection</p>
            <p className="text-slate-400 text-xs mb-4">
              Download native apps for system-wide VPN with no leaks.
            </p>
            <div className="flex gap-2">
              <a 
                href="#" 
                className="flex-1 inline-flex items-center justify-center gap-2 bg-green-500 text-white text-xs font-bold px-3 py-2.5 rounded-lg hover:bg-green-600 transition-colors"
              >
                <Download className="w-4 h-4" />
                Windows
              </a>
              <a 
                href="#" 
                className="flex-1 inline-flex items-center justify-center gap-2 bg-blue-500 text-white text-xs font-bold px-3 py-2.5 rounded-lg hover:bg-blue-600 transition-colors"
              >
                <Download className="w-4 h-4" />
                Android
              </a>
            </div>
          </motion.div>
        </div>
      </div>
    </div>
  )
}
