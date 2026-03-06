import { useState, useEffect } from 'react'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { LanguageProvider } from './context/LanguageContext'
import { VpnProvider } from './context/VpnContext'
import { StealthProvider } from './context/StealthContext'
import { ChatProvider } from './context/ChatContext'
import Onboarding from './pages/Onboarding'
import Dashboard from './pages/Dashboard'
import Servers from './pages/Servers'
import Settings from './pages/Settings'
import Statistics from './pages/Statistics'
import Chat from './pages/Chat'
import Navigation from './components/Navigation'
import PanicOverlay from './components/PanicOverlay'

function App() {
  const [showOnboarding, setShowOnboarding] = useState(true)
  const [isStealthMode, setIsStealthMode] = useState(false)

  useEffect(() => {
    const hasSeenOnboarding = localStorage.getItem('freedomvpn_onboarding_complete')
    if (hasSeenOnboarding) {
      setShowOnboarding(false)
    }
  }, [])

  const completeOnboarding = () => {
    localStorage.setItem('freedomvpn_onboarding_complete', 'true')
    setShowOnboarding(false)
  }

  if (showOnboarding) {
    return (
      <LanguageProvider>
        <Onboarding onComplete={completeOnboarding} />
      </LanguageProvider>
    )
  }

  return (
    <BrowserRouter>
      <LanguageProvider>
        <VpnProvider>
          <StealthProvider>
            <ChatProvider>
              <div className="min-h-screen bg-gradient-to-br from-slate-900 to-slate-800">
                <PanicOverlay />
                <Navigation />
                {/* Main content area - offset for sidebar on desktop */}
                <div className="lg:ml-64 pb-20 lg:pb-0">
                  <div className="max-w-6xl mx-auto">
                    <Routes>
                      <Route path="/" element={<Dashboard />} />
                      <Route path="/chat" element={<Chat />} />
                      <Route path="/servers" element={<Servers />} />
                      <Route path="/statistics" element={<Statistics />} />
                      <Route path="/settings" element={<Settings />} />
                    </Routes>
                  </div>
                </div>
              </div>
            </ChatProvider>
          </StealthProvider>
        </VpnProvider>
      </LanguageProvider>
    </BrowserRouter>
  )
}

export default App
