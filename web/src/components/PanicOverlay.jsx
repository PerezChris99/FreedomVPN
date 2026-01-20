import { motion, AnimatePresence } from 'framer-motion'
import { useStealth } from '../context/StealthContext'
import { useVpn } from '../context/VpnContext'
import { useLanguage } from '../context/LanguageContext'
import { Calculator, Cloud, FileText, Flashlight } from 'lucide-react'

// Decoy apps for when panic is activated
const DecoyCalculator = () => {
  return (
    <div className="h-full flex flex-col bg-gray-100 text-black">
      <div className="flex-1 flex items-end justify-end p-6">
        <span className="text-5xl font-light">0</span>
      </div>
      <div className="grid grid-cols-4 gap-1 p-2 bg-gray-200">
        {['C', '±', '%', '÷', '7', '8', '9', '×', '4', '5', '6', '-', '1', '2', '3', '+', '0', '0', '.', '='].map((btn, i) => (
          <div 
            key={i} 
            className={`aspect-square flex items-center justify-center text-2xl rounded-full ${
              ['÷', '×', '-', '+', '='].includes(btn) 
                ? 'bg-orange-500 text-white' 
                : ['C', '±', '%'].includes(btn)
                ? 'bg-gray-300'
                : 'bg-gray-100'
            }`}
          >
            {btn}
          </div>
        ))}
      </div>
    </div>
  )
}

const DecoyWeather = () => {
  return (
    <div className="h-full bg-gradient-to-b from-blue-400 to-blue-600 text-white flex flex-col items-center justify-center">
      <Cloud className="w-32 h-32 mb-4" />
      <h1 className="text-6xl font-light mb-2">24°</h1>
      <p className="text-xl">Kampala</p>
      <p className="text-lg opacity-80">Partly Cloudy</p>
      <div className="mt-8 flex gap-8">
        <div className="text-center">
          <p className="text-sm opacity-80">High</p>
          <p className="text-xl">28°</p>
        </div>
        <div className="text-center">
          <p className="text-sm opacity-80">Low</p>
          <p className="text-xl">19°</p>
        </div>
      </div>
    </div>
  )
}

const DecoyNotes = () => {
  return (
    <div className="h-full bg-yellow-100 text-gray-800 p-6">
      <h1 className="text-2xl font-bold mb-4 text-gray-700">Notes</h1>
      <div className="space-y-4">
        <div className="bg-white p-4 rounded-lg shadow">
          <h2 className="font-medium">Shopping List</h2>
          <p className="text-gray-500 text-sm">Milk, Bread, Eggs...</p>
        </div>
        <div className="bg-white p-4 rounded-lg shadow">
          <h2 className="font-medium">Meeting Notes</h2>
          <p className="text-gray-500 text-sm">Discuss project timeline...</p>
        </div>
        <div className="bg-white p-4 rounded-lg shadow">
          <h2 className="font-medium">Ideas</h2>
          <p className="text-gray-500 text-sm">New app concept...</p>
        </div>
      </div>
    </div>
  )
}

const DecoyFlashlight = () => {
  return (
    <div className="h-full bg-black text-white flex flex-col items-center justify-center">
      <Flashlight className="w-32 h-32 mb-8 text-yellow-400" />
      <div className="w-24 h-24 bg-gray-800 rounded-full flex items-center justify-center">
        <div className="w-20 h-20 bg-gray-700 rounded-full" />
      </div>
      <p className="mt-8 text-gray-400">Tap to turn on</p>
    </div>
  )
}

export default function PanicOverlay() {
  const { panicActive, deactivatePanic, appDisguise } = useStealth()
  const { disconnect } = useVpn()
  const { t } = useLanguage()

  // When panic is activated, disconnect VPN
  if (panicActive) {
    disconnect()
  }

  const getDecoyApp = () => {
    switch (appDisguise) {
      case 'calculator':
        return <DecoyCalculator />
      case 'weather':
        return <DecoyWeather />
      case 'notes':
        return <DecoyNotes />
      case 'flashlight':
        return <DecoyFlashlight />
      default:
        // Show panic message if no disguise
        return (
          <div className="h-full flex flex-col items-center justify-center text-center p-8">
            <motion.div
              initial={{ scale: 0 }}
              animate={{ scale: 1 }}
              className="w-24 h-24 bg-red-500/20 rounded-full flex items-center justify-center mb-6"
            >
              <span className="text-5xl">🛑</span>
            </motion.div>
            <h1 className="text-3xl font-bold text-red-400 mb-4">{t('panicActivated')}</h1>
            <p className="text-slate-300 mb-8">VPN disconnected. Activity cleared.</p>
            <p className="text-slate-500 text-sm">{t('pressToExit')}</p>
          </div>
        )
    }
  }

  return (
    <AnimatePresence>
      {panicActive && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          className="fixed inset-0 z-[100] bg-slate-900"
          onClick={deactivatePanic}
        >
          {getDecoyApp()}
          
          {/* Hidden exit hint - only shows after 5 seconds */}
          <motion.p
            initial={{ opacity: 0 }}
            animate={{ opacity: 0.3 }}
            transition={{ delay: 5 }}
            className="absolute bottom-4 left-0 right-0 text-center text-xs text-slate-500"
          >
            Press ESC or tap to exit
          </motion.p>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
