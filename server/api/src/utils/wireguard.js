'use strict';
/**
 * WireGuard CLI wrapper
 * ======================
 * Wraps `wg set` / `wg` commands for peer management.
 * All arguments are passed as an array to execFile() — never via shell
 * interpolation — to prevent command injection.
 *
 * Requires: WireGuard tools installed, running as root (or with CAP_NET_ADMIN)
 */

const { execFile } = require('child_process');
const { promisify } = require('util');

const execFileAsync = promisify(execFile);
const WG_BIN        = process.env.WG_BIN || '/usr/bin/wg';
const IFACE         = () => process.env.WG_IFACE || 'wg0';

// Timeout for wg commands (ms)
const CMD_TIMEOUT = 10_000;

/**
 * Validate base64 WireGuard key — prevents shell injection even though
 * we use execFile (extra defence in depth).
 */
function validateKey(key) {
  if (typeof key !== 'string' || key.length !== 44) {
    throw new Error(`Invalid WireGuard key length: ${key?.length}`);
  }
  if (!/^[A-Za-z0-9+/]{43}=$/.test(key)) {
    throw new Error('WireGuard key contains invalid characters');
  }
}

/**
 * Validate CIDR notation for allowed-ips.
 * Only accept IPv4 /32 addresses from the VPN pool.
 */
function validateCIDR(cidr) {
  if (!/^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\/\d{1,2}$/.test(cidr)) {
    throw new Error(`Invalid CIDR: ${cidr}`);
  }
  const [ip, prefix] = cidr.split('/');
  if (parseInt(prefix, 10) < 16 || parseInt(prefix, 10) > 32) {
    throw new Error(`CIDR prefix out of range: ${prefix}`);
  }
  const octets = ip.split('.').map(Number);
  if (octets.some(o => o < 0 || o > 255)) {
    throw new Error(`CIDR IP out of range: ${ip}`);
  }
}

/**
 * Add a peer to the WireGuard interface.
 * Equivalent to: wg set wg0 peer <pubkey> allowed-ips <cidr>
 */
async function addPeer(publicKey, allowedIPs) {
  validateKey(publicKey);
  validateCIDR(allowedIPs);

  await execFileAsync(WG_BIN, [
    'set', IFACE(),
    'peer', publicKey,
    'allowed-ips', allowedIPs,
  ], { timeout: CMD_TIMEOUT });
}

/**
 * Remove a peer from the WireGuard interface.
 * Equivalent to: wg set wg0 peer <pubkey> remove
 */
async function removePeer(publicKey) {
  validateKey(publicKey);

  await execFileAsync(WG_BIN, [
    'set', IFACE(),
    'peer', publicKey,
    'remove',
  ], { timeout: CMD_TIMEOUT });
}

/**
 * Get peer statistics.
 * Returns parsed output of `wg show wg0 dump`.
 */
async function getPeerStats() {
  const { stdout } = await execFileAsync(WG_BIN, ['show', IFACE(), 'dump'], {
    timeout: CMD_TIMEOUT,
  });
  // Skip first line (server line), parse peer lines
  return stdout.trim().split('\n').slice(1).map(line => {
    const [pubkey, presharedKey, endpoint, allowedIPs, lastHandshake, rxBytes, txBytes] = line.split('\t');
    return { pubkey, endpoint, allowedIPs, lastHandshake: parseInt(lastHandshake, 10), rxBytes: parseInt(rxBytes, 10), txBytes: parseInt(txBytes, 10) };
  });
}

module.exports = { addPeer, removePeer, getPeerStats };
