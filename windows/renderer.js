/**
 * FreedomVPN for Windows - Renderer Process
 * Handles UI interactions and displays VPN status
 */

// Server regions
const serverRegions = {
  africa: ['ke-nrb', 'rw-kgl', 'tz-dar', 'za-jhb', 'eg-cai', 'ng-los', 'gh-acc'],
  europe: ['nl-ams', 'de-fra', 'gb-lon', 'ch-zur'],
  americas: ['us-nyc', 'us-lax', 'ca-tor'],
  cdn: ['cdn-cloudflare', 'cdn-google', 'cdn-azure']
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

let currentRegion = 'africa';
let connectionTimer = null;

// DOM Elements
const statusCard = document.getElementById('statusCard');
const connectBtn = document.getElementById('connectBtn');
const quickConnectBtn = document.getElementById('quickConnectBtn');
const statusText = document.getElementById('statusText');
const statusMessage = document.getElementById('statusMessage');
const statusDot = document.getElementById('statusDot');
const btnText = document.getElementById('btnText');
const ipAddress = document.getElementById('ipAddress');
const ipCard = document.getElementById('ipCard');
const locationText = document.getElementById('locationText');
const serverList = document.getElementById('serverList');
const connectionTime = document.getElementById('connectionTime');
const moneySaved = document.getElementById('moneySaved');
const latencyValue = document.getElementById('latencyValue');
const qualityIndicator = document.getElementById('qualityIndicator');
const settingsBtn = document.getElementById('settingsBtn');
const settingsPanel = document.getElementById('settingsPanel');
const backBtn = document.getElementById('backBtn');

// Initialize
document.addEventListener('DOMContentLoaded', async () => {
  await loadState();
  setupEventListeners();
  setupIPCListeners();
  updateUI();
  fetchIP();
});

// Load state from main process
async function loadState() {
  try {
    const response = await window.freedomVPN.getState();
    state.servers = response.servers || {};
    state.settings = response.settings || {};
    
    if (response.state) {
      state.isConnected = response.state.isConnected;
      state.currentServer = response.state.currentServer;
      state.startTime = response.state.startTime;
    }
  } catch (error) {
    console.error('Failed to load state:', error);
  }
}

// Setup event listeners
function setupEventListeners() {
  // Window controls
  document.getElementById('minimizeBtn').addEventListener('click', () => {
    window.freedomVPN.minimize();
  });
  
  document.getElementById('closeBtn').addEventListener('click', () => {
    window.freedomVPN.close();
  });
  
  // Connect buttons
  connectBtn.addEventListener('click', toggleConnection);
  quickConnectBtn.addEventListener('click', quickConnect);
  
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
  
  // Refresh IP
  document.getElementById('refreshIpBtn').addEventListener('click', fetchIP);
  
  // Settings toggles
  setupSettingsListeners();
  
  // Force failover
  document.getElementById('forceFailoverBtn').addEventListener('click', async () => {
    if (state.isConnected) {
      showToast('Switching to another server...');
      await window.freedomVPN.forceFailover();
    }
  });
}

// Setup settings toggle listeners
function setupSettingsListeners() {
  const toggles = {
    autoConnectToggle: 'autoConnect',
    killSwitchToggle: 'killSwitch',
    leakProtectionToggle: 'leakProtection',
    stealthToggle: 'stealthMode',
    preferAfricanToggle: 'preferAfrican'
  };
  
  Object.entries(toggles).forEach(([id, setting]) => {
    const el = document.getElementById(id);
    if (el) {
      el.checked = state.settings[setting] !== false;
      el.addEventListener('change', (e) => {
        state.settings[setting] = e.target.checked;
        saveSettings();
      });
    }
  });
  
  const langSelect = document.getElementById('languageSelect');
  if (langSelect) {
    langSelect.value = state.settings.language || 'en';
    langSelect.addEventListener('change', (e) => {
      state.settings.language = e.target.value;
      saveSettings();
    });
  }
}

// Setup IPC listeners for updates from main process
function setupIPCListeners() {
  window.freedomVPN.onConnectionState((data) => {
    state.isConnected = data.connected;
    state.currentServer = data.server;
    
    if (data.connected) {
      state.startTime = Date.now();
      startConnectionTimer();
      showToast(`Connected to ${data.server?.city}! 🛡️`);
    } else {
      stopConnectionTimer();
    }
    
    updateUI();
    fetchIP();
  });
  
  window.freedomVPN.onHealthUpdate((health) => {
    latencyValue.textContent = `${health.latency}ms`;
    qualityIndicator.className = `quality-indicator quality-${health.quality}`;
    qualityIndicator.textContent = health.quality.charAt(0).toUpperCase() + health.quality.slice(1);
  });
  
  window.freedomVPN.onStatsUpdate((stats) => {
    const ugx = stats.moneySaved || 0;
    moneySaved.textContent = `${ugx.toLocaleString()} UGX`;
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

// Connect to selected server
async function connect() {
  const selectedServerId = state.settings.selectedServer || 'ke-nrb';
  
  state.isConnecting = true;
  updateUI();
  
  try {
    const result = await window.freedomVPN.connect(selectedServerId);
    state.isConnecting = false;
    
    if (result.success) {
      state.isConnected = true;
      state.currentServer = result.state?.currentServer;
      state.startTime = Date.now();
      startConnectionTimer();
      showToast(`Connected to ${state.currentServer?.city}! 🛡️`);
    } else {
      showToast('Connection failed. Try another server.');
    }
  } catch (error) {
    state.isConnecting = false;
    showToast('Connection error: ' + error.message);
  }
  
  updateUI();
  setTimeout(fetchIP, 2000);
}

// Quick connect to best server
async function quickConnect() {
  if (state.isConnecting) return;
  
  state.isConnecting = true;
  updateUI();
  
  try {
    const result = await window.freedomVPN.connectBest();
    state.isConnecting = false;
    
    if (result.success) {
      state.isConnected = true;
      state.currentServer = result.state?.currentServer;
      state.startTime = Date.now();
      startConnectionTimer();
      showToast(`Connected to best server: ${state.currentServer?.city}! 🚀`);
    }
  } catch (error) {
    state.isConnecting = false;
    showToast('Quick connect failed');
  }
  
  updateUI();
  setTimeout(fetchIP, 2000);
}

// Disconnect
async function disconnect() {
  try {
    await window.freedomVPN.disconnect();
    state.isConnected = false;
    state.currentServer = null;
    stopConnectionTimer();
  } catch (error) {
    showToast('Disconnect error');
  }
  
  updateUI();
  setTimeout(fetchIP, 1000);
}

// Select server
function selectServer(serverId) {
  state.settings.selectedServer = serverId;
  saveSettings();
  renderServers();
  
  if (state.isConnected) {
    disconnect().then(() => connect());
  }
}

// Fetch current IP
async function fetchIP() {
  ipAddress.textContent = 'Detecting...';
  locationText.textContent = 'Detecting location...';
  
  try {
    const response = await fetch('https://ipapi.co/json/');
    const data = await response.json();
    
    ipAddress.textContent = data.ip;
    locationText.textContent = `${data.city}, ${data.country_name}`;
    
    if (state.isConnected) {
      ipCard.classList.add('protected');
    } else {
      ipCard.classList.remove('protected');
    }
  } catch (error) {
    ipAddress.textContent = 'Unable to detect';
    locationText.textContent = 'Check connection';
  }
}

// Update UI based on state
function updateUI() {
  statusCard.classList.remove('connected', 'connecting');
  
  if (state.isConnecting) {
    statusCard.classList.add('connecting');
    statusText.textContent = 'Connecting...';
    btnText.textContent = 'Connecting...';
    statusMessage.textContent = '';
    statusDot.style.background = '#f59e0b';
  } else if (state.isConnected) {
    statusCard.classList.add('connected');
    statusText.textContent = `Connected to ${state.currentServer?.city || 'VPN'}`;
    btnText.textContent = 'Disconnect';
    statusMessage.textContent = '✓ Your connection is protected';
    statusDot.style.background = '#22c55e';
    ipCard.classList.add('protected');
  } else {
    statusText.textContent = 'Not Connected';
    btnText.textContent = 'Connect';
    statusMessage.textContent = '⚠ Your connection is not protected';
    statusDot.style.background = '#ef4444';
    ipCard.classList.remove('protected');
  }
  
  renderServers();
}

// Render server list
function renderServers() {
  const serverIds = serverRegions[currentRegion] || [];
  
  serverList.innerHTML = serverIds.map(id => {
    const server = state.servers[id];
    if (!server) return '';
    
    const isSelected = state.settings.selectedServer === id;
    const isConnected = state.isConnected && state.currentServer?.id === id;
    
    const badge = server.isCDN 
      ? '<span class="badge cdn">CDN</span>'
      : server.isAfrican 
        ? '<span class="badge african">🌍</span>'
        : '';
    
    return `
      <div class="server-item ${isSelected ? 'selected' : ''} ${isConnected ? 'connected' : ''}" 
           onclick="selectServer('${id}')">
        <span class="server-flag">${server.flag}</span>
        <div class="server-info">
          <span class="server-name">${server.country}</span>
          <span class="server-city">${server.city} ${badge}</span>
        </div>
        <span class="server-status">${isConnected ? '✓' : ''}</span>
      </div>
    `;
  }).join('');
}

// Make selectServer available globally
window.selectServer = selectServer;

// Connection timer
function startConnectionTimer() {
  stopConnectionTimer();
  
  connectionTimer = setInterval(() => {
    if (state.startTime) {
      const elapsed = Math.floor((Date.now() - state.startTime) / 1000);
      connectionTime.textContent = formatDuration(elapsed);
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

function formatDuration(seconds) {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  return `${pad(h)}:${pad(m)}:${pad(s)}`;
}

function pad(n) {
  return n.toString().padStart(2, '0');
}

// Save settings
async function saveSettings() {
  try {
    await window.freedomVPN.saveSettings(state.settings);
  } catch (error) {
    console.error('Failed to save settings:', error);
  }
}

// Toast notification
function showToast(message) {
  const existingToast = document.querySelector('.toast');
  if (existingToast) existingToast.remove();
  
  const toast = document.createElement('div');
  toast.className = 'toast';
  toast.textContent = message;
  document.body.appendChild(toast);
  
  setTimeout(() => toast.remove(), 3000);
}

console.log('[FreedomVPN] Renderer loaded 🛡️');
