import { NavLink } from 'react-router-dom'
import { useLanguage } from '../context/LanguageContext'
import { Home, Server, BarChart3, Settings } from 'lucide-react'

export default function Navigation() {
  const { t } = useLanguage()

  const navItems = [
    { path: '/', icon: Home, label: t('home') },
    { path: '/servers', icon: Server, label: t('servers') },
    { path: '/statistics', icon: BarChart3, label: t('statistics') },
    { path: '/settings', icon: Settings, label: t('settings') },
  ]

  return (
    <nav className="fixed bottom-0 left-0 right-0 bg-slate-900/95 backdrop-blur-lg border-t border-slate-700/50 px-4 py-2 z-40">
      <div className="max-w-md mx-auto">
        <div className="flex justify-around">
          {navItems.map(({ path, icon: Icon, label }) => (
            <NavLink
              key={path}
              to={path}
              className={({ isActive }) =>
                `flex flex-col items-center gap-1 px-4 py-2 rounded-xl transition-all ${
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
  )
}
