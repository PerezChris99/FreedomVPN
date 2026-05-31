import { motion } from 'framer-motion'
import { useVpn } from '../context/VpnContext'
import { useLanguage } from '../context/LanguageContext'
import { 
  TrendingDown, 
  Clock, 
  DollarSign, 
  Wifi, 
  Shield, 
  Zap,
  BarChart3,
  PieChart,
  Activity
} from 'lucide-react'

export default function Statistics() {
  const { stats, formatDataSize, formatTime, formatMoney, compressionEnabled } = useVpn()
  const { t } = useLanguage()

  // Calculate compression ratio visualization
  const compressionPercent = compressionEnabled ? 45 : 0
  const originalData = stats.dataUsed + stats.dataSaved
  const compressedData = stats.dataUsed

  const statCards = [
    {
      icon: Wifi,
      label: 'Total Data Used',
      value: formatDataSize(stats.dataUsed),
      color: 'text-blue-400',
      bgColor: 'bg-blue-500/20',
    },
    {
      icon: TrendingDown,
      label: t('dataSaved'),
      value: formatDataSize(stats.dataSaved),
      color: 'text-green-400',
      bgColor: 'bg-green-500/20',
    },
    {
      icon: Clock,
      label: t('timeSaved'),
      value: formatTime(stats.connectionTime),
      color: 'text-purple-400',
      bgColor: 'bg-purple-500/20',
    },
    {
      icon: DollarSign,
      label: t('moneySaved'),
      value: `${formatMoney(stats.moneySaved)} UGX`,
      color: 'text-yellow-400',
      bgColor: 'bg-yellow-500/20',
    },
  ]

  return (
    <div className="min-h-screen p-3 sm:p-4 lg:p-8 pt-6 sm:pt-8">
      {/* Header */}
      <div className="mb-4 sm:mb-6">
        <h1 className="text-xl sm:text-2xl lg:text-3xl font-bold text-white mb-1">{t('statistics')}</h1>
        <p className="text-slate-400 text-xs sm:text-sm">Your usage and savings</p>
      </div>

      {/* Main stats grid */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-2 sm:gap-4 mb-4 sm:mb-6">
        {statCards.map((stat, index) => (
          <motion.div
            key={stat.label}
            className="glass rounded-xl sm:rounded-2xl p-3 sm:p-4"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: index * 0.1 }}
          >
            <div className={`w-8 h-8 sm:w-10 sm:h-10 ${stat.bgColor} rounded-lg sm:rounded-xl flex items-center justify-center mb-2 sm:mb-3`}>
              <stat.icon className={`w-4 h-4 sm:w-5 sm:h-5 ${stat.color}`} />
            </div>
            <p className="text-slate-400 text-xs sm:text-sm mb-1 truncate">{stat.label}</p>
            <p className={`text-base sm:text-xl font-bold ${stat.color} truncate`}>{stat.value}</p>
          </motion.div>
        ))}
      </div>

      {/* Desktop: Side by side, Mobile: Stack */}
      <div className="lg:grid lg:grid-cols-2 lg:gap-6">
        {/* Compression visualization */}
        <motion.div
          className="glass rounded-xl sm:rounded-2xl p-4 sm:p-6 mb-4 sm:mb-6 lg:mb-0"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.4 }}
        >
          <div className="flex items-center gap-2 sm:gap-3 mb-4">
            <div className="w-8 h-8 sm:w-10 sm:h-10 bg-green-500/20 rounded-lg sm:rounded-xl flex items-center justify-center">
              <Zap className="w-4 h-4 sm:w-5 sm:h-5 text-green-400" />
            </div>
            <div>
              <p className="text-white font-medium text-sm sm:text-base">{t('dataCompression')}</p>
              <p className="text-slate-400 text-xs sm:text-sm">{compressionPercent}% compression ratio</p>
            </div>
          </div>

          {/* Visual comparison */}
          <div className="space-y-3">
            <div>
              <div className="flex justify-between text-xs sm:text-sm mb-1">
                <span className="text-slate-400">Without Compression</span>
                <span className="text-slate-300">{formatDataSize(originalData)}</span>
              </div>
              <div className="h-3 sm:h-4 bg-slate-700 rounded-full overflow-hidden">
                <div className="h-full bg-red-500 rounded-full" style={{ width: '100%' }} />
              </div>
            </div>

            <div>
              <div className="flex justify-between text-xs sm:text-sm mb-1">
                <span className="text-slate-400">With Compression</span>
                <span className="text-green-400">{formatDataSize(compressedData)}</span>
              </div>
              <div className="h-3 sm:h-4 bg-slate-700 rounded-full overflow-hidden">
                <motion.div 
                  className="h-full bg-green-500 rounded-full" 
                  initial={{ width: 0 }}
                  animate={{ width: `${100 - compressionPercent}%` }}
                  transition={{ duration: 1, delay: 0.5 }}
                />
              </div>
            </div>
          </div>

          {/* Savings highlight */}
          <div className="mt-4 p-3 sm:p-4 bg-green-500/10 rounded-xl border border-green-500/20">
            <p className="text-green-400 text-center">
              <span className="font-bold text-xl sm:text-2xl">{compressionPercent}%</span>
              <br />
              <span className="text-xs sm:text-sm">Less data used = More money saved!</span>
            </p>
          </div>
        </motion.div>

        {/* Money savings breakdown */}
        <motion.div
          className="glass rounded-xl sm:rounded-2xl p-4 sm:p-6 mb-4 sm:mb-6 lg:mb-0"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.5 }}
        >
          <div className="flex items-center gap-2 sm:gap-3 mb-4">
            <div className="w-8 h-8 sm:w-10 sm:h-10 bg-yellow-500/20 rounded-lg sm:rounded-xl flex items-center justify-center">
              <DollarSign className="w-4 h-4 sm:w-5 sm:h-5 text-yellow-400" />
            </div>
            <div>
              <p className="text-white font-medium text-sm sm:text-base">Money Saved</p>
              <p className="text-slate-400 text-xs sm:text-sm">Based on UGX 50/MB rate</p>
            </div>
          </div>

          <div className="grid grid-cols-3 gap-2 sm:gap-4 text-center">
            <div>
              <p className="text-slate-400 text-xs sm:text-sm mb-1">Today</p>
              <p className="text-white font-bold text-xs sm:text-base">{formatMoney(stats.moneySaved)} UGX</p>
            </div>
            <div>
              <p className="text-slate-400 text-xs sm:text-sm mb-1">This Week</p>
              <p className="text-white font-bold text-xs sm:text-base">{formatMoney(stats.moneySaved * 7)} UGX</p>
            </div>
            <div>
              <p className="text-slate-400 text-xs sm:text-sm mb-1">This Month</p>
              <p className="text-yellow-400 font-bold text-xs sm:text-base">{formatMoney(stats.moneySaved * 30)} UGX</p>
            </div>
          </div>
        </motion.div>
      </div>

      {/* Security stats */}
      <motion.div
        className="glass rounded-xl sm:rounded-2xl p-4 sm:p-6"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.6 }}
      >
        <div className="flex items-center gap-2 sm:gap-3 mb-4">
          <div className="w-8 h-8 sm:w-10 sm:h-10 bg-purple-500/20 rounded-lg sm:rounded-xl flex items-center justify-center">
            <Shield className="w-4 h-4 sm:w-5 sm:h-5 text-purple-400" />
          </div>
          <div>
            <p className="text-white font-medium text-sm sm:text-base">Security Status</p>
            <p className="text-slate-400 text-xs sm:text-sm">Your protection level</p>
          </div>
        </div>

        <div className="space-y-2 sm:space-y-3 lg:grid lg:grid-cols-2 lg:gap-x-8 lg:space-y-0">
          <div className="flex justify-between items-center py-1">
            <span className="text-slate-300 text-sm">Encryption</span>
            <span className="text-green-400 text-xs sm:text-sm">AES-256-GCM</span>
          </div>
          <div className="flex justify-between items-center py-1">
            <span className="text-slate-300 text-sm">Protocol</span>
            <span className="text-green-400 text-xs sm:text-sm">WireGuard</span>
          </div>
          <div className="flex justify-between items-center py-1">
            <span className="text-slate-300 text-sm">DNS Leak Protection</span>
            <span className="text-green-400 text-xs sm:text-sm">Active</span>
          </div>
          <div className="flex justify-between items-center py-1">
            <span className="text-slate-300 text-sm">Kill Switch</span>
            <span className="text-green-400 text-xs sm:text-sm">Enabled</span>
          </div>
        </div>
      </motion.div>
    </div>
  )
}
