import { motion } from 'framer-motion'
import { useVpn } from '../context/VpnContext'
import { useLanguage } from '../context/LanguageContext'
import { useStealth } from '../context/StealthContext'
import { Shield, ShieldCheck, ShieldX, Wifi, Globe, Zap, TrendingDown, Clock, DollarSign, AlertTriangle, MapPin, Server, Loader2, Copy, Check, Layers, ChevronRight } from 'lucide-react'
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
  
  // Multi-Hop State
  const [multiHopState, setMultiHopState] = useState(multiHopService.getState())
  const [showPresets, setShowPresets] = useState(false)
  
  // Subscribe to multi-hop changes
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
    <div className="min-h-screen p-4 pt-8">
      {/* Header */}
      <div className="text-center mb-8">
        <h1 className="text-2xl font-bold text-white mb-1">{t('appName')}</h1>
        <p className="text-slate-400 text-sm">{t('tagline')}</p>
      </div>

      {/* Connection Status Card */}
      <motion.div 
        className="glass rounded-3xl p-6 mb-6"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
      >
        {/* Status indicator */}
        <div className="flex items-center justify-center gap-2 mb-6">
          <div className={`w-3 h-3 rounded-full ${
            isConnected ? 'bg-green-500 animate-pulse' : 
            isConnecting ? 'bg-yellow-500 animate-pulse' : 
            'bg-red-500'
          }`} />
          <span className={`font-medium ${
            isConnected ? 'text-green-400' : 
            isConnecting ? 'text-yellow-400' : 
            'text-red-400'
          }`}>
            {isConnecting ? t('connecting') : isConnected ? t('connected') : t('disconnected')}
          </span>
        </div>

        {/* Main connect button */}
        <div className="flex justify-center mb-6">
          <motion.button
            onClick={toggleConnection}
            disabled={isConnecting}
            whileHover={{ scale: 1.05 }}
            whileTap={{ scale: 0.95 }}
            className={`relative w-40 h-40 rounded-full flex items-center justify-center transition-all connect-button ${
              isConnected 
                ? 'bg-gradient-to-br from-green-500 to-emerald-600 glow-effect' 
                : isConnecting
                ? 'bg-gradient-to-br from-yellow-500 to-orange-500'
                : 'bg-gradient-to-br from-slate-600 to-slate-700 hover:from-green-600 hover:to-emerald-700'
            }`}
          >
            {/* Ripple effects when connected */}
            {isConnected && (
              <>
                <span className="absolute inset-0 rounded-full bg-green-500 opacity-20 ripple-effect" />
                <span className="absolute inset-0 rounded-full bg-green-500 opacity-20 ripple-effect" style={{ animationDelay: '0.5s' }} />
              </>
            )}
            
            {isConnecting ? (
              <div className="animate-spin">
                <Shield className="w-16 h-16 text-white" />
              </div>
            ) : isConnected ? (
              <ShieldCheck className="w-16 h-16 text-white" />
            ) : (
              <ShieldX className="w-16 h-16 text-white" />
            )}
          </motion.button>
        </div>

        {/* Status message */}
        <p className={`text-center text-sm ${isConnected ? 'text-green-400' : 'text-slate-400'}`}>
          {isConnected ? t('protected') : t('unprotected')}
        </p>
      </motion.div>

      {/* IP Address Card */}
      <motion.div 
        className={`glass rounded-2xl p-4 mb-4 ${isConnected ? 'ring-2 ring-green-500/50' : ''}`}
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.05 }}
      >
        <div className="flex items-center gap-2 mb-3">
          <Globe className={`w-5 h-5 ${isConnected ? 'text-green-400' : 'text-slate-400'}`} />
          <span className="text-slate-300 font-medium">
            {isConnected ? 'VPN IP Address' : 'Your Real IP Address'}
          </span>
          {isLoadingIP && <Loader2 className="w-4 h-4 text-slate-400 animate-spin" />}
        </div>
        
        {currentIP ? (
          <div className="space-y-2">
            {/* IP Address */}
            <div className="flex items-center justify-between bg-slate-800/50 rounded-xl p-3">
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
                  <p className="text-slate-400 text-xs">
                    {isConnected ? '🔒 Protected' : '⚠️ Exposed'}
                  </p>
                </div>
              </div>
              <button
                onClick={copyIP}
                className="p-2 hover:bg-slate-700 rounded-lg transition-colors"
                title="Copy IP"
              >
                {copiedIP ? (
                  <Check className="w-5 h-5 text-green-400" />
                ) : (
                  <Copy className="w-5 h-5 text-slate-400" />
                )}
              </button>
            </div>
            
            {/* Location Info */}
            <div className="grid grid-cols-2 gap-2">
              <div className="bg-slate-800/50 rounded-xl p-3">
                <div className="flex items-center gap-2 mb-1">
                  <MapPin className="w-4 h-4 text-slate-400" />
                  <span className="text-slate-400 text-xs">Location</span>
                </div>
                <p className="text-white text-sm font-medium">
                  {currentIP.city}, {currentIP.country}
                </p>
              </div>
              <div className="bg-slate-800/50 rounded-xl p-3">
                <div className="flex items-center gap-2 mb-1">
                  <Wifi className="w-4 h-4 text-slate-400" />
                  <span className="text-slate-400 text-xs">ISP</span>
                </div>
                <p className="text-white text-sm font-medium truncate">
                  {currentIP.isp}
                </p>
              </div>
            </div>

            {/* Before/After comparison when connected */}
            {isConnected && realIP && (
              <div className="mt-2 p-3 bg-green-500/10 border border-green-500/20 rounded-xl">
                <p className="text-green-400 text-sm text-center">
                  ✓ Your real IP <span className="font-mono text-red-400 line-through">{realIP.ip}</span> is now hidden
                </p>
              </div>
            )}
          </div>
        ) : (
          <div className="flex items-center justify-center py-4">
            <Loader2 className="w-6 h-6 text-slate-400 animate-spin" />
            <span className="ml-2 text-slate-400">Detecting IP...</span>
          </div>
        )}
      </motion.div>

      {/* Current Server */}
      <motion.div 
        className="glass rounded-2xl p-4 mb-4 flex items-center gap-4"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1 }}
      >
        <div className="text-3xl">{selectedServer.flag}</div>
        <div className="flex-1">
          <p className="text-white font-medium">{selectedServer.country}</p>
          <p className="text-slate-400 text-sm">{selectedServer.city}</p>
        </div>
        <div className="text-right">
          <p className="text-green-400 font-medium">{selectedServer.ping}ms</p>
          <p className="text-slate-400 text-sm">{selectedServer.load}% load</p>
        </div>
      </motion.div>

      {/* Multi-Hop Toggle - Server Bouncing for Maximum Anonymity */}
      <motion.div 
        className={`rounded-2xl p-4 mb-4 border-2 transition-all ${
          multiHopState.isEnabled 
            ? 'bg-gradient-to-r from-purple-500/20 to-indigo-500/20 border-purple-500/50' 
            : 'glass border-transparent'
        }`}
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.15 }}
      >
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-3">
            <div className={`w-10 h-10 rounded-xl flex items-center justify-center ${
              multiHopState.isEnabled ? 'bg-purple-500' : 'bg-slate-700'
            }`}>
              <Layers className="w-5 h-5 text-white" />
            </div>
            <div>
              <p className="text-white font-medium">Multi-Hop</p>
              <p className="text-slate-400 text-xs">Server bouncing for max anonymity</p>
            </div>
          </div>
          <button
            onClick={() => multiHopService.toggle()}
            className={`relative w-14 h-8 rounded-full transition-all duration-200 ${
              multiHopState.isEnabled 
                ? 'bg-purple-500' 
                : 'bg-slate-600'
            }`}
          >
            <span className={`absolute top-1 w-6 h-6 rounded-full bg-white shadow transition-all duration-200 ${
              multiHopState.isEnabled ? 'left-7' : 'left-1'
            }`} />
          </button>
        </div>
        
        {/* Multi-Hop Chain Display */}
        {multiHopState.isEnabled && multiHopState.chain.length > 0 && (
          <div className="mt-3 p-3 bg-black/30 rounded-xl">
            <div className="flex items-center justify-between mb-2">
              <span className="text-slate-400 text-xs">Traffic Route</span>
              <span className="text-purple-400 text-xs font-medium">
                {Math.round(multiHopState.speedRetention * 100)}% speed
              </span>
            </div>
            <div className="flex items-center gap-2 overflow-x-auto pb-1">
              <div className="flex items-center gap-1 text-green-400 text-xs">
                <span>You</span>
              </div>
              {multiHopState.chain.map((hop, index) => (
                <div key={hop.id} className="flex items-center gap-1">
                  <ChevronRight className="w-4 h-4 text-slate-500" />
                  <div className={`flex items-center gap-1 px-2 py-1 rounded-lg ${
                    hop.isExit ? 'bg-purple-500/30 text-purple-300' : 'bg-slate-700/50 text-slate-300'
                  }`}>
                    <span className="text-sm">{hop.flag}</span>
                    <span className="text-xs font-medium">{hop.city}</span>
                  </div>
                </div>
              ))}
              <ChevronRight className="w-4 h-4 text-slate-500" />
              <div className="flex items-center gap-1 text-blue-400 text-xs">
                <Globe className="w-3 h-3" />
                <span>Web</span>
              </div>
            </div>
            <p className="mt-2 text-slate-500 text-xs text-center">
              Your exit IP: {multiHopState.exitServer?.city}, {multiHopState.exitServer?.country}
            </p>
          </div>
        )}
        
        {/* Preset Selector */}
        <button
          onClick={() => setShowPresets(!showPresets)}
          className="mt-3 w-full flex items-center justify-between p-2 bg-slate-800/50 rounded-lg hover:bg-slate-700/50 transition-colors"
        >
          <span className="text-slate-400 text-sm">
            Preset: <span className="text-white font-medium">{multiHopState.preset.label}</span>
          </span>
          <ChevronRight className={`w-4 h-4 text-slate-400 transition-transform ${showPresets ? 'rotate-90' : ''}`} />
        </button>
        
        {showPresets && (
          <div className="mt-2 space-y-1">
            {Object.entries(MultiHopPresets).map(([key, preset]) => (
              <button
                key={key}
                onClick={() => {
                  multiHopService.setPreset(key)
                  setShowPresets(false)
                }}
                className={`w-full p-2 rounded-lg text-left transition-colors ${
                  multiHopState.preset.label === preset.label
                    ? 'bg-purple-500/30 border border-purple-500/50'
                    : 'bg-slate-800/30 hover:bg-slate-700/50'
                }`}
              >
                <span className="text-white text-sm font-medium">{preset.label}</span>
                <span className="text-slate-400 text-xs ml-2">{preset.description}</span>
              </button>
            ))}
          </div>
        )}
      </motion.div>

      {/* Statistics Grid */}
      {isConnected && (
        <motion.div 
          className="grid grid-cols-2 gap-4 mb-4"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
        >
          {/* Data Used */}
          <div className="glass rounded-2xl p-4">
            <div className="flex items-center gap-2 mb-2">
              <Wifi className="w-4 h-4 text-blue-400" />
              <span className="text-slate-400 text-sm">Data Used</span>
            </div>
            <p className="text-white text-xl font-bold">{formatDataSize(stats.dataUsed)}</p>
          </div>

          {/* Data Saved */}
          <div className="glass rounded-2xl p-4">
            <div className="flex items-center gap-2 mb-2">
              <TrendingDown className="w-4 h-4 text-green-400" />
              <span className="text-slate-400 text-sm">{t('dataSaved')}</span>
            </div>
            <p className="text-green-400 text-xl font-bold">{formatDataSize(stats.dataSaved)}</p>
          </div>

          {/* Time Connected */}
          <div className="glass rounded-2xl p-4">
            <div className="flex items-center gap-2 mb-2">
              <Clock className="w-4 h-4 text-purple-400" />
              <span className="text-slate-400 text-sm">{t('timeSaved')}</span>
            </div>
            <p className="text-white text-xl font-bold">{formatTime(stats.connectionTime)}</p>
          </div>

          {/* Money Saved */}
          <div className="glass rounded-2xl p-4">
            <div className="flex items-center gap-2 mb-2">
              <DollarSign className="w-4 h-4 text-yellow-400" />
              <span className="text-slate-400 text-sm">{t('moneySaved')}</span>
            </div>
            <p className="text-yellow-400 text-xl font-bold">{formatMoney(stats.moneySaved)} UGX</p>
          </div>
        </motion.div>
      )}

      {/* Features Status */}
      <motion.div 
        className="glass rounded-2xl p-4 space-y-3"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.3 }}
      >
        {/* Obfuscation */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Globe className="w-5 h-5 text-purple-400" />
            <span className="text-slate-300">{t('obfuscation')}</span>
          </div>
          <span className="text-green-400 text-sm">{obfuscationProtocol.name}</span>
        </div>

        {/* Compression */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Zap className="w-5 h-5 text-yellow-400" />
            <span className="text-slate-300">{t('compression')}</span>
          </div>
          <span className={`text-sm ${compressionEnabled ? 'text-green-400' : 'text-slate-500'}`}>
            {compressionEnabled ? 'ON (45%)' : 'OFF'}
          </span>
        </div>

        {/* Stealth Mode */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Shield className="w-5 h-5 text-blue-400" />
            <span className="text-slate-300">{t('stealthMode')}</span>
          </div>
          <span className={`text-sm ${stealthMode ? 'text-green-400' : 'text-slate-500'}`}>
            {stealthMode ? 'ON' : 'OFF'}
          </span>
        </div>
      </motion.div>

      {/* Panic Button (if stealth mode enabled) */}
      {stealthMode && (
        <motion.button
          onClick={activatePanic}
          className="mt-4 w-full glass rounded-2xl p-4 flex items-center justify-center gap-3 text-red-400 hover:bg-red-500/20 transition-colors"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.4 }}
        >
          <AlertTriangle className="w-5 h-5" />
          <span className="font-medium">{t('panicButton')} (Press P x3)</span>
        </motion.button>
      )}
      
      {/* System-Wide Protection Notice */}
      <motion.div
        className="mt-6 rounded-2xl p-4 border border-orange-500/30"
        style={{ background: 'linear-gradient(135deg, rgba(255,165,0,0.1) 0%, rgba(255,140,0,0.05) 100%)' }}
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.5 }}
      >
        <div className="flex items-start gap-3">
          <div className="w-10 h-10 rounded-xl bg-orange-500/20 flex items-center justify-center flex-shrink-0">
            <AlertTriangle className="w-5 h-5 text-orange-400" />
          </div>
          <div>
            <p className="text-orange-400 font-medium text-sm mb-1">
              ⚠️ Web App = Browser Traffic Only
            </p>
            <p className="text-slate-400 text-xs mb-3">
              This web version only protects browser traffic. For <strong className="text-white">FULL SYSTEM-WIDE protection</strong> (all apps, games, system services), download our Desktop or Mobile app.
            </p>
            <div className="flex gap-2 flex-wrap">
              <a 
                href="https://github.com/FreedomVPN/releases" 
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-2 bg-gradient-to-r from-green-500 to-emerald-600 text-white text-xs font-bold px-4 py-2 rounded-lg hover:opacity-90 transition-opacity"
              >
                🖥️ Windows App
              </a>
              <a 
                href="https://github.com/FreedomVPN/releases" 
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-2 bg-gradient-to-r from-blue-500 to-blue-600 text-white text-xs font-bold px-4 py-2 rounded-lg hover:opacity-90 transition-opacity"
              >
                📱 Android App
              </a>
            </div>
          </div>
        </div>
      </motion.div>
    </div>
  )
}
