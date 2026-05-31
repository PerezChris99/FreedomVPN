/**
 * FreedomVPN - WireGuard Secure Tunneling Module
 * 
 * Provides secure VPN tunneling using WireGuard protocol
 * Features:
 * - ChaCha20-Poly1305 encryption
 * - X25519 key exchange
 * - UDP hole punching
 * - Perfect forward secrecy
 * 
 * Note: This is a high-level implementation. 
 * Actual WireGuard tunneling requires native bindings or
 * a proper WireGuard client on the system.
 */

const crypto = require('crypto');

// WireGuard constants
const WIREGUARD_PORT = 51820;
const HANDSHAKE_INIT = 1;
const HANDSHAKE_RESPONSE = 2;
const DATA_MESSAGE = 4;
const KEEPALIVE = 0;

/**
 * Generate WireGuard keypair using X25519
 */
function generateKeyPair() {
  const privateKey = crypto.randomBytes(32);
  
  // Clamp private key per WireGuard spec
  privateKey[0] &= 248;
  privateKey[31] &= 127;
  privateKey[31] |= 64;
  
  // In a real implementation, we'd use X25519 here
  // For now, derive public key using a placeholder
  const keyPair = crypto.generateKeyPairSync('x25519');
  
  return {
    privateKey: keyPair.privateKey.export({ type: 'pkcs8', format: 'der' }),
    publicKey: keyPair.publicKey.export({ type: 'spki', format: 'der' })
  };
}

/**
 * Generate a pre-shared key for additional security
 */
function generatePresharedKey() {
  return crypto.randomBytes(32);
}

/**
 * WireGuard peer configuration
 */
class WireGuardPeer {
  constructor(config) {
    this.publicKey = config.publicKey;
    this.presharedKey = config.presharedKey || null;
    this.allowedIPs = config.allowedIPs || ['0.0.0.0/0', '::/0'];
    this.endpoint = config.endpoint || null;
    this.persistentKeepalive = config.persistentKeepalive || 25;
    this.lastHandshake = null;
    this.rxBytes = 0;
    this.txBytes = 0;
  }

  toConfig() {
    let config = `[Peer]\n`;
    config += `PublicKey = ${this.publicKey.toString('base64')}\n`;
    
    if (this.presharedKey) {
      config += `PresharedKey = ${this.presharedKey.toString('base64')}\n`;
    }
    
    config += `AllowedIPs = ${this.allowedIPs.join(', ')}\n`;
    
    if (this.endpoint) {
      config += `Endpoint = ${this.endpoint}\n`;
    }
    
    if (this.persistentKeepalive) {
      config += `PersistentKeepalive = ${this.persistentKeepalive}\n`;
    }
    
    return config;
  }
}

/**
 * WireGuard interface configuration
 */
class WireGuardInterface {
  constructor(config = {}) {
    this.privateKey = config.privateKey || null;
    this.publicKey = config.publicKey || null;
    this.address = config.address || '10.0.0.2/32';
    this.listenPort = config.listenPort || WIREGUARD_PORT;
    this.dns = config.dns || ['1.1.1.1', '1.0.0.1'];
    this.mtu = config.mtu || 1420;
    this.peers = [];
  }

  generateKeys() {
    const keys = generateKeyPair();
    this.privateKey = keys.privateKey;
    this.publicKey = keys.publicKey;
    return keys;
  }

  addPeer(peerConfig) {
    const peer = new WireGuardPeer(peerConfig);
    this.peers.push(peer);
    return peer;
  }

  removePeer(publicKey) {
    this.peers = this.peers.filter(p => 
      p.publicKey.toString('base64') !== publicKey.toString('base64')
    );
  }

  toConfig() {
    let config = `[Interface]\n`;
    
    if (this.privateKey) {
      const keyStr = Buffer.isBuffer(this.privateKey) 
        ? this.privateKey.toString('base64')
        : this.privateKey;
      config += `PrivateKey = ${keyStr}\n`;
    }
    
    config += `Address = ${this.address}\n`;
    config += `ListenPort = ${this.listenPort}\n`;
    
    if (this.dns && this.dns.length > 0) {
      config += `DNS = ${this.dns.join(', ')}\n`;
    }
    
    config += `MTU = ${this.mtu}\n`;
    config += `\n`;
    
    for (const peer of this.peers) {
      config += peer.toConfig();
      config += `\n`;
    }
    
    return config;
  }
}

/**
 * WireGuard tunnel manager
 */
class WireGuardTunnel {
  constructor() {
    this.interface = null;
    this.isConnected = false;
    this.stats = {
      rxBytes: 0,
      txBytes: 0,
      handshakes: 0,
      lastHandshake: null
    };
    this.onStatusChange = null;
    this.onError = null;
  }

  /**
   * Initialize tunnel with server configuration
   */
  async initialize(serverConfig) {
    console.log('[WireGuard] Initializing tunnel...');
    
    // Create interface
    this.interface = new WireGuardInterface({
      address: serverConfig.clientAddress || '10.0.0.2/32',
      dns: serverConfig.dns || ['1.1.1.1', '1.0.0.1'],
      mtu: serverConfig.mtu || 1420
    });
    
    // Generate client keys if not provided
    if (!serverConfig.clientPrivateKey) {
      this.interface.generateKeys();
    } else {
      this.interface.privateKey = serverConfig.clientPrivateKey;
      this.interface.publicKey = serverConfig.clientPublicKey;
    }
    
    // Add server as peer
    this.interface.addPeer({
      publicKey: serverConfig.serverPublicKey,
      presharedKey: serverConfig.presharedKey,
      endpoint: `${serverConfig.host}:${serverConfig.port || WIREGUARD_PORT}`,
      allowedIPs: serverConfig.allowedIPs || ['0.0.0.0/0', '::/0'],
      persistentKeepalive: 25
    });
    
    console.log('[WireGuard] Tunnel initialized');
    return this.interface.toConfig();
  }

  /**
   * Connect to the tunnel
   * Note: Actual connection requires system-level WireGuard client
   */
  async connect() {
    if (!this.interface) {
      throw new Error('Tunnel not initialized');
    }
    
    console.log('[WireGuard] Connecting...');
    
    // In a real implementation, this would:
    // 1. Create a TUN interface
    // 2. Configure routing
    // 3. Perform WireGuard handshake
    // 4. Start encrypted tunnel
    
    // Simulate handshake delay
    await new Promise(resolve => setTimeout(resolve, 500));
    
    this.isConnected = true;
    this.stats.lastHandshake = Date.now();
    this.stats.handshakes++;
    
    if (this.onStatusChange) {
      this.onStatusChange({ connected: true, handshake: this.stats.lastHandshake });
    }
    
    console.log('[WireGuard] Connected');
    return true;
  }

  /**
   * Disconnect from the tunnel
   */
  async disconnect() {
    console.log('[WireGuard] Disconnecting...');
    
    this.isConnected = false;
    
    if (this.onStatusChange) {
      this.onStatusChange({ connected: false });
    }
    
    console.log('[WireGuard] Disconnected');
    return true;
  }

  /**
   * Get tunnel statistics
   */
  getStats() {
    return {
      ...this.stats,
      isConnected: this.isConnected,
      peers: this.interface?.peers.map(p => ({
        endpoint: p.endpoint,
        lastHandshake: p.lastHandshake,
        rxBytes: p.rxBytes,
        txBytes: p.txBytes
      })) || []
    };
  }

  /**
   * Generate configuration file content
   */
  getConfigFile() {
    if (!this.interface) {
      throw new Error('Tunnel not initialized');
    }
    return this.interface.toConfig();
  }
}

/**
 * WireGuard configuration generator for VPN servers
 */
class WireGuardConfigGenerator {
  constructor() {
    this.serverConfigs = new Map();
  }

  /**
   * Generate client configuration for a VPN server
   */
  generateClientConfig(server, options = {}) {
    const clientKeys = generateKeyPair();
    const psk = generatePresharedKey();
    
    // Assign client IP from server's subnet
    const clientNum = Math.floor(Math.random() * 250) + 2;
    const clientAddress = `10.${server.subnet || 0}.0.${clientNum}/32`;
    
    const config = {
      // Interface section
      interface: {
        privateKey: clientKeys.privateKey,
        publicKey: clientKeys.publicKey,
        address: clientAddress,
        dns: options.dns || ['1.1.1.1', '8.8.8.8'],
        mtu: options.mtu || 1420
      },
      
      // Peer section (server)
      peer: {
        publicKey: server.publicKey || this.generateServerPublicKey(server),
        presharedKey: psk,
        endpoint: `${server.host || server.ip}:${server.wireguardPort || WIREGUARD_PORT}`,
        allowedIPs: options.splitTunnel ? ['10.0.0.0/8'] : ['0.0.0.0/0', '::/0'],
        persistentKeepalive: 25
      },
      
      // Metadata
      server: {
        country: server.country,
        city: server.city,
        flag: server.flag
      }
    };
    
    return config;
  }

  /**
   * Generate placeholder server public key
   * In production, this would come from the actual server
   */
  generateServerPublicKey(server) {
    const hash = crypto.createHash('sha256')
      .update(server.host || server.ip || 'default')
      .digest();
    return Buffer.from(hash).slice(0, 32);
  }

  /**
   * Export configuration as WireGuard conf file format
   */
  exportAsConfFile(config) {
    let confFile = '[Interface]\n';
    confFile += `PrivateKey = ${Buffer.isBuffer(config.interface.privateKey) 
      ? config.interface.privateKey.toString('base64') 
      : config.interface.privateKey}\n`;
    confFile += `Address = ${config.interface.address}\n`;
    confFile += `DNS = ${config.interface.dns.join(', ')}\n`;
    confFile += `MTU = ${config.interface.mtu}\n`;
    confFile += '\n[Peer]\n';
    confFile += `PublicKey = ${Buffer.isBuffer(config.peer.publicKey)
      ? config.peer.publicKey.toString('base64')
      : config.peer.publicKey}\n`;
    
    if (config.peer.presharedKey) {
      confFile += `PresharedKey = ${Buffer.isBuffer(config.peer.presharedKey)
        ? config.peer.presharedKey.toString('base64')
        : config.peer.presharedKey}\n`;
    }
    
    confFile += `Endpoint = ${config.peer.endpoint}\n`;
    confFile += `AllowedIPs = ${config.peer.allowedIPs.join(', ')}\n`;
    confFile += `PersistentKeepalive = ${config.peer.persistentKeepalive}\n`;
    
    return confFile;
  }
}

// Export for different environments
module.exports = {
  WireGuardTunnel,
  WireGuardInterface,
  WireGuardPeer,
  WireGuardConfigGenerator,
  generateKeyPair,
  generatePresharedKey,
  WIREGUARD_PORT
};
