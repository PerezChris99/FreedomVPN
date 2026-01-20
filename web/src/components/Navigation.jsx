import { NavLink } from 'react-router-dom'
import { useLanguage } from '../context/LanguageContext'
import { Home, Server, BarChart3, Settings, Shield } from 'lucide-react'

export default function Navigation() {
  const { t } = useLanguage()

  const navItems = [
    { path: '/', icon: Home, label: t('home') },
    { path: '/servers', icon: Server, label: t('servers') },
    { path: '/statistics', icon: BarChart3, label: t('statistics') },
    { path: '/settings', icon: Settings, label: t('settings') },
  ]

  return (
    <>
      {/* Desktop Sidebar - Hidden on mobile */}
      <nav className="hidden lg:flex fixed left-0 top-0 bottom-0 w-64 bg-slate-900/95 backdrop-blur-lg border-r border-slate-700/50 flex-col z-40">
        {/* Logo */}
        <div className="p-6 border-b border-slate-700/50">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 bg-gradient-to-br from-green-500 to-emerald-600 rounded-xl flex items-center justify-center">
              <Shield className="w-6 h-6 text-white" />
            </div>
            <div>
              <h1 className="text-white font-bold text-lg">FreedomVPN</h1>
              <p className="text-slate-500 text-xs">Web Dashboard</p>
            </div>
          </div>
        </div>

        {/* Navigation Items */}
        <div className="flex-1 p-4">
          <div className="space-y-2">
            {navItems.map(({ path, icon: Icon, label }) => (
              <NavLink
                key={path}
                to={path}
                className={({ isActive }) =>
                  `flex items-center gap-3 px-4 py-3 rounded-xl transition-all ${
                    isActive 
                      ? 'bg-green-500/20 text-green-400 border border-green-500/30' 
                      : 'text-slate-400 hover:bg-slate-800 hover:text-slate-200'
                  }`
                }
              >
                {({ isActive }) => (
                  <>
                    <Icon className={`w-5 h-5 ${isActive ? 'text-green-400' : ''}`} />
                    <span className="font-medium">{label}</span>
                  </>
                )}
              </NavLink>
            ))}
          </div>
        </div>

        {/* Desktop Footer */}
        <div className="p-4 border-t border-slate-700/50">
          <div className="bg-amber-500/10 border border-amber-500/30 rounded-xl p-3 mb-4">
            <p className="text-amber-400 text-xs font-medium mb-1">⚠️ Web Demo Mode</p>
            <p className="text-amber-200/70 text-xs">Download desktop or mobile app for real VPN protection.</p>
          </div>
          <a 
            href="https://perezchris.netlify.app" 
            target="_blank" 
            rel="noopener noreferrer"
            className="text-xs text-slate-500 hover:text-green-400 transition-colors flex items-center justify-center gap-1"
          >
            ⚡ Developed by <span className="font-semibold">Nemesis</span>
          </a>
        </div>
      </nav>

      {/* Mobile Bottom Nav - Hidden on desktop */}
      <nav className="lg:hidden fixed bottom-0 left-0 right-0 bg-slate-900/95 backdrop-blur-lg border-t border-slate-700/50 px-4 py-2 z-40 safe-area-bottom">
        <div className="max-w-md mx-auto">
          <div className="flex justify-around">
            {navItems.map(({ path, icon: Icon, label }) => (
              <NavLink
                key={path}
                to={path}
                className={({ isActive }) =>
                  `flex flex-col items-center gap-1 px-4 py-2 rounded-xl transition-all touch-target ${
                    isActive 
                      ? 'text-green-400' 
                      : 'text-slate-400 hover:text-slate-200'
                  }`
                }
              >
                {({ isActive }) => (
                  <>
                    <Icon className={`w-6 h-6 ${isActive ? 'scale-110' : ''} transition-transform`} />
                    <span className="text-xs font-medium">{label}</span>
                  </>
                )}
              </NavLink>
            ))}
          </div>
          <div className="text-center mt-2 pb-1">
            <a 
              href="https://perezchris.netlify.app" 
              target="_blank" 
              rel="noopener noreferrer"
              className="text-xs text-slate-500 hover:text-green-400 transition-colors"
            >
              ⚡ Developed by <span className="font-semibold">Nemesis</span>
            </a>
          </div>
        </div>
      </nav>
    </>
  )
}
