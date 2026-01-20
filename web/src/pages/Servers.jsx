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
    <div className="min-h-screen p-4 pt-8">
      {/* Header */}
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-white mb-1">{t('servers')}</h1>
        <p className="text-slate-400 text-sm">{filteredServers.length} servers available</p>
      </div>

      {/* Search */}
      <div className="relative mb-4">
        <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400" />
        <input
          type="text"
          placeholder="Search countries or cities..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="w-full bg-slate-800 text-white pl-12 pr-4 py-3 rounded-xl border border-slate-700 focus:outline-none focus:border-green-500 transition-colors"
        />
      </div>

      {/* Region tabs */}
      <div className="flex gap-2 mb-6 overflow-x-auto pb-2 scrollbar-hide">
        {regions.map(region => (
          <button
            key={region.id}
            onClick={() => setSelectedRegion(region.id)}
            className={`flex items-center gap-2 px-4 py-2 rounded-xl whitespace-nowrap transition-all ${
              selectedRegion === region.id
                ? 'bg-green-600 text-white'
                : 'bg-slate-800 text-slate-300 hover:bg-slate-700'
            }`}
          >
            <region.icon className="w-4 h-4" />
            {region.name}
          </button>
        ))}
      </div>

      {/* Quick actions */}
      <div className="grid grid-cols-2 gap-4 mb-6">
        <motion.button
          onClick={() => selectServer(fastestServer)}
          className="glass rounded-2xl p-4 text-left card-hover"
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
        >
          <div className="flex items-center gap-3 mb-2">
            <div className="w-10 h-10 bg-yellow-500/20 rounded-xl flex items-center justify-center">
              <Zap className="w-5 h-5 text-yellow-400" />
            </div>
            <div>
              <p className="text-white font-medium">{t('fastest')}</p>
              <p className="text-slate-400 text-xs">{fastestServer.city}</p>
            </div>
          </div>
          <p className="text-green-400 text-sm">{fastestServer.ping}ms</p>
        </motion.button>

        <motion.button
          onClick={() => selectServer(servers.find(s => s.region === 'africa'))}
          className="glass rounded-2xl p-4 text-left card-hover"
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
        >
          <div className="flex items-center gap-3 mb-2">
            <div className="w-10 h-10 bg-green-500/20 rounded-xl flex items-center justify-center">
              <Star className="w-5 h-5 text-green-400" />
            </div>
            <div>
              <p className="text-white font-medium">{t('recommended')}</p>
              <p className="text-slate-400 text-xs">{t('african_servers')}</p>
            </div>
          </div>
          <p className="text-yellow-400 text-sm">Priority</p>
        </motion.button>
      </div>

      {/* Server list */}
      <div className="space-y-3">
        {filteredServers.map((server, index) => (
          <motion.button
            key={server.id}
            onClick={() => selectServer(server)}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: index * 0.05 }}
            className={`w-full glass rounded-2xl p-4 flex items-center gap-4 text-left transition-all card-hover ${
              selectedServer.id === server.id ? 'ring-2 ring-green-500' : ''
            }`}
          >
            {/* Flag */}
            <div className="text-3xl">{server.flag}</div>

            {/* Info */}
            <div className="flex-1">
              <div className="flex items-center gap-2">
                <p className="text-white font-medium">{server.country}</p>
                {server.region === 'africa' && (
                  <span className="text-xs bg-green-500/20 text-green-400 px-2 py-0.5 rounded-full">
                    Africa
                  </span>
                )}
              </div>
              <p className="text-slate-400 text-sm">{server.city}</p>
            </div>

            {/* Stats */}
            <div className="text-right">
              <div className="flex items-center gap-1 justify-end mb-1">
                <Signal className={`w-4 h-4 ${getPingColor(server.ping)}`} />
                <span className={`font-medium ${getPingColor(server.ping)}`}>{server.ping}ms</span>
              </div>
              <div className="flex items-center gap-2">
                <div className="w-16 h-2 bg-slate-700 rounded-full overflow-hidden">
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
              <div className="w-3 h-3 bg-green-500 rounded-full animate-pulse" />
            )}
          </motion.button>
        ))}
      </div>
    </div>
  )
}
