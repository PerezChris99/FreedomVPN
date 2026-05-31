import { useState } from 'react'
import { motion } from 'framer-motion'
import { useLanguage } from '../context/LanguageContext'
import { useVpn } from '../context/VpnContext'
import { useStealth } from '../context/StealthContext'
import { 
  Globe, 
  Shield, 
  Zap, 
  Eye, 
  EyeOff,
  AlertTriangle, 
  Smartphone,
  Lock,
  Wifi,
  Moon,
  Power,
  ChevronRight,
  Check,
  Languages
} from 'lucide-react'

export default function Settings() {
  const { t, language, changeLanguage, availableLanguages } = useLanguage()
  const { 
    obfuscationProtocol, 
    obfuscationProtocols, 
    setObfuscationProtocol,
    compressionEnabled,
    setCompressionEnabled,
    lowBandwidthMode,
    setLowBandwidthMode
  } = useVpn()
  const { 
    stealthMode, 
    toggleStealthMode, 
    appDisguise, 
    setDisguise, 
    disguises 
  } = useStealth()

  const [showLanguages, setShowLanguages] = useState(false)
  const [showObfuscation, setShowObfuscation] = useState(false)
  const [showDisguise, setShowDisguise] = useState(false)

  const SettingToggle = ({ label, description, icon: Icon, enabled, onChange, color = 'green' }) => (
    <div className="glass rounded-xl sm:rounded-2xl p-3 sm:p-4 flex items-center gap-3 sm:gap-4">
      <div className={`w-8 h-8 sm:w-10 sm:h-10 bg-${color}-500/20 rounded-lg sm:rounded-xl flex items-center justify-center flex-shrink-0`}>
        <Icon className={`w-4 h-4 sm:w-5 sm:h-5 text-${color}-400`} />
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-white font-medium text-sm sm:text-base">{label}</p>
        {description && <p className="text-slate-400 text-xs sm:text-sm truncate">{description}</p>}
      </div>
      <button
        onClick={onChange}
        className={`w-12 h-7 sm:w-14 sm:h-8 rounded-full transition-all flex-shrink-0 ${
          enabled ? 'bg-green-500' : 'bg-slate-600'
        }`}
      >
        <motion.div
          className="w-5 h-5 sm:w-6 sm:h-6 bg-white rounded-full shadow-md"
          animate={{ x: enabled ? (window.innerWidth < 640 ? 22 : 26) : 2 }}
          transition={{ type: 'spring', stiffness: 500, damping: 30 }}
        />
      </button>
    </div>
  )

  const SettingButton = ({ label, description, icon: Icon, value, onClick, color = 'blue' }) => (
    <button
      onClick={onClick}
      className="w-full glass rounded-xl sm:rounded-2xl p-3 sm:p-4 flex items-center gap-3 sm:gap-4 text-left hover:bg-white/5 transition-colors touch-target"
    >
      <div className={`w-8 h-8 sm:w-10 sm:h-10 bg-${color}-500/20 rounded-lg sm:rounded-xl flex items-center justify-center flex-shrink-0`}>
        <Icon className={`w-4 h-4 sm:w-5 sm:h-5 text-${color}-400`} />
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-white font-medium text-sm sm:text-base">{label}</p>
        {description && <p className="text-slate-400 text-xs sm:text-sm truncate">{description}</p>}
      </div>
      <div className="flex items-center gap-1 sm:gap-2 flex-shrink-0">
        <span className="text-green-400 text-xs sm:text-sm truncate max-w-20 sm:max-w-none">{value}</span>
        <ChevronRight className="w-4 h-4 sm:w-5 sm:h-5 text-slate-400" />
      </div>
    </button>
  )

  return (
    <div className="min-h-screen p-3 sm:p-4 lg:p-8 pt-6 sm:pt-8">
      {/* Header */}
      <div className="mb-4 sm:mb-6">
        <h1 className="text-xl sm:text-2xl lg:text-3xl font-bold text-white mb-1">{t('settings')}</h1>
        <p className="text-slate-400 text-xs sm:text-sm">Customize your VPN experience</p>
      </div>

      {/* Desktop grid layout */}
      <div className="lg:grid lg:grid-cols-2 lg:gap-6">
        {/* Connection Settings */}
        <div className="mb-4 sm:mb-6">
          <h2 className="text-slate-400 text-xs sm:text-sm font-medium mb-2 sm:mb-3 px-1">CONNECTION</h2>
          <div className="space-y-2 sm:space-y-3">
          <SettingButton
            icon={Globe}
            label={t('obfuscation')}
            description="Bypass deep packet inspection"
            value={obfuscationProtocol.name}
            onClick={() => setShowObfuscation(true)}
            color="purple"
          />

          <SettingToggle
            icon={Zap}
            label={t('compression')}
            description="Save up to 45% on data"
            enabled={compressionEnabled}
            onChange={() => setCompressionEnabled(!compressionEnabled)}
            color="yellow"
          />

          <SettingToggle
            icon={Wifi}
            label={t('lowBandwidthMode')}
            description="Optimized for 2G/3G networks"
            enabled={lowBandwidthMode}
            onChange={() => setLowBandwidthMode(!lowBandwidthMode)}
            color="blue"
          />
        </div>
      </div>

      {/* Security Settings */}
      <div className="mb-4 sm:mb-6">
        <h2 className="text-slate-400 text-xs sm:text-sm font-medium mb-2 sm:mb-3 px-1">SECURITY</h2>
        <div className="space-y-2 sm:space-y-3">
          <SettingToggle
            icon={stealthMode ? Eye : EyeOff}
            label={t('stealthMode')}
            description="Enable panic button & app disguise"
            enabled={stealthMode}
            onChange={toggleStealthMode}
            color="red"
          />

          {stealthMode && (
            <motion.div
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
            >
              <SettingButton
                icon={Smartphone}
                label={t('appDisguise')}
                description="Hide app appearance"
                value={disguises.find(d => d.id === appDisguise)?.name || 'None'}
                onClick={() => setShowDisguise(true)}
                color="orange"
              />
            </motion.div>
          )}

          <SettingToggle
            icon={Lock}
            label={t('killSwitch')}
            description="Block internet if VPN disconnects"
            enabled={true}
            onChange={() => {}}
            color="green"
          />
        </div>
      </div>
      </div>

      {/* General Settings */}
      <div className="mb-4 sm:mb-6">
        <h2 className="text-slate-400 text-xs sm:text-sm font-medium mb-2 sm:mb-3 px-1">GENERAL</h2>
        <div className="space-y-2 sm:space-y-3">
          <SettingButton
            icon={Languages}
            label={t('language')}
            description="Choose your language"
            value={availableLanguages.find(l => l.code === language)?.name}
            onClick={() => setShowLanguages(true)}
            color="cyan"
          />

          <SettingToggle
            icon={Power}
            label={t('autoConnect')}
            description="Connect on app start"
            enabled={false}
            onChange={() => {}}
            color="green"
          />
        </div>
      </div>

      {/* About */}
      <div className="glass rounded-xl sm:rounded-2xl p-3 sm:p-4 text-center">
        <p className="text-white font-medium text-sm sm:text-base">FreedomVPN</p>
        <p className="text-slate-400 text-xs sm:text-sm">Version 1.0.0</p>
        <p className="text-green-400 text-xs mt-2">Built for Uganda 🇺🇬</p>
      </div>

      {/* Language Modal */}
      {showLanguages && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="fixed inset-0 bg-black/80 flex items-center sm:items-end justify-center z-50 p-3 sm:p-4"
          onClick={() => setShowLanguages(false)}
        >
          <motion.div
            initial={{ y: 300, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            className="bg-slate-800 rounded-2xl sm:rounded-3xl w-full max-w-md p-4 sm:p-6 max-h-[80vh] overflow-hidden"
            onClick={e => e.stopPropagation()}
          >
            <h3 className="text-lg sm:text-xl font-bold text-white mb-4">Select Language</h3>
            <div className="space-y-2 max-h-64 sm:max-h-96 overflow-y-auto">
              {availableLanguages.map(lang => (
                <button
                  key={lang.code}
                  onClick={() => {
                    changeLanguage(lang.code)
                    setShowLanguages(false)
                  }}
                  className={`w-full flex items-center gap-3 sm:gap-4 p-3 sm:p-4 rounded-xl transition-colors touch-target ${
                    language === lang.code ? 'bg-green-500/20' : 'hover:bg-slate-700'
                  }`}
                >
                  <span className="text-xl sm:text-2xl">{lang.flag}</span>
                  <span className="text-white flex-1 text-left text-sm sm:text-base">{lang.name}</span>
                  {language === lang.code && <Check className="w-4 h-4 sm:w-5 sm:h-5 text-green-400" />}
                </button>
              ))}
            </div>
          </motion.div>
        </motion.div>
      )}

      {/* Obfuscation Modal */}
      {showObfuscation && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="fixed inset-0 bg-black/80 flex items-center sm:items-end justify-center z-50 p-3 sm:p-4"
          onClick={() => setShowObfuscation(false)}
        >
          <motion.div
            initial={{ y: 300, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            className="bg-slate-800 rounded-2xl sm:rounded-3xl w-full max-w-md p-4 sm:p-6 max-h-[80vh] overflow-hidden"
            onClick={e => e.stopPropagation()}
          >
            <h3 className="text-lg sm:text-xl font-bold text-white mb-4">Obfuscation Protocol</h3>
            <div className="space-y-2 max-h-64 sm:max-h-96 overflow-y-auto">
              {obfuscationProtocols.map(protocol => (
                <button
                  key={protocol.id}
                  onClick={() => {
                    setObfuscationProtocol(protocol)
                    setShowObfuscation(false)
                  }}
                  className={`w-full flex items-center gap-3 sm:gap-4 p-3 sm:p-4 rounded-xl transition-colors touch-target ${
                    obfuscationProtocol.id === protocol.id ? 'bg-green-500/20' : 'hover:bg-slate-700'
                  }`}
                >
                  <div className="flex-1 text-left">
                    <p className="text-white font-medium text-sm sm:text-base">{protocol.name}</p>
                    <p className="text-slate-400 text-xs sm:text-sm">{protocol.description}</p>
                  </div>
                  <div className="text-right">
                    <p className="text-green-400 text-xs sm:text-sm">{protocol.effectiveness}%</p>
                    <p className="text-slate-500 text-xs hidden sm:block">effectiveness</p>
                  </div>
                  {obfuscationProtocol.id === protocol.id && <Check className="w-4 h-4 sm:w-5 sm:h-5 text-green-400" />}
                </button>
              ))}
            </div>
          </motion.div>
        </motion.div>
      )}

      {/* App Disguise Modal */}
      {showDisguise && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="fixed inset-0 bg-black/80 flex items-center sm:items-end justify-center z-50 p-3 sm:p-4"
          onClick={() => setShowDisguise(false)}
        >
          <motion.div
            initial={{ y: 300, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            className="bg-slate-800 rounded-2xl sm:rounded-3xl w-full max-w-md p-4 sm:p-6 max-h-[80vh] overflow-hidden"
            onClick={e => e.stopPropagation()}
          >
            <h3 className="text-lg sm:text-xl font-bold text-white mb-4">App Disguise</h3>
            <div className="space-y-2">
              {disguises.map(disguise => (
                <button
                  key={disguise.id}
                  onClick={() => {
                    setDisguise(disguise.id)
                    setShowDisguise(false)
                  }}
                  className={`w-full flex items-center gap-3 sm:gap-4 p-3 sm:p-4 rounded-xl transition-colors touch-target ${
                    appDisguise === disguise.id ? 'bg-green-500/20' : 'hover:bg-slate-700'
                  }`}
                >
                  <span className="text-xl sm:text-2xl">{disguise.icon}</span>
                  <span className="text-white flex-1 text-left text-sm sm:text-base">{disguise.name}</span>
                  {appDisguise === disguise.id && <Check className="w-4 h-4 sm:w-5 sm:h-5 text-green-400" />}
                </button>
              ))}
            </div>
          </motion.div>
        </motion.div>
      )}
    </div>
  )
}
