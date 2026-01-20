// FreedomVPN Extension Popup Script

// Translations (matching Android and Web versions)
const translations = {
  en: {
    tagline: "Digital Freedom for Africa",
    connect: "Connect",
    disconnect: "Disconnect",
    connecting: "Connecting...",
    connected: "Connected",
    disconnected: "Not Connected",
    protected: "Your connection is protected",
    unprotected: "Your connection is not protected",
    selectServer: "Select Server",
    settings: "Settings",
    language: "Language",
    compression: "Data Compression",
    stealthMode: "Stealth Mode",
    autoConnect: "Auto Connect",
    yourIP: "Your IP Address",
    timeConnected: "Time Connected",
    moneySaved: "Money Saved",
    copied: "Copied!",
  },
  sw: {
    tagline: "Uhuru wa Kidijitali kwa Afrika",
    connect: "Unganisha",
    disconnect: "Tenganisha",
    connecting: "Inaunganisha...",
    connected: "Imeunganishwa",
    disconnected: "Haijaunganishwa",
    protected: "Muunganisho wako umelindwa",
    unprotected: "Muunganisho wako haujalindwa",
    selectServer: "Chagua Seva",
    settings: "Mipangilio",
    language: "Lugha",
    moneySaved: "Pesa Zilizohifadhiwa",
  },
  lg: {
    tagline: "Eddembe lya Digito mu Afrika",
    connect: "Kwatibwa",
    disconnect: "Kwawukana",
    connecting: "Ekwatibwa...",
    connected: "Ekwatiddwa",
    disconnected: "Tekwatiddwa",
    moneySaved: "Ssente Ezitereddwa",
  },
  fr: {
    tagline: "Liberté Numérique pour l'Afrique",
    connect: "Connecter",
    disconnect: "Déconnecter",
    connecting: "Connexion...",
    connected: "Connecté",
    disconnected: "Non Connecté",
    protected: "Votre connexion est protégée",
    unprotected: "Votre connexion n'est pas protégée",
  }
};

// State
let state = {
  isConnected: false,
  isConnecting: false,
  currentServer: null,
  servers: {},
  settings: {},
  startTime: null
};

let currentLanguage = 'en';
let currentRegion = 'africa';
let connectionTimer = null;

// DOM Elements
const statusCard = document.getElementById('statusCard');
const connectBtn = document.getElementById('connectBtn');
const statusText = document.querySelector('.status-text');
const statusMessage = document.getElementById('statusMessage');
const btnText = document.querySelector('.btn-text');
const ipAddress = document.getElementById('ipAddress');
const ipCard = document.getElementById('ipCard');
const locationFlag = document.getElementById('locationFlag');
const locationText = document.getElementById('locationText');
const serverList = document.getElementById('serverList');
const connectionTime = document.getElementById('connectionTime');
const moneySaved = document.getElementById('moneySaved');
const settingsBtn = document.getElementById('settingsBtn');
const settingsPanel = document.getElementById('settingsPanel');
const backBtn = document.getElementById('backBtn');

// Server regions
const serverRegions = {
  africa: ['ug', 'ke', 'za', 'ng', 'eg'],
  europe: ['nl', 'de', 'gb', 'fr'],
  americas: ['us', 'br'],
  asia: ['sg', 'jp', 'ae']
};

// Initialize
document.addEventListener('DOMContentLoaded', async () => {
  await loadState();
  setupEventListeners();
  updateUI();
  fetchCurrentIP();
});

// Load state from background
async function loadState() {
  return new Promise((resolve) => {
    chrome.runtime.sendMessage({ action: 'getState' }, (response) => {
      if (response) {
        state.servers = response.servers || {};
        state.settings = response.settings || {};
        
        if (response.state) {
          state.isConnected = response.state.isConnected;
          state.currentServer = response.state.currentServer;
          state.startTime = response.state.startTime;
        }
        
        currentLanguage = state.settings.language || 'en';
      }
      resolve();
    });
  });
}

// Setup event listeners
function setupEventListeners() {
  // Connect button
  connectBtn.addEventListener('click', toggleConnection);
  
  // Settings
  settingsBtn.addEventListener('click', () => {
    settingsPanel.classList.add('active');
  });
  
  backBtn.addEventListener('click', () => {
    settingsPanel.classList.remove('active');
  });
  
  // Server tabs
  document.querySelectorAll('.tab').forEach(tab => {
    tab.addEventListener('click', () => {
      document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
      tab.classList.add('active');
      currentRegion = tab.dataset.region;
      renderServers();
    });
  });
  
  // IP buttons
  document.getElementById('refreshIpBtn').addEventListener('click', fetchCurrentIP);
  document.getElementById('copyIpBtn').addEventListener('click', copyIP);
  
  // Settings toggles
  document.getElementById('languageSelect').addEventListener('change', (e) => {
    currentLanguage = e.target.value;
    state.settings.language = currentLanguage;
    saveSettings();
    applyTranslations();
  });
  
  document.getElementById('compressionToggle').addEventListener('change', (e) => {
    state.settings.compression = e.target.checked;
    saveSettings();
  });
  
  document.getElementById('stealthToggle').addEventListener('change', (e) => {
    state.settings.stealthMode = e.target.checked;
    saveSettings();
  });
  
  document.getElementById('autoConnectToggle').addEventListener('change', (e) => {
    state.settings.autoConnect = e.target.checked;
    saveSettings();
  });
}

// Toggle connection
async function toggleConnection() {
  if (state.isConnecting) return;
  
  if (state.isConnected) {
    await disconnect();
  } else {
    await connect();
  }
}

// Connect to VPN
async function connect() {
  const selectedServerId = state.settings.selectedServer || 'ug';
  
  state.isConnecting = true;
  updateUI();
  
  return new Promise((resolve) => {
    chrome.runtime.sendMessage({ 
      action: 'connect', 
      serverId: selectedServerId 
    }, (response) => {
      state.isConnecting = false;
      
      if (response?.success) {
        state.isConnected = true;
        state.currentServer = response.state?.currentServer;
        state.startTime = Date.now();
        startConnectionTimer();
      }
      
      updateUI();
      
      // Refresh IP after connection
      setTimeout(fetchCurrentIP, 2000);
      
      resolve(response);
    });
  });
}

// Disconnect from VPN
async function disconnect() {
  return new Promise((resolve) => {
    chrome.runtime.sendMessage({ action: 'disconnect' }, (response) => {
      state.isConnected = false;
      state.currentServer = null;
      state.startTime = null;
      stopConnectionTimer();
      updateUI();
      
      // Refresh IP after disconnect
      setTimeout(fetchCurrentIP, 1000);
      
      resolve(response);
    });
  });
}

// Select server
function selectServer(serverId) {
  state.settings.selectedServer = serverId;
  saveSettings();
  renderServers();
  
  // If connected, reconnect to new server
  if (state.isConnected) {
    disconnect().then(() => connect());
  }
}

// Fetch current IP
async function fetchCurrentIP() {
  ipAddress.textContent = 'Detecting...';
  locationText.textContent = 'Detecting location...';
  
  try {
    const response = await fetch('https://ipapi.co/json/', { cache: 'no-store' });
    const data = await response.json();
    
    ipAddress.textContent = data.ip;
    locationFlag.textContent = getCountryFlag(data.country_code);
    locationText.textContent = `${data.city}, ${data.country_name}`;
    
    // Update card style based on connection
    if (state.isConnected) {
      ipCard.classList.add('protected');
    } else {
      ipCard.classList.remove('protected');
    }
  } catch (error) {
    console.error('Failed to fetch IP:', error);
    ipAddress.textContent = 'Unable to detect';
    locationText.textContent = 'Check your connection';
  }
}

// Copy IP to clipboard
function copyIP() {
  const ip = ipAddress.textContent;
  if (ip && ip !== 'Detecting...' && ip !== 'Unable to detect') {
    navigator.clipboard.writeText(ip);
    showToast(t('copied'));
  }
}

// Update UI based on state
function updateUI() {
  // Connection status
  statusCard.classList.remove('connected', 'connecting');
  
  if (state.isConnecting) {
    statusCard.classList.add('connecting');
    statusText.textContent = t('connecting');
    btnText.textContent = t('connecting');
    statusMessage.textContent = '';
  } else if (state.isConnected) {
    statusCard.classList.add('connected');
    statusText.textContent = t('connected');
    btnText.textContent = t('disconnect');
    statusMessage.textContent = t('protected');
    ipCard.classList.add('protected');
  } else {
    statusText.textContent = t('disconnected');
    btnText.textContent = t('connect');
    statusMessage.textContent = t('unprotected');
    ipCard.classList.remove('protected');
  }
  
  // Render servers
  renderServers();
  
  // Apply settings
  document.getElementById('languageSelect').value = currentLanguage;
  document.getElementById('compressionToggle').checked = state.settings.compression !== false;
  document.getElementById('stealthToggle').checked = state.settings.stealthMode || false;
  document.getElementById('autoConnectToggle').checked = state.settings.autoConnect || false;
  
  // Apply translations
  applyTranslations();
}

// Render server list
function renderServers() {
  const serverIds = serverRegions[currentRegion] || [];
  
  serverList.innerHTML = serverIds.map(id => {
    const server = state.servers[id];
    if (!server) return '';
    
    const isSelected = state.settings.selectedServer === id;
    
    return `
      <div class="server-item ${isSelected ? 'selected' : ''}" data-server="${id}">
        <span class="server-flag">${server.flag}</span>
        <div class="server-info">
          <span class="server-name">${server.country}</span>
          <span class="server-city">${server.city}</span>
        </div>
        <span class="server-ping">${Math.floor(Math.random() * 50) + 20}ms</span>
      </div>
    `;
  }).join('');
  
  // Add click handlers
  document.querySelectorAll('.server-item').forEach(item => {
    item.addEventListener('click', () => {
      selectServer(item.dataset.server);
    });
  });
}

// Connection timer
function startConnectionTimer() {
  stopConnectionTimer();
  
  connectionTimer = setInterval(() => {
    if (state.startTime) {
      const elapsed = Math.floor((Date.now() - state.startTime) / 1000);
      const hours = Math.floor(elapsed / 3600);
      const minutes = Math.floor((elapsed % 3600) / 60);
      const seconds = elapsed % 60;
      
      connectionTime.textContent = `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
      
      // Calculate money saved (simulated)
      const dataSavedMB = elapsed * 0.1; // Simulated data savings
      const ugxSaved = Math.floor(dataSavedMB * 50 * 0.45); // 45% compression * 50 UGX/MB
      moneySaved.textContent = `${ugxSaved.toLocaleString()} UGX`;
    }
  }, 1000);
}

function stopConnectionTimer() {
  if (connectionTimer) {
    clearInterval(connectionTimer);
    connectionTimer = null;
  }
  connectionTime.textContent = '00:00:00';
  moneySaved.textContent = '0 UGX';
}

function pad(num) {
  return num.toString().padStart(2, '0');
}

// Save settings
function saveSettings() {
  chrome.runtime.sendMessage({ 
    action: 'saveSettings', 
    settings: state.settings 
  });
}

// Translation helper
function t(key) {
  return translations[currentLanguage]?.[key] || translations.en[key] || key;
}

// Apply translations to UI
function applyTranslations() {
  document.querySelectorAll('[data-i18n]').forEach(el => {
    const key = el.dataset.i18n;
    const translated = t(key);
    if (translated) {
      el.textContent = translated;
    }
  });
}

// Get country flag emoji
function getCountryFlag(countryCode) {
  if (!countryCode || countryCode.length !== 2) return '🌍';
  
  const codePoints = countryCode
    .toUpperCase()
    .split('')
    .map(char => 127397 + char.charCodeAt(0));
  
  return String.fromCodePoint(...codePoints);
}

// Show toast notification
function showToast(message) {
  const toast = document.createElement('div');
  toast.className = 'toast';
  toast.textContent = message;
  document.body.appendChild(toast);
  
  setTimeout(() => toast.remove(), 2000);
}
