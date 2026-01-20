import { createContext, useContext, useState, useEffect, useCallback } from 'react'

const StealthContext = createContext()

export function StealthProvider({ children }) {
  const [stealthMode, setStealthMode] = useState(false)
  const [panicActive, setPanicActive] = useState(false)
  const [appDisguise, setAppDisguise] = useState('none') // none, calculator, weather, notes
  const [duressPin, setDuressPin] = useState('')
  
  // Track key presses for panic button (press 'P' 3 times quickly)
  const [keyPresses, setKeyPresses] = useState([])

  useEffect(() => {
    const savedStealth = localStorage.getItem('freedomvpn_stealth')
    const savedDisguise = localStorage.getItem('freedomvpn_disguise')
    if (savedStealth) setStealthMode(savedStealth === 'true')
    if (savedDisguise) setAppDisguise(savedDisguise)
  }, [])

  // Panic button keyboard trigger (press P 3 times in 2 seconds)
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key.toLowerCase() === 'p' && stealthMode) {
        const now = Date.now()
        setKeyPresses(prev => {
          const recent = [...prev, now].filter(t => now - t < 2000)
          if (recent.length >= 3) {
            activatePanic()
            return []
          }
          return recent
        })
      }
      
      // ESC to exit panic mode
      if (e.key === 'Escape' && panicActive) {
        deactivatePanic()
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [stealthMode, panicActive])

  const activatePanic = useCallback(() => {
    setPanicActive(true)
    // In a real app, this would:
    // - Disconnect VPN immediately
    // - Clear recent activity
    // - Show decoy screen
  }, [])

  const deactivatePanic = useCallback(() => {
    setPanicActive(false)
  }, [])

  const toggleStealthMode = useCallback(() => {
    const newValue = !stealthMode
    setStealthMode(newValue)
    localStorage.setItem('freedomvpn_stealth', String(newValue))
  }, [stealthMode])

  const setDisguise = useCallback((disguise) => {
    setAppDisguise(disguise)
    localStorage.setItem('freedomvpn_disguise', disguise)
  }, [])

  const disguises = [
    { id: 'none', name: 'None (FreedomVPN)', icon: '🛡️' },
    { id: 'calculator', name: 'Calculator', icon: '🔢' },
    { id: 'weather', name: 'Weather App', icon: '🌤️' },
    { id: 'notes', name: 'Notes', icon: '📝' },
    { id: 'flashlight', name: 'Flashlight', icon: '🔦' },
  ]

  return (
    <StealthContext.Provider value={{
      stealthMode,
      panicActive,
      appDisguise,
      duressPin,
      disguises,
      toggleStealthMode,
      activatePanic,
      deactivatePanic,
      setDisguise,
      setDuressPin,
    }}>
      {children}
    </StealthContext.Provider>
  )
}

export function useStealth() {
  const context = useContext(StealthContext)
  if (!context) {
    throw new Error('useStealth must be used within a StealthProvider')
  }
  return context
}
