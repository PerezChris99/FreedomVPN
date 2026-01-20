/**
 * FreedomVPN for Windows - Preload Script
 * Exposes safe APIs to renderer process
 */

const { contextBridge, ipcRenderer } = require('electron');

// Expose protected methods that allow the renderer process
// to use ipcRenderer without exposing the entire object
contextBridge.exposeInMainWorld('freedomVPN', {
  // Connection methods
  connect: (serverId) => ipcRenderer.invoke('connect', serverId),
  connectBest: () => ipcRenderer.invoke('connect-best'),
  disconnect: () => ipcRenderer.invoke('disconnect'),
  
  // State and data
  getState: () => ipcRenderer.invoke('get-state'),
  getServers: () => ipcRenderer.invoke('get-servers'),
  getStats: () => ipcRenderer.invoke('get-stats'),
  getRealIP: () => ipcRenderer.invoke('get-real-ip'),
  
  // Enhanced IP Detection & Privacy
  getIPDetails: () => ipcRenderer.invoke('get-ip-details'),
  runPrivacyCheck: () => ipcRenderer.invoke('run-privacy-check'),
  generateVpnIP: (serverId) => ipcRenderer.invoke('generate-vpn-ip', serverId),
  
  // Settings
  saveSettings: (settings) => ipcRenderer.invoke('save-settings', settings),
  
  // Actions
  forceFailover: () => ipcRenderer.invoke('force-failover'),
  
  // Multi-Hop
  toggleMultiHop: (enabled) => ipcRenderer.invoke('toggle-multihop', enabled),
  getMultiHopState: () => ipcRenderer.invoke('get-multihop-state'),
  setMultiHopPreset: (preset) => ipcRenderer.invoke('set-multihop-preset', preset),
  
  // Window controls
  minimize: () => ipcRenderer.invoke('minimize'),
  close: () => ipcRenderer.invoke('close'),
  
  // Event listeners
  onConnectionState: (callback) => {
    ipcRenderer.on('connection-state', (event, data) => callback(data));
  },
  onHealthUpdate: (callback) => {
    ipcRenderer.on('health-update', (event, data) => callback(data));
  },
  onStatsUpdate: (callback) => {
    ipcRenderer.on('stats-update', (event, data) => callback(data));
  },
  onPrivacyUpdate: (callback) => {
    ipcRenderer.on('privacy-update', (event, data) => callback(data));
  },
  onMultiHopUpdate: (callback) => {
    ipcRenderer.on('multihop-chain-update', (event, data) => callback(data));
  },
  
  // Remove listeners
  removeAllListeners: (channel) => {
    ipcRenderer.removeAllListeners(channel);
  }
});
