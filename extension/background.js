// FreedomVPN Background Service Worker
// Handles proxy configuration and connection management

// Free proxy servers for demonstration (in production, use your own servers)
const PROXY_SERVERS = {
  // Africa
  'ug': { host: '41.210.141.217', port: 8080, country: 'Uganda', city: 'Kampala', flag: '🇺🇬' },
  'ke': { host: '197.232.39.196', port: 8080, country: 'Kenya', city: 'Nairobi', flag: '🇰🇪' },
  'za': { host: '196.41.85.146', port: 8080, country: 'South Africa', city: 'Johannesburg', flag: '🇿🇦' },
  'ng': { host: '197.210.217.130', port: 8080, country: 'Nigeria', city: 'Lagos', flag: '🇳🇬' },
  'eg': { host: '197.55.202.97', port: 8080, country: 'Egypt', city: 'Cairo', flag: '🇪🇬' },
  
  // Europe
  'nl': { host: '185.162.231.170', port: 80, country: 'Netherlands', city: 'Amsterdam', flag: '🇳🇱' },
  'de': { host: '185.220.101.33', port: 9443, country: 'Germany', city: 'Frankfurt', flag: '🇩🇪' },
  'gb': { host: '185.189.186.19', port: 8080, country: 'United Kingdom', city: 'London', flag: '🇬🇧' },
  'fr': { host: '185.230.91.16', port: 8080, country: 'France', city: 'Paris', flag: '🇫🇷' },
  
  // Americas
  'us': { host: '172.93.213.200', port: 80, country: 'United States', city: 'New York', flag: '🇺🇸' },
  'br': { host: '177.54.229.115', port: 9090, country: 'Brazil', city: 'São Paulo', flag: '🇧🇷' },
  
  // Asia
  'sg': { host: '103.86.50.168', port: 8080, country: 'Singapore', city: 'Singapore', flag: '🇸🇬' },
  'jp': { host: '103.73.65.116', port: 8080, country: 'Japan', city: 'Tokyo', flag: '🇯🇵' },
  'ae': { host: '185.203.116.33', port: 80, country: 'UAE', city: 'Dubai', flag: '🇦🇪' }
};

// State management
let connectionState = {
  isConnected: false,
  currentServer: null,
  startTime: null,
  dataUsed: 0
};

// Apply proxy configuration
function setProxy(serverId) {
  const server = PROXY_SERVERS[serverId];
  if (!server) {
    console.error('Server not found:', serverId);
    return false;
  }

  const config = {
    mode: "fixed_servers",
    rules: {
      singleProxy: {
        scheme: "http",
        host: server.host,
        port: server.port
      },
      bypassList: ["localhost", "127.0.0.1"]
    }
  };

  chrome.proxy.settings.set(
    { value: config, scope: 'regular' },
    () => {
      if (chrome.runtime.lastError) {
        console.error('Proxy error:', chrome.runtime.lastError);
        return;
      }
      
      connectionState.isConnected = true;
      connectionState.currentServer = { id: serverId, ...server };
      connectionState.startTime = Date.now();
      
      // Save state
      chrome.storage.local.set({ connectionState });
      
      // Update icon
      updateIcon(true);
      
      console.log(`Connected to ${server.country} (${server.host}:${server.port})`);
    }
  );

  return true;
}

// Clear proxy (disconnect)
function clearProxy() {
  chrome.proxy.settings.clear({ scope: 'regular' }, () => {
    connectionState.isConnected = false;
    connectionState.currentServer = null;
    connectionState.startTime = null;
    
    chrome.storage.local.set({ connectionState });
    updateIcon(false);
    
    console.log('Disconnected from VPN');
  });
}

// Update extension icon based on connection state
function updateIcon(connected) {
  const iconPath = connected ? 'icons/icon-connected' : 'icons/icon';
  
  chrome.action.setIcon({
    path: {
      "16": `${iconPath}16.png`,
      "32": `${iconPath}32.png`,
      "48": `${iconPath}48.png`,
      "128": `${iconPath}128.png`
    }
  }).catch(() => {
    // Fallback if connected icons don't exist
    chrome.action.setIcon({
      path: {
        "16": "icons/icon16.png",
        "32": "icons/icon32.png",
        "48": "icons/icon48.png",
        "128": "icons/icon128.png"
      }
    });
  });

  // Update badge
  chrome.action.setBadgeText({ text: connected ? 'ON' : '' });
  chrome.action.setBadgeBackgroundColor({ color: connected ? '#22c55e' : '#ef4444' });
}

// Message handler for popup communication
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  switch (message.action) {
    case 'connect':
      const success = setProxy(message.serverId);
      setTimeout(() => {
        sendResponse({ success, state: connectionState });
      }, 500);
      return true;
      
    case 'disconnect':
      clearProxy();
      setTimeout(() => {
        sendResponse({ success: true, state: connectionState });
      }, 300);
      return true;
      
    case 'getState':
      chrome.storage.local.get(['connectionState', 'settings'], (data) => {
        sendResponse({
          state: data.connectionState || connectionState,
          settings: data.settings || {},
          servers: PROXY_SERVERS
        });
      });
      return true;
      
    case 'getServers':
      sendResponse({ servers: PROXY_SERVERS });
      return true;
      
    case 'saveSettings':
      chrome.storage.local.set({ settings: message.settings }, () => {
        sendResponse({ success: true });
      });
      return true;
  }
});

// Initialize on install
chrome.runtime.onInstalled.addListener(() => {
  chrome.storage.local.set({
    connectionState: {
      isConnected: false,
      currentServer: null,
      startTime: null
    },
    settings: {
      language: 'en',
      compression: true,
      stealthMode: false,
      autoConnect: false,
      selectedServer: 'ug'
    }
  });
  
  updateIcon(false);
});

// Restore state on startup
chrome.runtime.onStartup.addListener(() => {
  chrome.storage.local.get(['connectionState', 'settings'], (data) => {
    if (data.connectionState?.isConnected && data.settings?.autoConnect) {
      setProxy(data.connectionState.currentServer?.id || 'ug');
    } else {
      updateIcon(false);
    }
  });
});

// Handle proxy errors
chrome.proxy.onProxyError.addListener((details) => {
  console.error('Proxy error:', details);
  // Try to reconnect or show error
});
