// FreedomVPN Extension Popup Script - Enhanced Anti-Censorship Version

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
    dataSaved: "Data Saved",
    copied: "Copied!",
    connectBest: "Quick Connect (Best Server)",
    latency: "Latency",
    quality: "Quality",
    excellent: "Excellent",
    good: "Good",
    fair: "Fair",
    poor: "Poor",
    critical: "Critical",
    blocksEvaded: "Blocks Evaded",
    reconnections: "Auto-Reconnects",
    killSwitch: "Kill Switch",
    leakProtection: "Leak Protection",
    preferAfrican: "Prefer African Servers"
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
    dataSaved: "Data Iliyohifadhiwa",
    connectBest: "Unganisha Haraka"
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
  startTime: null,
  stats: {
    bytesIn: 0,
    bytesOut: 0,
    dataSaved: 0,
    moneySaved: 0,
    blocksEvaded: 0,
    reconnections: 0
  },
  health: {
    latency: 0,
    quality: 'unknown'
  },
  ip: {
    real: null,
    masked: null
  },
  multiHop: {
    enabled: false,
    chain: [],
    preset: 'BALANCED'
  }
};

let currentLanguage = 'en';
let currentRegion = 'africa';
let connectionTimer = null;
let statsUpdateInterval = null;

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

// Server regions - updated with new server IDs
const serverRegions = {
  africa: ['ke-nrb', 'rw-kgl', 'tz-dar', 'za-jhb', 'eg-cai', 'ng-los', 'gh-acc'],
  europe: ['nl-ams', 'de-fra', 'gb-lon', 'fr-par', 'ch-zur'],
  americas: ['us-nyc', 'us-lax', 'br-sao', 'ca-tor'],
  asia: ['sg-sin', 'jp-tky', 'ae-dxb', 'in-mum'],
  cdn: ['cdn-cloudflare', 'cdn-google', 'cdn-azure', 'cdn-amazon']
};

// Initialize
document.addEventListener('DOMContentLoaded', async () => {
  await loadState();
  setupEventListeners();
  updateUI();
  fetchCurrentIP();
  startStatsUpdater();
});

// Load state from background
async function loadState() {
  return new Promise((resolve) => {
    chrome.runtime.sendMessage({ action: 'getState' }, (response) => {
      if (response) {
        state.servers = response.servers || {};
        
        if (response.state) {
          state.isConnected = response.state.isConnected;
          state.currentServer = response.state.currentServer;
          state.startTime = response.state.startTime;
          state.settings = response.state.settings || {};
          state.stats = response.state.stats || state.stats;
          state.health = response.state.health || state.health;
          state.ip = response.state.ip || state.ip;
        }
        
        currentLanguage = state.settings.language || 'en';
      }
      resolve();
    });
  });
}

// Start real-time stats updater
function startStatsUpdater() {
  stopStatsUpdater();
  statsUpdateInterval = setInterval(async () => {
    if (state.isConnected) {
      await updateStats();
    }
  }, 5000);
}

function stopStatsUpdater() {
  if (statsUpdateInterval) {
    clearInterval(statsUpdateInterval);
    statsUpdateInterval = null;
  }
}

// Update stats from background
async function updateStats() {
  return new Promise((resolve) => {
    chrome.runtime.sendMessage({ action: 'getStats' }, (response) => {
      if (response) {
        state.stats = response.stats || state.stats;
        state.health = response.health || state.health;
        updateStatsDisplay();
      }
      resolve();
    });
  });
}

// Update stats display
function updateStatsDisplay() {
  // Update latency indicator
  const latencyEl = document.getElementById('latencyValue');
  const qualityEl = document.getElementById('qualityIndicator');
  
  if (latencyEl && state.health.latency) {
    latencyEl.textContent = `${state.health.latency}ms`;
  }
  
  if (qualityEl) {
    qualityEl.className = `quality-indicator quality-${state.health.quality || 'unknown'}`;
    qualityEl.textContent = t(state.health.quality || 'unknown');
  }
  
  // Update blocks evaded
  const blocksEl = document.getElementById('blocksEvaded');
  if (blocksEl && state.stats.blocksEvaded > 0) {
    blocksEl.textContent = `🛡️ ${state.stats.blocksEvaded} blocks evaded`;
  }
}

// Setup event listeners
function setupEventListeners() {
  // Connect button
  connectBtn.addEventListener('click', toggleConnection);
  
  // Quick connect (best server) - add button if exists
  const quickConnectBtn = document.getElementById('quickConnectBtn');
  if (quickConnectBtn) {
    quickConnectBtn.addEventListener('click', quickConnect);
  }
  
  // Settings
  settingsBtn.addEventListener('click', () => {
    settingsPanel.classList.add('active');
  });
  
  backBtn.addEventListener('click', () => {
    settingsPanel.classList.remove('active');
  });
  
  // Server tabs - add CDN tab
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
  
  // Enhanced settings - kill switch, leak protection, prefer African
  const killSwitchToggle = document.getElementById('killSwitchToggle');
  if (killSwitchToggle) {
    killSwitchToggle.addEventListener('change', (e) => {
      state.settings.killSwitch = e.target.checked;
      saveSettings();
    });
  }
  
  const leakProtectionToggle = document.getElementById('leakProtectionToggle');
  if (leakProtectionToggle) {
    leakProtectionToggle.addEventListener('change', (e) => {
      state.settings.leakProtection = e.target.checked;
      saveSettings();
    });
  }
  
  const preferAfricanToggle = document.getElementById('preferAfricanToggle');
  if (preferAfricanToggle) {
    preferAfricanToggle.addEventListener('change', (e) => {
      state.settings.preferAfrican = e.target.checked;
      saveSettings();
    });
  }
  
  // Force failover button
  const failoverBtn = document.getElementById('forceFailoverBtn');
  if (failoverBtn) {
    failoverBtn.addEventListener('click', forceFailover);
  }
  
  // Multi-Hop toggle
  const multiHopToggle = document.getElementById('multiHopToggle');
  if (multiHopToggle) {
    multiHopToggle.addEventListener('change', (e) => {
      toggleMultiHop(e.target.checked);
    });
  }
  
  // Multi-Hop preset selector
  const multiHopPreset = document.getElementById('multiHopPreset');
  if (multiHopPreset) {
    multiHopPreset.addEventListener('change', (e) => {
      setMultiHopPreset(e.target.value);
    });
  }
}

// Multi-Hop Presets Configuration
const MULTIHOP_PRESETS = {
  FAST: { hopCount: 2, diverse: false, speed: 90 },
  BALANCED: { hopCount: 2, diverse: true, speed: 88 },
  MAXIMUM: { hopCount: 3, diverse: true, speed: 80 },
  PARANOID: { hopCount: 4, diverse: true, speed: 70 }
};

// Toggle Multi-Hop Mode
async function toggleMultiHop(enabled) {
  console.log('[MultiHop] Toggle:', enabled);
  
  if (enabled) {
    // Build multi-hop chain
    const preset = MULTIHOP_PRESETS[state.multiHop.preset];
    const chain = buildMultiHopChain(preset.hopCount, preset.diverse);
    
    if (chain.length >= 2) {
      state.multiHop.enabled = true;
      state.multiHop.chain = chain;
      
      // Update UI
      updateMultiHopDisplay();
      showToast(`Multi-Hop activated! ${chain.length} servers 🔀`);
      
      // If connected, re-route through chain
      if (state.isConnected) {
        // Reconnect with multi-hop routing
        await reconnectWithMultiHop();
      }
    } else {
      showToast('Not enough servers for Multi-Hop');
      document.getElementById('multiHopToggle').checked = false;
    }
  } else {
    state.multiHop.enabled = false;
    state.multiHop.chain = [];
    updateMultiHopDisplay();
    showToast('Multi-Hop disabled');
  }
}

// Build Multi-Hop chain
function buildMultiHopChain(hopCount, diverse) {
  const allServers = Object.entries(state.servers);
  if (allServers.length < hopCount) return [];
  
  const chain = [];
  const usedRegions = new Set();
  
  // Sort by latency (prefer faster servers)
  const sorted = allServers.sort((a, b) => {
    const latA = a[1].latency || 100;
    const latB = b[1].latency || 100;
    return latA - latB;
  });
  
  for (let i = 0; i < hopCount && sorted.length > 0; i++) {
    let selected = null;
    
    if (diverse && usedRegions.size > 0) {
      // Find server in different region
      const idx = sorted.findIndex(([id, s]) => !usedRegions.has(getRegion(id)));
      if (idx !== -1) {
        selected = sorted.splice(idx, 1)[0];
      }
    }
    
    if (!selected && sorted.length > 0) {
      selected = sorted.shift();
    }
    
    if (selected) {
      const [id, server] = selected;
      chain.push({
        id,
        ...server,
        isEntry: i === 0,
        isExit: i === hopCount - 1
      });
      usedRegions.add(getRegion(id));
    }
  }
  
  return chain;
}

// Get region from server ID
function getRegion(serverId) {
  for (const [region, ids] of Object.entries(serverRegions)) {
    if (ids.includes(serverId)) return region;
  }
  return 'unknown';
}

// Update Multi-Hop display
function updateMultiHopDisplay() {
  const chainEl = document.getElementById('multiHopChain');
  const routeEl = document.getElementById('multiHopRoute');
  const speedEl = document.getElementById('multiHopSpeed');
  
  if (!chainEl || !routeEl) return;
  
  if (state.multiHop.enabled && state.multiHop.chain.length > 0) {
    chainEl.style.display = 'block';
    
    // Build route display
    const routeHTML = ['<span style="color: #22c55e;">You</span>'];
    state.multiHop.chain.forEach((hop, i) => {
      const style = hop.isExit 
        ? 'background: rgba(147,51,234,0.3); color: #c084fc; padding: 2px 6px; border-radius: 4px;'
        : 'color: #888;';
      routeHTML.push(`<span style="color: #555;">→</span>`);
      routeHTML.push(`<span style="${style}">${hop.flag} ${hop.city}</span>`);
    });
    routeHTML.push(`<span style="color: #555;">→</span>`);
    routeHTML.push(`<span style="color: #3b82f6;">🌐 Web</span>`);
    
    routeEl.innerHTML = routeHTML.join(' ');
    
    // Update speed estimate
    const preset = MULTIHOP_PRESETS[state.multiHop.preset];
    if (speedEl) {
      speedEl.textContent = `~${preset.speed}% speed`;
    }
  } else {
    chainEl.style.display = 'none';
  }
}

// Set Multi-Hop preset
function setMultiHopPreset(presetKey) {
  state.multiHop.preset = presetKey;
  
  if (state.multiHop.enabled) {
    // Rebuild chain with new preset
    const preset = MULTIHOP_PRESETS[presetKey];
    state.multiHop.chain = buildMultiHopChain(preset.hopCount, preset.diverse);
    updateMultiHopDisplay();
  }
}

// Reconnect with Multi-Hop routing
async function reconnectWithMultiHop() {
  if (!state.multiHop.enabled || state.multiHop.chain.length === 0) return;
  
  showToast('Routing through multi-hop chain...');
  
  // The exit server is our final destination
  const exitServer = state.multiHop.chain[state.multiHop.chain.length - 1];
  
  // Send to background to set up proxy chain
  chrome.runtime.sendMessage({
    action: 'setMultiHop',
    chain: state.multiHop.chain
  }, (response) => {
    if (response?.success) {
      showToast(`Multi-Hop active: ${state.multiHop.chain.map(h => h.flag).join('→')}`);
    }
  });
}

// Quick connect to best server
async function quickConnect() {
  if (state.isConnecting) return;
  
  state.isConnecting = true;
  updateUI();
  
  return new Promise((resolve) => {
    chrome.runtime.sendMessage({ action: 'connectBest' }, (response) => {
      state.isConnecting = false;
      
      if (response?.success) {
        state.isConnected = true;
        state.currentServer = response.state?.currentServer;
        state.startTime = Date.now();
        startConnectionTimer();
        showToast('Connected to best server! 🚀');
      } else {
        showToast('Connection failed. Trying fallback...');
      }
      
      updateUI();
      setTimeout(fetchCurrentIP, 2000);
      resolve(response);
    });
  });
}

// Force failover to another server
async function forceFailover() {
  if (!state.isConnected) return;
  
  showToast('Switching to another server...');
  
  return new Promise((resolve) => {
    chrome.runtime.sendMessage({ action: 'forceFailover' }, (response) => {
      if (response?.success) {
        state.currentServer = response.newServer;
        showToast(`Switched to ${response.newServer?.city}! 🔄`);
      }
      updateUI();
      setTimeout(fetchCurrentIP, 2000);
      resolve(response);
    });
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

// Connect to VPN with enhanced error handling
async function connect() {
  const selectedServerId = state.settings.selectedServer || 'ke-nrb';
  
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
        state.ip.masked = response.ip;
        startConnectionTimer();
        showToast(`Connected to ${state.currentServer?.city}! 🛡️`);
      } else {
        showToast('Connection failed. Try another server.');
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

// Render server list with health indicators and obfuscation info
function renderServers() {
  const serverIds = serverRegions[currentRegion] || [];
  
  serverList.innerHTML = serverIds.map(id => {
    const server = state.servers[id];
    if (!server) return '';
    
    const isSelected = state.settings.selectedServer === id;
    const isConnected = state.isConnected && state.currentServer?.id === id;
    
    // Show obfuscation methods
    const obfuscationBadge = server.obfuscation?.includes('domain-front') 
      ? '<span class="obfuscation-badge">CDN</span>'
      : server.obfuscation?.includes('websocket')
        ? '<span class="obfuscation-badge">WS</span>'
        : '<span class="obfuscation-badge">TLS</span>';
    
    // African server indicator
    const africanBadge = server.isAfrican 
      ? '<span class="african-badge">🌍 African</span>' 
      : '';
    
    return `
      <div class="server-item ${isSelected ? 'selected' : ''} ${isConnected ? 'connected' : ''}" data-server="${id}">
        <span class="server-flag">${server.flag || '🌐'}</span>
        <div class="server-info">
          <span class="server-name">${server.country}</span>
          <span class="server-city">${server.city} ${africanBadge}</span>
          ${server.description ? `<span class="server-desc">${server.description}</span>` : ''}
        </div>
        <div class="server-meta">
          ${obfuscationBadge}
          <span class="server-ping">${isConnected ? state.health.latency + 'ms' : '~'}</span>
        </div>
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
