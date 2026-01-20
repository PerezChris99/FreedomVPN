// Shared configuration for all FreedomVPN platforms
// This file should be kept in sync across: web, extension, android, desktop

export const APP_CONFIG = {
  name: 'FreedomVPN',
  version: '1.0.0',
  tagline: 'Digital Freedom for Africa',
  website: 'https://github.com/PerezChris99/FreedomVPN',
  
  // Theme colors (same across all platforms)
  colors: {
    primary: '#16a34a',
    primaryLight: '#22c55e',
    primaryDark: '#15803d',
    danger: '#ef4444',
    warning: '#f59e0b',
    bgDark: '#0f172a',
    bgCard: '#1e293b',
    bgHover: '#334155',
    textPrimary: '#ffffff',
    textSecondary: '#94a3b8',
    textMuted: '#64748b',
    border: '#334155',
    uganda: {
      black: '#000000',
      yellow: '#FCDC04',
      red: '#D90000',
    }
  },
  
  // Pricing (for data savings calculations)
  pricing: {
    ugxPerMB: 50,  // Ugandan Shillings per megabyte
    compressionRatio: 0.45,  // 45% data savings
  },
  
  // Supported languages
  languages: [
    { code: 'en', name: 'English', flag: '🇬🇧' },
    { code: 'sw', name: 'Kiswahili', flag: '🇹🇿' },
    { code: 'lg', name: 'Luganda', flag: '🇺🇬' },
    { code: 'fr', name: 'Français', flag: '🇫🇷' },
    { code: 'am', name: 'አማርኛ', flag: '🇪🇹' },
    { code: 'ar', name: 'العربية', flag: '🇸🇩' },
    { code: 'pt', name: 'Português', flag: '🇲🇿' },
    { code: 'ha', name: 'Hausa', flag: '🇳🇬' },
  ],
};

// Server configuration shared across platforms
export const SERVERS = [
  // Africa (Priority - closest to Uganda)
  { id: 'ug', country: 'Uganda', city: 'Kampala', lat: 0.3476, lng: 32.5825, region: 'africa', flag: '🇺🇬' },
  { id: 'ke', country: 'Kenya', city: 'Nairobi', lat: -1.2921, lng: 36.8219, region: 'africa', flag: '🇰🇪' },
  { id: 'tz', country: 'Tanzania', city: 'Dar es Salaam', lat: -6.7924, lng: 39.2083, region: 'africa', flag: '🇹🇿' },
  { id: 'rw', country: 'Rwanda', city: 'Kigali', lat: -1.9403, lng: 29.8739, region: 'africa', flag: '🇷🇼' },
  { id: 'za', country: 'South Africa', city: 'Johannesburg', lat: -26.2041, lng: 28.0473, region: 'africa', flag: '🇿🇦' },
  { id: 'eg', country: 'Egypt', city: 'Cairo', lat: 30.0444, lng: 31.2357, region: 'africa', flag: '🇪🇬' },
  { id: 'ng', country: 'Nigeria', city: 'Lagos', lat: 6.5244, lng: 3.3792, region: 'africa', flag: '🇳🇬' },
  { id: 'gh', country: 'Ghana', city: 'Accra', lat: 5.6037, lng: -0.1870, region: 'africa', flag: '🇬🇭' },
  { id: 'et', country: 'Ethiopia', city: 'Addis Ababa', lat: 9.0320, lng: 38.7469, region: 'africa', flag: '🇪🇹' },
  
  // Europe
  { id: 'nl', country: 'Netherlands', city: 'Amsterdam', lat: 52.3676, lng: 4.9041, region: 'europe', flag: '🇳🇱' },
  { id: 'de', country: 'Germany', city: 'Frankfurt', lat: 50.1109, lng: 8.6821, region: 'europe', flag: '🇩🇪' },
  { id: 'gb', country: 'United Kingdom', city: 'London', lat: 51.5074, lng: -0.1278, region: 'europe', flag: '🇬🇧' },
  { id: 'fr', country: 'France', city: 'Paris', lat: 48.8566, lng: 2.3522, region: 'europe', flag: '🇫🇷' },
  
  // Americas
  { id: 'us-ny', country: 'United States', city: 'New York', lat: 40.7128, lng: -74.0060, region: 'americas', flag: '🇺🇸' },
  { id: 'us-la', country: 'United States', city: 'Los Angeles', lat: 34.0522, lng: -118.2437, region: 'americas', flag: '🇺🇸' },
  { id: 'br', country: 'Brazil', city: 'São Paulo', lat: -23.5505, lng: -46.6333, region: 'americas', flag: '🇧🇷' },
  
  // Asia
  { id: 'sg', country: 'Singapore', city: 'Singapore', lat: 1.3521, lng: 103.8198, region: 'asia', flag: '🇸🇬' },
  { id: 'jp', country: 'Japan', city: 'Tokyo', lat: 35.6762, lng: 139.6503, region: 'asia', flag: '🇯🇵' },
  { id: 'ae', country: 'UAE', city: 'Dubai', lat: 25.2048, lng: 55.2708, region: 'asia', flag: '🇦🇪' },
];

// Obfuscation protocols (same across all platforms)
export const OBFUSCATION_PROTOCOLS = [
  { id: 'tls', name: 'TLS Camouflage', description: 'Looks like HTTPS traffic', effectiveness: 95 },
  { id: 'http', name: 'HTTP Disguise', description: 'Mimics normal web browsing', effectiveness: 85 },
  { id: 'dns', name: 'DNS Tunnel', description: 'Hides in DNS queries', effectiveness: 90 },
  { id: 'shadowsocks', name: 'Shadowsocks-like', description: 'AEAD encrypted stream', effectiveness: 92 },
  { id: 'domain', name: 'Domain Fronting', description: 'Uses CDN as cover', effectiveness: 88 },
  { id: 'websocket', name: 'WebSocket Wrap', description: 'WebSocket encapsulation', effectiveness: 87 },
  { id: 'random', name: 'Random Padding', description: 'Adds noise to packets', effectiveness: 75 },
  { id: 'shape', name: 'Traffic Shaping', description: 'Alters timing patterns', effectiveness: 80 },
  { id: 'xor', name: 'XOR Cipher', description: 'Simple obfuscation layer', effectiveness: 70 },
];

// Translations (shared across all platforms)
export const TRANSLATIONS = {
  en: {
    appName: 'FreedomVPN',
    tagline: 'Digital Freedom for Africa',
    connect: 'Connect',
    disconnect: 'Disconnect',
    connecting: 'Connecting...',
    connected: 'Connected',
    disconnected: 'Not Connected',
    protected: 'Your connection is protected',
    unprotected: 'Your connection is not protected',
    selectServer: 'Select Server',
    servers: 'Servers',
    settings: 'Settings',
    statistics: 'Statistics',
    home: 'Home',
    dataSaved: 'Data Saved',
    timeSaved: 'Time Connected',
    currentServer: 'Current Server',
    speed: 'Speed',
    ping: 'Ping',
    obfuscation: 'Obfuscation',
    compression: 'Data Compression',
    language: 'Language',
    stealthMode: 'Stealth Mode',
    panicButton: 'Panic Button',
    appDisguise: 'App Disguise',
    darkMode: 'Dark Mode',
    autoConnect: 'Auto Connect',
    killSwitch: 'Kill Switch',
    splitTunneling: 'Split Tunneling',
    protocol: 'Protocol',
    next: 'Next',
    skip: 'Skip',
    getStarted: 'Get Started',
    recommended: 'Recommended',
    fastest: 'Fastest',
    nearest: 'Nearest',
    africa: 'Africa',
    europe: 'Europe',
    americas: 'Americas',
    asia: 'Asia',
    moneySaved: 'Money Saved',
    ugx: 'UGX',
    copied: 'Copied!',
    yourIP: 'Your IP Address',
  },
  sw: {
    appName: 'FreedomVPN',
    tagline: 'Uhuru wa Kidijitali kwa Afrika',
    connect: 'Unganisha',
    disconnect: 'Tenganisha',
    connecting: 'Inaunganisha...',
    connected: 'Imeunganishwa',
    disconnected: 'Haijaunganishwa',
    protected: 'Muunganisho wako umelindwa',
    unprotected: 'Muunganisho wako haujalindwa',
    selectServer: 'Chagua Seva',
    servers: 'Seva',
    settings: 'Mipangilio',
    statistics: 'Takwimu',
    home: 'Nyumbani',
    dataSaved: 'Data Iliyohifadhiwa',
    moneySaved: 'Pesa Zilizohifadhiwa',
  },
  lg: {
    appName: 'FreedomVPN',
    tagline: 'Eddembe lya Digito mu Afrika',
    connect: 'Kwatibwa',
    disconnect: 'Kwawukana',
    connecting: 'Ekwatibwa...',
    connected: 'Ekwatiddwa',
    disconnected: 'Tekwatiddwa',
    moneySaved: 'Ssente Ezitereddwa',
  },
  fr: {
    appName: 'FreedomVPN',
    tagline: 'Liberté Numérique pour l\'Afrique',
    connect: 'Connecter',
    disconnect: 'Déconnecter',
    connecting: 'Connexion...',
    connected: 'Connecté',
    disconnected: 'Non Connecté',
    protected: 'Votre connexion est protégée',
    unprotected: 'Votre connexion n\'est pas protégée',
  },
  am: {
    appName: 'FreedomVPN',
    tagline: 'ለአፍሪካ ዲጂታል ነፃነት',
    connect: 'አገናኝ',
    disconnect: 'አቋርጥ',
  },
  ar: {
    appName: 'FreedomVPN',
    tagline: 'الحرية الرقمية لأفريقيا',
    connect: 'اتصال',
    disconnect: 'قطع الاتصال',
  },
  pt: {
    appName: 'FreedomVPN',
    tagline: 'Liberdade Digital para África',
    connect: 'Conectar',
    disconnect: 'Desconectar',
  },
  ha: {
    appName: 'FreedomVPN',
    tagline: 'Yancin Dijital na Afirka',
    connect: 'Haɗa',
    disconnect: 'Yanke',
  }
};
