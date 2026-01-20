/**
 * FreedomVPN Desktop - Renderer Process
 * Modern desktop UI with real VPN Gate integration
 */

// State
let state = {
  isConnected: false,
  isConnecting: false,
  currentServer: null,
  realIP: null,
  vpnIP: null,
  startTime: null,
  servers: {}
};

let connectionTimer = null;

// ============= INITIALIZATION =============
document.addEventListener('DOMContentLoaded', async () => {
  setupWindowControls();
  setupNavigation();
  setupEventListeners();
  setupIPCListeners();
  await loadInitialState();
  await fetchRealIP();
  renderServers();
});

// ============= WINDOW CONTROLS =============
function setupWindowControls() {
  document.getElementById('minimizeBtn').addEventListener('click', () => {
    window.freedomVPN.minimize();
  });
  
  document.getElementById('maximizeBtn')?.addEventListener('click', () => {
    window.freedomVPN.maximize();
  });
  
  document.getElementById('closeBtn').addEventListener('click', () => {
    window.freedomVPN.close();
  });
}

// ============= NAVIGATION =============
function setupNavigation() {
  document.querySelectorAll('.nav-item').forEach(item => {
    item.addEventListener('click', () => {
      const page = item.dataset.page;
      
      // Update nav
      document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
      item.classList.add('active');
      
      // Update pages
      document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
      document.getElementById(`page-${page}`).classList.add('active');
    });
  });
}

// ============= EVENT LISTENERS =============
function setupEventListeners() {
  // Connect button
  document.getElementById('connectBtn').addEventListener('click', async () => {
    if (state.isConnected) {
      await disconnect();
    } else if (state.isConnecting) {
      // Cancel
      state.isConnecting = false;
      updateConnectionUI();
    } else {
      await quickConnect();
    }
  });
  
  // Refresh IP
  document.getElementById('refreshIpBtn').addEventListener('click', fetchRealIP);
  
  // Settings toggles
  setupSettingsListeners();
}

// ============= IPC LISTENERS =============
function setupIPCListeners() {
  if (!window.freedomVPN) {
    console.warn('freedomVPN IPC not available');
    return;
  }
  
  window.freedomVPN.onConnectionState((event, data) => {
    state.isConnected = data.connected;
    state.currentServer = data.server;
    state.vpnIP = data.ip;
    if (data.connected) {
      state.startTime = Date.now();
      startConnectionTimer();
    } else {
      stopConnectionTimer();
    }
    updateConnectionUI();
  });
  
  window.freedomVPN.onHealthUpdate?.((event, data) => {
    if (data.latency) {
      document.getElementById('currentLatency').textContent = `${data.latency} ms`;
    }
  });
}

// ============= STATE MANAGEMENT =============
async function loadInitialState() {
  try {
    if (window.freedomVPN?.getState) {
      const response = await window.freedomVPN.getState();
      state.servers = response.servers || {};
      if (response.state?.isConnected) {
        state.isConnected = true;
        state.currentServer = response.state.currentServer;
        state.startTime = response.state.startTime;
        startConnectionTimer();
      }
    }
  } catch (e) {
    console.log('Loading default state');
  }
  updateConnectionUI();
}

// ============= CONNECTION =============
async function quickConnect() {
  state.isConnecting = true;
  updateConnectionUI();
  
  try {
    const result = await window.freedomVPN.connect();
    
    if (result.success) {
      state.isConnected = true;
      state.currentServer = result.state?.currentServer;
      state.vpnIP = result.ip;
      state.startTime = Date.now();
      startConnectionTimer();
      showNotification('Connected', `Protected via ${state.currentServer?.city || 'VPN'}`);
    } else {
      showNotification('Connection Failed', result.error || 'Unknown error', 'error');
    }
  } catch (error) {
    showNotification('Connection Failed', error.message, 'error');
  }
  
  state.isConnecting = false;
  updateConnectionUI();
}

async function connectToServer(serverId) {
  state.isConnecting = true;
  updateConnectionUI();
  
  try {
    const result = await window.freedomVPN.connectToServer(serverId);
    
    if (result.success) {
      state.isConnected = true;
      state.currentServer = result.state?.currentServer;
      state.vpnIP = result.ip;
      state.startTime = Date.now();
      startConnectionTimer();
    } else {
      showNotification('Connection Failed', result.error, 'error');
    }
  } catch (error) {
    showNotification('Connection Failed', error.message, 'error');
  }
  
  state.isConnecting = false;
  updateConnectionUI();
}

async function disconnect() {
  try {
    await window.freedomVPN.disconnect();
    state.isConnected = false;
    state.currentServer = null;
    state.vpnIP = null;
    stopConnectionTimer();
    showNotification('Disconnected', 'Your connection is no longer protected');
  } catch (error) {
    console.error('Disconnect error:', error);
  }
  updateConnectionUI();
}

// ============= UI UPDATES =============
function updateConnectionUI() {
  const connectBtn = document.getElementById('connectBtn');
  const connectionRing = document.getElementById('connectionRing');
  const connectionIcon = document.getElementById('connectionIcon');
  const connectionStatus = document.getElementById('connectionStatusText');
  const globalStatus = document.getElementById('globalStatus');
  
  if (state.isConnecting) {
    connectBtn.innerHTML = '<span>⏳ Connecting...</span>';
    connectBtn.disabled = true;
    connectionRing.classList.remove('connected');
    connectionStatus.textContent = 'Connecting...';
    connectionIcon.textContent = '⏳';
  } else if (state.isConnected) {
    connectBtn.innerHTML = '<span>⏹️ Disconnect</span>';
    connectBtn.classList.add('danger');
    connectBtn.disabled = false;
    connectionRing.classList.add('connected');
    connectionStatus.textContent = 'Connected';
    connectionIcon.textContent = '✅';
    
    // Update server info
    if (state.currentServer) {
      document.getElementById('serverName').textContent = 
        `${state.currentServer.flag || ''} ${state.currentServer.country} - ${state.currentServer.city}`;
    }
    
    if (state.vpnIP) {
      document.getElementById('currentIP').textContent = state.vpnIP;
      document.getElementById('vpnIP').textContent = state.vpnIP;
      document.getElementById('vpnLocation').textContent = 
        state.currentServer ? `${state.currentServer.city}, ${state.currentServer.country}` : '--';
    }
    
    // Global status
    globalStatus.innerHTML = '<span class="status-dot connected"></span><span>Protected</span>';
  } else {
    connectBtn.innerHTML = '<span>⚡ Quick Connect</span>';
    connectBtn.classList.remove('danger');
    connectBtn.disabled = false;
    connectionRing.classList.remove('connected');
    connectionStatus.textContent = 'Disconnected';
    connectionIcon.textContent = '🛡️';
    
    document.getElementById('serverName').textContent = 'Not selected';
    document.getElementById('currentIP').textContent = '---';
    document.getElementById('vpnIP').textContent = 'Not Connected';
    document.getElementById('vpnLocation').textContent = '--';
    
    // Global status
    globalStatus.innerHTML = '<span class="status-dot disconnected"></span><span>Not Protected</span>';
  }
}

// ============= IP DETECTION =============
async function fetchRealIP() {
  const realIPEl = document.getElementById('realIP');
  const realLocationEl = document.getElementById('realLocation');
  
  realIPEl.textContent = 'Detecting...';
  
  const services = [
    { url: 'https://api.ipify.org?format=json', parser: d => d.ip },
    { url: 'https://ipinfo.io/json', parser: d => d.ip },
    { url: 'https://api.myip.com', parser: d => d.ip }
  ];
  
  for (const service of services) {
    try {
      const response = await fetch(service.url, { timeout: 5000 });
      const data = await response.json();
      state.realIP = service.parser(data);
      realIPEl.textContent = state.realIP;
      
      // Try to get location
      if (data.country) {
        realLocationEl.textContent = `${data.city || ''} ${data.country}`.trim();
      } else {
        realLocationEl.textContent = 'Location unknown';
      }
      return;
    } catch (e) {
      continue;
    }
  }
  
  realIPEl.textContent = 'Unable to detect';
}

// ============= SERVERS =============
function renderServers() {
  const serverList = document.getElementById('serverList');
  const fullServerList = document.getElementById('fullServerList');
  
  const servers = [
    { id: 'ke-nrb', flag: '🇰🇪', country: 'Kenya', city: 'Nairobi', latency: 25 },
    { id: 'rw-kgl', flag: '🇷🇼', country: 'Rwanda', city: 'Kigali', latency: 20 },
    { id: 'za-jhb', flag: '🇿🇦', country: 'South Africa', city: 'Johannesburg', latency: 80 },
    { id: 'nl-ams', flag: '🇳🇱', country: 'Netherlands', city: 'Amsterdam', latency: 150 },
    { id: 'de-fra', flag: '🇩🇪', country: 'Germany', city: 'Frankfurt', latency: 155 },
    { id: 'us-nyc', flag: '🇺🇸', country: 'United States', city: 'New York', latency: 200 },
    { id: 'jp-tky', flag: '🇯🇵', country: 'Japan', city: 'Tokyo', latency: 220 },
    { id: 'sg-sin', flag: '🇸🇬', country: 'Singapore', city: 'Singapore', latency: 180 }
  ];
  
  // Render dashboard server list (quick select)
  if (serverList) {
    serverList.innerHTML = servers.slice(0, 5).map(server => `
      <div class="server-item" data-id="${server.id}">
        <span class="server-flag">${server.flag}</span>
        <div class="server-info">
          <span class="server-name">${server.country}</span>
          <span class="server-city">${server.city}</span>
        </div>
        <span class="server-latency">${server.latency} ms</span>
      </div>
    `).join('');
    
    // Add click handlers
    serverList.querySelectorAll('.server-item').forEach(item => {
      item.addEventListener('click', () => {
        connectToServer(item.dataset.id);
      });
    });
  }
  
  // Render full server list
  if (fullServerList) {
    fullServerList.innerHTML = servers.map(server => `
      <div class="server-item" data-id="${server.id}">
        <span class="server-flag">${server.flag}</span>
        <div class="server-info">
          <span class="server-name">${server.country}</span>
          <span class="server-city">${server.city}</span>
        </div>
        <span class="server-latency">${server.latency} ms</span>
      </div>
    `).join('');
    
    fullServerList.querySelectorAll('.server-item').forEach(item => {
      item.addEventListener('click', () => {
        connectToServer(item.dataset.id);
      });
    });
  }
}

// ============= TIMER =============
function startConnectionTimer() {
  stopConnectionTimer();
  connectionTimer = setInterval(() => {
    if (state.startTime) {
      const elapsed = Math.floor((Date.now() - state.startTime) / 1000);
      const hours = Math.floor(elapsed / 3600).toString().padStart(2, '0');
      const minutes = Math.floor((elapsed % 3600) / 60).toString().padStart(2, '0');
      const seconds = (elapsed % 60).toString().padStart(2, '0');
      
      document.getElementById('sessionTime').textContent = `${hours}:${minutes}:${seconds}`;
      
      // Simulate data usage
      const dataUsed = (elapsed * 0.05).toFixed(1);
      document.getElementById('dataUsed').textContent = `${dataUsed} MB`;
      document.getElementById('moneySaved').textContent = `${Math.floor(dataUsed * 25)} UGX`;
    }
  }, 1000);
}

function stopConnectionTimer() {
  if (connectionTimer) {
    clearInterval(connectionTimer);
    connectionTimer = null;
  }
  document.getElementById('sessionTime').textContent = '00:00:00';
}

// ============= SETTINGS =============
function setupSettingsListeners() {
  const toggleIds = ['killSwitch', 'leakProtection', 'preferAfrican', 'stealthMode'];
  
  toggleIds.forEach(id => {
    const el = document.getElementById(id);
    if (el) {
      el.addEventListener('change', async (e) => {
        if (window.freedomVPN?.updateSettings) {
          await window.freedomVPN.updateSettings({ [id]: e.target.checked });
        }
      });
    }
  });
}

// ============= NOTIFICATIONS =============
function showNotification(title, message, type = 'success') {
  // Could use system notifications
  console.log(`[${type.toUpperCase()}] ${title}: ${message}`);
}
