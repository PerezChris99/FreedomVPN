import { useState } from 'react'
import { motion } from 'framer-motion'
import { useVpn } from '../context/VpnContext'
import { useLanguage } from '../context/LanguageContext'
import { Search, Star, Zap, MapPin, Signal, Globe, Server } from 'lucide-react'

export default function Servers() {
  const { servers, selectedServer, selectServer, connectionState, getServersByRegion, getFastestServer } = useVpn()
  const { t } = useLanguage()
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedRegion, setSelectedRegion] = useState('all')

  const regions = [
    { id: 'all', name: 'All', icon: Globe },
    { id: 'africa', name: t('africa'), icon: MapPin },
    { id: 'europe', name: t('europe'), icon: Server },
    { id: 'americas', name: t('americas'), icon: Server },
    { id: 'asia', name: t('asia'), icon: Server },
  ]

  const filteredServers = servers.filter(server => {
    const matchesSearch = server.country.toLowerCase().includes(searchQuery.toLowerCase()) ||
                          server.city.toLowerCase().includes(searchQuery.toLowerCase())
    const matchesRegion = selectedRegion === 'all' || server.region === selectedRegion
    return matchesSearch && matchesRegion
  })

  const fastestServer = getFastestServer()

  const getPingColor = (ping) => {
    if (ping < 50) return 'text-green-400'
    if (ping < 100) return 'text-yellow-400'
    if (ping < 150) return 'text-orange-400'
    return 'text-red-400'
  }

  const getLoadColor = (load) => {
    if (load < 40) return 'bg-green-500'
    if (load < 70) return 'bg-yellow-500'
    return 'bg-red-500'
  }

  return (
    <div className="min-h-screen p-3 sm:p-4 lg:p-8 pt-6 sm:pt-8">
      {/* Header */}
      <div className="mb-4 sm:mb-6">
        <h1 className="text-xl sm:text-2xl lg:text-3xl font-bold text-white mb-1">{t('servers')}</h1>
        <p className="text-slate-400 text-xs sm:text-sm">{filteredServers.length} servers available</p>
      </div>

      {/* Search */}
      <div className="relative mb-4">
        <Search className="absolute left-3 sm:left-4 top-1/2 -translate-y-1/2 w-4 h-4 sm:w-5 sm:h-5 text-slate-400" />
        <input
          type="text"
          placeholder="Search countries or cities..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="w-full bg-slate-800 text-white text-sm sm:text-base pl-10 sm:pl-12 pr-4 py-2.5 sm:py-3 rounded-xl border border-slate-700 focus:outline-none focus:border-green-500 transition-colors"
        />
      </div>

      {/* Region tabs */}
      <div className="flex gap-2 mb-4 sm:mb-6 overflow-x-auto pb-2 scrollbar-hide -mx-3 sm:mx-0 px-3 sm:px-0">
        {regions.map(region => (
          <button
            key={region.id}
            onClick={() => setSelectedRegion(region.id)}
            className={`flex items-center gap-1.5 sm:gap-2 px-3 sm:px-4 py-2 rounded-xl whitespace-nowrap transition-all text-xs sm:text-sm touch-target ${
              selectedRegion === region.id
                ? 'bg-green-600 text-white'
                : 'bg-slate-800 text-slate-300 hover:bg-slate-700'
            }`}
          >
            <region.icon className="w-3 h-3 sm:w-4 sm:h-4" />
            {region.name}
          </button>
        ))}
      </div>

      {/* Quick actions */}
      <div className="grid grid-cols-2 gap-2 sm:gap-4 mb-4 sm:mb-6">
        <motion.button
          onClick={() => selectServer(fastestServer)}
          className="glass rounded-xl sm:rounded-2xl p-3 sm:p-4 text-left card-hover touch-target"
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
        >
          <div className="flex items-center gap-2 sm:gap-3 mb-2">
            <div className="w-8 h-8 sm:w-10 sm:h-10 bg-yellow-500/20 rounded-lg sm:rounded-xl flex items-center justify-center">
              <Zap className="w-4 h-4 sm:w-5 sm:h-5 text-yellow-400" />
            </div>
            <div className="min-w-0">
              <p className="text-white font-medium text-xs sm:text-sm truncate">{t('fastest')}</p>
              <p className="text-slate-400 text-xs truncate">{fastestServer.city}</p>
            </div>
          </div>
          <p className="text-green-400 text-xs sm:text-sm">{fastestServer.ping}ms</p>
        </motion.button>

        <motion.button
          onClick={() => selectServer(servers.find(s => s.region === 'africa'))}
          className="glass rounded-xl sm:rounded-2xl p-3 sm:p-4 text-left card-hover touch-target"
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
        >
          <div className="flex items-center gap-2 sm:gap-3 mb-2">
            <div className="w-8 h-8 sm:w-10 sm:h-10 bg-green-500/20 rounded-lg sm:rounded-xl flex items-center justify-center">
              <Star className="w-4 h-4 sm:w-5 sm:h-5 text-green-400" />
            </div>
            <div className="min-w-0">
              <p className="text-white font-medium text-xs sm:text-sm truncate">{t('recommended')}</p>
              <p className="text-slate-400 text-xs truncate">{t('african_servers')}</p>
            </div>
          </div>
          <p className="text-yellow-400 text-xs sm:text-sm">Priority</p>
        </motion.button>
      </div>

      {/* Server list - Desktop shows grid, mobile shows list */}
      <div className="space-y-2 sm:space-y-3 lg:grid lg:grid-cols-2 lg:gap-4 lg:space-y-0">
        {filteredServers.map((server, index) => (
          <motion.button
            key={server.id}
            onClick={() => selectServer(server)}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: index * 0.03 }}
            className={`w-full glass rounded-xl sm:rounded-2xl p-3 sm:p-4 flex items-center gap-3 sm:gap-4 text-left transition-all card-hover touch-target ${
              selectedServer.id === server.id ? 'ring-2 ring-green-500' : ''
            }`}
          >
            {/* Flag */}
            <div className="text-2xl sm:text-3xl flex-shrink-0">{server.flag}</div>

            {/* Info */}
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2">
                <p className="text-white font-medium text-sm sm:text-base truncate">{server.country}</p>
                {server.region === 'africa' && (
                  <span className="text-xs bg-green-500/20 text-green-400 px-1.5 sm:px-2 py-0.5 rounded-full flex-shrink-0">
                    Africa
                  </span>
                )}
              </div>
              <p className="text-slate-400 text-xs sm:text-sm truncate">{server.city}</p>
            </div>

            {/* Stats */}
            <div className="text-right flex-shrink-0">
              <div className="flex items-center gap-1 justify-end mb-1">
                <Signal className={`w-3 h-3 sm:w-4 sm:h-4 ${getPingColor(server.ping)}`} />
                <span className={`font-medium text-xs sm:text-sm ${getPingColor(server.ping)}`}>{server.ping}ms</span>
              </div>
              <div className="flex items-center gap-1 sm:gap-2">
                <div className="w-10 sm:w-16 h-1.5 sm:h-2 bg-slate-700 rounded-full overflow-hidden">
                  <div 
                    className={`h-full ${getLoadColor(server.load)} transition-all`}
                    style={{ width: `${server.load}%` }}
                  />
                </div>
                <span className="text-slate-400 text-xs">{server.load}%</span>
              </div>
            </div>

            {/* Selected indicator */}
            {selectedServer.id === server.id && (
              <div className="w-2 h-2 sm:w-3 sm:h-3 bg-green-500 rounded-full animate-pulse flex-shrink-0" />
            )}
          </motion.button>
        ))}
      </div>
    </div>
  )
}
