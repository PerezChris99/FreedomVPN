import { createContext, useContext, useState, useEffect } from 'react'

// Translations for 8 African languages (matching Android version)
const translations = {
  en: {
    appName: "FreedomVPN",
    tagline: "Digital Freedom for Africa",
    connect: "Connect",
    disconnect: "Disconnect",
    connecting: "Connecting...",
    connected: "Connected",
    disconnected: "Not Connected",
    protected: "Your connection is protected",
    unprotected: "Your connection is not protected",
    selectServer: "Select Server",
    servers: "Servers",
    settings: "Settings",
    statistics: "Statistics",
    home: "Home",
    dataSaved: "Data Saved",
    timeSaved: "Time Connected",
    currentServer: "Current Server",
    speed: "Speed",
    ping: "Ping",
    obfuscation: "Obfuscation",
    compression: "Data Compression",
    language: "Language",
    stealthMode: "Stealth Mode",
    panicButton: "Panic Button",
    appDisguise: "App Disguise",
    darkMode: "Dark Mode",
    autoConnect: "Auto Connect",
    killSwitch: "Kill Switch",
    splitTunneling: "Split Tunneling",
    protocol: "Protocol",
    onboardingWelcome: "Welcome to FreedomVPN",
    onboardingPrivacy: "Your Privacy Matters",
    onboardingSpeed: "Optimized for Africa",
    onboardingStealth: "Stay Hidden",
    onboardingReady: "Ready to Connect",
    next: "Next",
    skip: "Skip",
    getStarted: "Get Started",
    recommended: "Recommended",
    fastest: "Fastest",
    nearest: "Nearest",
    africa: "Africa",
    europe: "Europe",
    americas: "Americas",
    asia: "Asia",
    moneySaved: "Money Saved",
    ugx: "UGX",
    dataCompression: "Data Compression",
    bandwidthOptimizer: "Bandwidth Optimizer",
    lowBandwidthMode: "Low Bandwidth Mode",
    african_servers: "African Servers",
    obfuscationLevel: "Obfuscation Level",
    maximum: "Maximum",
    standard: "Standard",
    minimal: "Minimal",
    panicActivated: "Panic Mode Activated",
    pressToExit: "Press anywhere to exit",
  },
  sw: {
    appName: "FreedomVPN",
    tagline: "Uhuru wa Kidijitali kwa Afrika",
    connect: "Unganisha",
    disconnect: "Tenganisha",
    connecting: "Inaunganisha...",
    connected: "Imeunganishwa",
    disconnected: "Haijaunganishwa",
    protected: "Muunganisho wako umelindwa",
    unprotected: "Muunganisho wako haujalindwa",
    selectServer: "Chagua Seva",
    servers: "Seva",
    settings: "Mipangilio",
    statistics: "Takwimu",
    home: "Nyumbani",
    dataSaved: "Data Iliyohifadhiwa",
    timeSaved: "Muda Uliounganishwa",
    currentServer: "Seva ya Sasa",
    speed: "Kasi",
    ping: "Ping",
    obfuscation: "Ufichaji",
    compression: "Ukandamizaji wa Data",
    language: "Lugha",
    stealthMode: "Hali ya Siri",
    panicButton: "Kitufe cha Dharura",
    appDisguise: "Kuficha Programu",
    darkMode: "Hali ya Giza",
    autoConnect: "Unganisha Kiotomatiki",
    killSwitch: "Swichi ya Kuua",
    splitTunneling: "Mgawanyo wa Tunnel",
    protocol: "Itifaki",
    next: "Ifuatayo",
    skip: "Ruka",
    getStarted: "Anza",
    recommended: "Inayopendekezwa",
    fastest: "Haraka Zaidi",
    nearest: "Karibu Zaidi",
    moneySaved: "Pesa Zilizohifadhiwa",
    ugx: "UGX",
  },
  lg: {
    appName: "FreedomVPN",
    tagline: "Eddembe lya Digito mu Afrika",
    connect: "Kwatibwa",
    disconnect: "Kwawukana",
    connecting: "Ekwatibwa...",
    connected: "Ekwatiddwa",
    disconnected: "Tekwatiddwa",
    protected: "Enkolagana yo erindiddwa",
    unprotected: "Enkolagana yo terindiddwa",
    selectServer: "Londa Seva",
    servers: "Seva",
    settings: "Entegeka",
    statistics: "Ebibalo",
    home: "Awaka",
    dataSaved: "Data Eteredde",
    timeSaved: "Obudde Obukwatiddwa",
    currentServer: "Seva Eriwo",
    speed: "Embiro",
    ping: "Ping",
    next: "Ekiddako",
    skip: "Buuka",
    getStarted: "Tandika",
    moneySaved: "Ssente Ezitereddwa",
    ugx: "UGX",
  },
  fr: {
    appName: "FreedomVPN",
    tagline: "Liberté Numérique pour l'Afrique",
    connect: "Connecter",
    disconnect: "Déconnecter",
    connecting: "Connexion...",
    connected: "Connecté",
    disconnected: "Non Connecté",
    protected: "Votre connexion est protégée",
    unprotected: "Votre connexion n'est pas protégée",
    selectServer: "Sélectionner Serveur",
    servers: "Serveurs",
    settings: "Paramètres",
    statistics: "Statistiques",
    home: "Accueil",
    dataSaved: "Données Économisées",
    timeSaved: "Temps Connecté",
    currentServer: "Serveur Actuel",
    speed: "Vitesse",
    ping: "Ping",
    obfuscation: "Obfuscation",
    compression: "Compression des Données",
    language: "Langue",
    stealthMode: "Mode Furtif",
    panicButton: "Bouton Panique",
    appDisguise: "Déguisement de l'App",
    next: "Suivant",
    skip: "Passer",
    getStarted: "Commencer",
    recommended: "Recommandé",
    fastest: "Le Plus Rapide",
    nearest: "Le Plus Proche",
    moneySaved: "Argent Économisé",
  },
  am: {
    appName: "FreedomVPN",
    tagline: "ለአፍሪካ ዲጂታል ነፃነት",
    connect: "አገናኝ",
    disconnect: "አቋርጥ",
    connecting: "በማገናኘት ላይ...",
    connected: "ተገናኝቷል",
    disconnected: "አልተገናኘም",
    protected: "ግንኙነትዎ ተጠብቋል",
    unprotected: "ግንኙነትዎ አልተጠበቀም",
    selectServer: "አገልጋይ ይምረጡ",
    servers: "አገልጋዮች",
    settings: "ቅንብሮች",
    statistics: "ስታቲስቲክስ",
    home: "ቤት",
    next: "ቀጣይ",
    skip: "ዝለል",
    getStarted: "ጀምር",
  },
  ar: {
    appName: "FreedomVPN",
    tagline: "الحرية الرقمية لأفريقيا",
    connect: "اتصال",
    disconnect: "قطع الاتصال",
    connecting: "جاري الاتصال...",
    connected: "متصل",
    disconnected: "غير متصل",
    protected: "اتصالك محمي",
    unprotected: "اتصالك غير محمي",
    selectServer: "اختر الخادم",
    servers: "الخوادم",
    settings: "الإعدادات",
    statistics: "الإحصائيات",
    home: "الرئيسية",
    next: "التالي",
    skip: "تخطي",
    getStarted: "ابدأ",
  },
  pt: {
    appName: "FreedomVPN",
    tagline: "Liberdade Digital para África",
    connect: "Conectar",
    disconnect: "Desconectar",
    connecting: "Conectando...",
    connected: "Conectado",
    disconnected: "Desconectado",
    protected: "Sua conexão está protegida",
    unprotected: "Sua conexão não está protegida",
    selectServer: "Selecionar Servidor",
    servers: "Servidores",
    settings: "Configurações",
    statistics: "Estatísticas",
    home: "Início",
    next: "Próximo",
    skip: "Pular",
    getStarted: "Começar",
  },
  ha: {
    appName: "FreedomVPN",
    tagline: "Yancin Dijital na Afirka",
    connect: "Haɗa",
    disconnect: "Yanke",
    connecting: "Ana haɗawa...",
    connected: "An haɗa",
    disconnected: "Ba a haɗa ba",
    protected: "An kare haɗin ku",
    unprotected: "Ba a kare haɗin ku ba",
    selectServer: "Zaɓi Uwar Garke",
    servers: "Sabobin",
    settings: "Saituna",
    statistics: "Ƙididdiga",
    home: "Gida",
    next: "Na gaba",
    skip: "Tsallake",
    getStarted: "Fara",
  }
}

const LanguageContext = createContext()

export function LanguageProvider({ children }) {
  const [language, setLanguage] = useState('en')

  useEffect(() => {
    const savedLanguage = localStorage.getItem('freedomvpn_language')
    if (savedLanguage && translations[savedLanguage]) {
      setLanguage(savedLanguage)
    } else {
      // Auto-detect from browser
      const browserLang = navigator.language.split('-')[0]
      if (translations[browserLang]) {
        setLanguage(browserLang)
      }
    }
  }, [])

  const changeLanguage = (lang) => {
    if (translations[lang]) {
      setLanguage(lang)
      localStorage.setItem('freedomvpn_language', lang)
    }
  }

  const t = (key) => {
    return translations[language]?.[key] || translations.en[key] || key
  }

  const availableLanguages = [
    { code: 'en', name: 'English', flag: '🇬🇧' },
    { code: 'sw', name: 'Kiswahili', flag: '🇹🇿' },
    { code: 'lg', name: 'Luganda', flag: '🇺🇬' },
    { code: 'fr', name: 'Français', flag: '🇫🇷' },
    { code: 'am', name: 'አማርኛ', flag: '🇪🇹' },
    { code: 'ar', name: 'العربية', flag: '🇸🇩' },
    { code: 'pt', name: 'Português', flag: '🇲🇿' },
    { code: 'ha', name: 'Hausa', flag: '🇳🇬' },
  ]

  return (
    <LanguageContext.Provider value={{ language, changeLanguage, t, availableLanguages }}>
      {children}
    </LanguageContext.Provider>
  )
}

export function useLanguage() {
  const context = useContext(LanguageContext)
  if (!context) {
    throw new Error('useLanguage must be used within a LanguageProvider')
  }
  return context
}
