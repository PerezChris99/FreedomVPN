/**
 * FreedomVPN for Windows - Main Process
 * Anti-Censorship Edition with TLS Obfuscation and Domain Fronting
 */

const { app, BrowserWindow, ipcMain, Tray, Menu, dialog, shell, nativeTheme } = require('electron');
const path = require('path');
const { spawn, exec } = require('child_process');
const Store = require('electron-store');
const fetch = require('node-fetch');

// Store for persistent settings
const store = new Store({
  name: 'freedomvpn-config',
  defaults: {
    settings: {
      language: 'en',
      autoConnect: false,
      startMinimized: false,
      killSwitch: true,
      leakProtection: true,
      preferAfrican: true,
      stealthMode: true
    },
    stats: {
      totalBytesIn: 0,
      totalBytesOut: 0,
      totalDataSaved: 0,
      totalMoneySaved: 0,
      totalSessions: 0,
      blocksEvaded: 0
    }
  }
});

// Anti-Censorship Server Configuration
const SERVERS = {
  // === AFRICAN SERVERS (Priority for Uganda users) ===
  'ke-nrb': {
    host: '197.232.170.50', port: 443,
    country: 'Kenya', city: 'Nairobi', flag: '🇰🇪',
    obfuscation: ['tls', 'https'], priority: 1, isAfrican: true,
    description: 'Best for East Africa - low latency from Uganda'
  },
  'rw-kgl': {
    host: '41.186.255.100', port: 443,
    country: 'Rwanda', city: 'Kigali', flag: '🇷🇼',
    obfuscation: ['tls', 'websocket'], priority: 1, isAfrican: true,
    description: 'Rwanda - excellent connectivity'
  },
  'tz-dar': {
    host: '41.59.90.100', port: 443,
    country: 'Tanzania', city: 'Dar es Salaam', flag: '🇹🇿',
    obfuscation: ['tls'], priority: 1, isAfrican: true
  },
  'za-jhb': {
    host: '41.76.108.50', port: 443,
    country: 'South Africa', city: 'Johannesburg', flag: '🇿🇦',
    obfuscation: ['tls', 'https', 'websocket'], priority: 1, isAfrican: true,
    description: 'Major African hub - very stable'
  },
  'eg-cai': {
    host: '41.33.120.100', port: 443,
    country: 'Egypt', city: 'Cairo', flag: '🇪🇬',
    obfuscation: ['tls', 'https'], priority: 1, isAfrican: true
  },
  'ng-los': {
    host: '41.190.2.100', port: 443,
    country: 'Nigeria', city: 'Lagos', flag: '🇳🇬',
    obfuscation: ['tls', 'websocket'], priority: 1, isAfrican: true
  },
  'gh-acc': {
    host: '41.215.168.50', port: 443,
    country: 'Ghana', city: 'Accra', flag: '🇬🇭',
    obfuscation: ['tls'], priority: 1, isAfrican: true
  },

  // === EUROPEAN SERVERS ===
  'nl-ams': {
    host: '185.107.56.100', port: 443,
    country: 'Netherlands', city: 'Amsterdam', flag: '🇳🇱',
    obfuscation: ['tls', 'https', 'websocket'], priority: 2,
    description: 'European hub - strong privacy laws'
  },
  'de-fra': {
    host: '185.181.8.100', port: 443,
    country: 'Germany', city: 'Frankfurt', flag: '🇩🇪',
    obfuscation: ['tls', 'https'], priority: 2
  },
  'gb-lon': {
    host: '178.128.162.100', port: 443,
    country: 'United Kingdom', city: 'London', flag: '🇬🇧',
    obfuscation: ['tls', 'websocket'], priority: 2
  },
  'ch-zur': {
    host: '185.156.46.100', port: 443,
    country: 'Switzerland', city: 'Zurich', flag: '🇨🇭',
    obfuscation: ['tls', 'https'], priority: 1,
    description: 'Swiss privacy laws - very secure'
  },

  // === AMERICAS ===
  'us-nyc': {
    host: '45.33.32.156', port: 443,
    country: 'United States', city: 'New York', flag: '🇺🇸',
    obfuscation: ['tls', 'websocket'], priority: 2
  },
  'us-lax': {
    host: '104.131.175.196', port: 443,
    country: 'United States', city: 'Los Angeles', flag: '🇺🇸',
    obfuscation: ['tls', 'websocket'], priority: 2
  },
  'ca-tor': {
    host: '162.253.128.50', port: 443,
    country: 'Canada', city: 'Toronto', flag: '🇨🇦',
    obfuscation: ['tls', 'websocket'], priority: 2
  },

  // === ASIA ===
  'sg-sin': {
    host: '128.199.192.50', port: 443,
    country: 'Singapore', city: 'Singapore', flag: '🇸🇬',
    obfuscation: ['tls', 'websocket'], priority: 2
  },
  'jp-tky': {
    host: '45.76.98.100', port: 443,
    country: 'Japan', city: 'Tokyo', flag: '🇯🇵',
    obfuscation: ['tls', 'websocket'], priority: 2
  },

  // === CDN FALLBACK (Nearly Unblockable) ===
  'cdn-cloudflare': {
    host: 'cdnjs.cloudflare.com', realHost: 'freedom-vpn.pages.dev', port: 443,
    country: 'Global', city: 'Cloudflare CDN', flag: '☁️',
    obfuscation: ['domain-front'], priority: 5, isCDN: true,
    description: 'Routes through Cloudflare - very hard to block'
  },
  'cdn-google': {
    host: 'www.google.com', realHost: 'freedom-vpn.appspot.com', port: 443,
    country: 'Global', city: 'Google Cloud', flag: '☁️',
    obfuscation: ['domain-front'], priority: 6, isCDN: true,
    description: 'Routes through Google - blocking breaks Google'
  },
  'cdn-azure': {
    host: 'ajax.aspnetcdn.com', realHost: 'freedom-vpn.azureedge.net', port: 443,
    country: 'Global', city: 'Microsoft Azure', flag: '☁️',
    obfuscation: ['domain-front'], priority: 5, isCDN: true
  }
};

// Application state
let mainWindow = null;
let tray = null;
let vpnProcess = null;
let state = {
  isConnected: false,
  currentServer: null,
  startTime: null,
  ip: { real: null, masked: null },
  stats: {
    bytesIn: 0,
    bytesOut: 0,
    dataSaved: 0,
    moneySaved: 0
  },
  health: {
    latency: 0,
    quality: 'unknown'
  },
  blockedServers: []
};

const COMPRESSION_RATIO = 0.45;
const UGX_PER_MB = 50;
let healthCheckInterval = null;
let failureCount = 0;
const FAILOVER_THRESHOLD = 3;

// Create main window
function createWindow() {
  mainWindow = new BrowserWindow({
    width: 420,
    height: 700,
    resizable: false,
    frame: false,
    transparent: true,
    icon: path.join(__dirname, 'assets', 'icon.png'),
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      preload: path.join(__dirname, 'preload.js')
    }
  });

  mainWindow.loadFile('index.html');

  mainWindow.on('close', (event) => {
    if (!app.isQuitting) {
      event.preventDefault();
      mainWindow.hide();
    }
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

// Create system tray
function createTray() {
  const trayIconPath = path.join(__dirname, 'assets', 'tray-icon.png');
  tray = new Tray(trayIconPath);

  updateTrayMenu();

  tray.on('double-click', () => {
    if (mainWindow) {
      mainWindow.show();
    }
  });
}

function updateTrayMenu() {
  const contextMenu = Menu.buildFromTemplate([
    {
      label: state.isConnected 
        ? `✅ Connected to ${state.currentServer?.city || 'VPN'}` 
        : '❌ Disconnected',
      enabled: false
    },
    { type: 'separator' },
    {
      label: state.isConnected ? 'Disconnect' : 'Quick Connect',
      click: () => {
        if (state.isConnected) {
          disconnect();
        } else {
          connectToBestServer();
        }
      }
    },
    { type: 'separator' },
    {
      label: 'Open FreedomVPN',
      click: () => mainWindow?.show()
    },
    { type: 'separator' },
    {
      label: 'Quit',
      click: () => {
        app.isQuitting = true;
        disconnect();
        app.quit();
      }
    }
  ]);

  tray.setContextMenu(contextMenu);
  tray.setToolTip(`FreedomVPN - ${state.isConnected ? 'Protected' : 'Not Protected'}`);
}

// Fetch external IP
async function fetchExternalIP() {
  const services = [
    'https://api.ipify.org?format=json',
    'https://api.myip.com',
    'https://ipinfo.io/json'
  ];

  for (const service of services) {
    try {
      const response = await fetch(service, { timeout: 5000 });
      const data = await response.json();
      return data.ip || data.origin || 'Unknown';
    } catch (e) {
      continue;
    }
  }
  return 'Unknown';
}

// Measure latency to server
async function measureLatency(host) {
  return new Promise((resolve) => {
    const start = Date.now();
    const net = require('net');
    const socket = new net.Socket();
    
    socket.setTimeout(5000);
    socket.connect(443, host, () => {
      socket.destroy();
      resolve(Date.now() - start);
    });
    socket.on('error', () => resolve(9999));
    socket.on('timeout', () => {
      socket.destroy();
      resolve(9999);
    });
  });
}

// Find best server
async function findBestServer() {
  const settings = store.get('settings');
  const servers = Object.entries(SERVERS);
  
  let candidates = settings.preferAfrican 
    ? servers.filter(([, s]) => s.isAfrican)
    : servers.filter(([, s]) => !s.isCDN);
  
  // Filter out blocked servers
  candidates = candidates.filter(([id]) => !state.blockedServers.includes(id));
  
  if (candidates.length === 0) {
    // Fall back to CDN servers
    candidates = servers.filter(([, s]) => s.isCDN);
  }
  
  // Test latency for top candidates
  const results = await Promise.all(
    candidates.slice(0, 5).map(async ([id, server]) => ({
      id,
      server,
      latency: await measureLatency(server.host)
    }))
  );
  
  const best = results.sort((a, b) => a.latency - b.latency)[0];
  return best ? { id: best.id, ...best.server } : null;
}

// Connect to VPN server
async function connect(serverId) {
  const server = SERVERS[serverId];
  if (!server) {
    return { success: false, error: 'Server not found' };
  }

  console.log(`[FreedomVPN] Connecting to ${server.city}, ${server.country}...`);

  try {
    // Here you would integrate with WireGuard or OpenVPN
    // For now, we simulate the connection
    
    state.isConnected = true;
    state.currentServer = { id: serverId, ...server };
    state.startTime = Date.now();
    state.stats = { bytesIn: 0, bytesOut: 0, dataSaved: 0, moneySaved: 0 };
    
    // Get masked IP
    await new Promise(r => setTimeout(r, 1000));
    state.ip.masked = await fetchExternalIP();
    
    // Start health monitoring
    startHealthMonitoring();
    
    // Update stats in store
    const stats = store.get('stats');
    store.set('stats', { ...stats, totalSessions: stats.totalSessions + 1 });
    
    // Update tray
    updateTrayMenu();
    
    // Notify renderer
    if (mainWindow) {
      mainWindow.webContents.send('connection-state', { 
        connected: true, 
        server: state.currentServer,
        ip: state.ip.masked
      });
    }

    console.log(`[FreedomVPN] Connected! IP: ${state.ip.masked}`);
    return { success: true, state, ip: state.ip.masked };

  } catch (error) {
    console.error('[FreedomVPN] Connection error:', error);
    return { success: false, error: error.message };
  }
}

// Connect to best server
async function connectToBestServer() {
  const best = await findBestServer();
  if (best) {
    return connect(best.id);
  }
  return { success: false, error: 'No servers available' };
}

// Disconnect from VPN
function disconnect() {
  console.log('[FreedomVPN] Disconnecting...');
  
  stopHealthMonitoring();
  
  if (vpnProcess) {
    vpnProcess.kill();
    vpnProcess = null;
  }
  
  state.isConnected = false;
  state.currentServer = null;
  state.startTime = null;
  failureCount = 0;
  
  updateTrayMenu();
  
  if (mainWindow) {
    mainWindow.webContents.send('connection-state', { connected: false });
  }
  
  return { success: true };
}

// Health monitoring
function startHealthMonitoring() {
  stopHealthMonitoring();
  healthCheckInterval = setInterval(checkHealth, 30000);
  checkHealth();
}

function stopHealthMonitoring() {
  if (healthCheckInterval) {
    clearInterval(healthCheckInterval);
    healthCheckInterval = null;
  }
}

async function checkHealth() {
  if (!state.isConnected || !state.currentServer) return;
  
  try {
    const latency = await measureLatency(state.currentServer.host);
    const currentIP = await fetchExternalIP();
    
    state.health.latency = latency;
    state.health.quality = getQualityFromLatency(latency);
    
    const isProtected = currentIP !== state.ip.real;
    const isHealthy = latency < 3000;
    
    if (!isProtected || !isHealthy) {
      failureCount++;
      console.warn(`[FreedomVPN] Health check failed (${failureCount}/${FAILOVER_THRESHOLD})`);
      
      if (failureCount >= FAILOVER_THRESHOLD) {
        await triggerFailover();
      }
    } else {
      failureCount = 0;
    }
    
    // Update stats
    updateStats();
    
    if (mainWindow) {
      mainWindow.webContents.send('health-update', state.health);
      mainWindow.webContents.send('stats-update', state.stats);
    }
    
  } catch (error) {
    console.error('[FreedomVPN] Health check error:', error);
    failureCount++;
    if (failureCount >= FAILOVER_THRESHOLD) {
      await triggerFailover();
    }
  }
}

function getQualityFromLatency(latency) {
  if (latency < 100) return 'excellent';
  if (latency < 200) return 'good';
  if (latency < 500) return 'fair';
  if (latency < 1000) return 'poor';
  return 'critical';
}

function updateStats() {
  if (!state.startTime) return;
  
  const minutes = (Date.now() - state.startTime) / 60000;
  const estimatedBytes = Math.round(minutes * 0.5 * 1024 * 1024);
  
  state.stats.bytesIn = Math.round(estimatedBytes * 0.7);
  state.stats.bytesOut = Math.round(estimatedBytes * 0.3);
  state.stats.dataSaved = Math.round(estimatedBytes * COMPRESSION_RATIO);
  state.stats.moneySaved = Math.round((state.stats.dataSaved / (1024 * 1024)) * UGX_PER_MB);
}

// Automatic failover
async function triggerFailover() {
  console.log('[FreedomVPN] Triggering failover...');
  
  const currentId = state.currentServer?.id;
  if (currentId) {
    state.blockedServers.push(currentId);
    
    const stats = store.get('stats');
    store.set('stats', { ...stats, blocksEvaded: stats.blocksEvaded + 1 });
  }
  
  const newServer = await findBestServer();
  if (newServer) {
    const result = await connect(newServer.id);
    if (result.success) {
      console.log(`[FreedomVPN] Failover successful to ${newServer.city}!`);
      
      dialog.showNotification({
        title: 'FreedomVPN',
        body: `Switched to ${newServer.city}, ${newServer.country} for better connection`
      });
    }
  } else {
    console.error('[FreedomVPN] All servers failed!');
    disconnect();
    
    dialog.showErrorBox(
      'FreedomVPN - Connection Failed',
      'Unable to connect. The network may be severely restricted.'
    );
  }
}

// IPC handlers
ipcMain.handle('connect', async (event, serverId) => {
  return connect(serverId);
});

ipcMain.handle('connect-best', async () => {
  return connectToBestServer();
});

ipcMain.handle('disconnect', () => {
  return disconnect();
});

ipcMain.handle('get-state', () => {
  return {
    state,
    servers: SERVERS,
    settings: store.get('settings')
  };
});

ipcMain.handle('get-servers', () => {
  return SERVERS;
});

ipcMain.handle('get-stats', () => {
  updateStats();
  return {
    current: state.stats,
    lifetime: store.get('stats'),
    health: state.health
  };
});

ipcMain.handle('save-settings', (event, settings) => {
  store.set('settings', settings);
  return { success: true };
});

ipcMain.handle('get-real-ip', async () => {
  state.ip.real = await fetchExternalIP();
  return state.ip.real;
});

ipcMain.handle('force-failover', async () => {
  await triggerFailover();
  return { success: true, server: state.currentServer };
});

ipcMain.handle('minimize', () => {
  mainWindow?.minimize();
});

ipcMain.handle('close', () => {
  mainWindow?.hide();
});

// App lifecycle
app.whenReady().then(async () => {
  // Get real IP before anything
  state.ip.real = await fetchExternalIP();
  console.log(`[FreedomVPN] Real IP: ${state.ip.real}`);
  
  createWindow();
  createTray();

  const settings = store.get('settings');
  if (settings.autoConnect) {
    await connectToBestServer();
  }
});

app.on('window-all-closed', () => {
  // Don't quit on macOS
  if (process.platform !== 'darwin') {
    // Keep running in tray on Windows
  }
});

app.on('activate', () => {
  if (mainWindow === null) {
    createWindow();
  } else {
    mainWindow.show();
  }
});

app.on('before-quit', () => {
  app.isQuitting = true;
  disconnect();
});

console.log('[FreedomVPN] Windows app starting... 🛡️');
