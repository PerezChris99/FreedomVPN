'use strict';
/**
 * Peer management routes
 * =======================
 * POST /api/peers/register  — register a new WireGuard peer
 * GET  /api/peers           — list all peers (admin only)
 * DELETE /api/peers/:id     — remove a peer (admin only)
 */

const express = require('express');
const { v4: uuidv4 } = require('uuid');
const db      = require('../db');
const wg      = require('../utils/wireguard');
const auth    = require('../middleware/auth');
const rl      = require('../middleware/rateLimit');

const router = express.Router();

// ─── Validation helpers ───────────────────────────────────────────────────────

/**
 * Validate a WireGuard base64 public key.
 * Must be exactly 44 base64 chars decoding to exactly 32 bytes.
 */
function isValidWireGuardKey(key) {
  if (typeof key !== 'string' || key.length !== 44) return false;
  if (!/^[A-Za-z0-9+/]{43}=$/.test(key)) return false;
  try {
    const decoded = Buffer.from(key, 'base64');
    return decoded.length === 32;
  } catch {
    return false;
  }
}

const VALID_PLATFORMS = new Set(['android', 'windows', 'extension', 'web', 'unknown']);

/**
 * Assign the next available IP from the VPN subnet pool.
 * Subnet: 10.8.0.0/24  →  server is 10.8.0.1  →  peers 10.8.0.2 – 10.8.0.254
 */
function assignIP() {
  const subnet = process.env.VPN_SUBNET || '10.8.0.0/24';
  const [base] = subnet.split('/');
  const parts  = base.split('.').map(Number);
  
  const usedStmt = db.prepare('SELECT assigned_ip FROM peers');
  const used = new Set(usedStmt.all().map(r => r.assigned_ip));
  
  for (let host = 2; host <= 254; host++) {
    const candidate = `${parts[0]}.${parts[1]}.${parts[2]}.${host}`;
    if (!used.has(candidate)) return candidate;
  }
  return null; // Pool exhausted
}

// ─── POST /api/peers/register ─────────────────────────────────────────────────

router.post('/register',
  rl.register,   // Stricter rate limit: 10/hour per IP
  async (req, res) => {
    const { publicKey, platform = 'unknown', deviceId = '' } = req.body || {};

    // Validate public key
    if (!publicKey) {
      return res.status(400).json({ error: 'publicKey is required' });
    }
    if (!isValidWireGuardKey(publicKey)) {
      return res.status(400).json({
        error: 'publicKey must be a 44-character base64-encoded 32-byte WireGuard public key',
      });
    }

    // Sanitise platform
    const safePlatform = VALID_PLATFORMS.has(platform) ? platform : 'unknown';

    // Sanitise deviceId — allow alphanumeric/dash/underscore up to 64 chars
    const safeDeviceId = String(deviceId).replace(/[^a-zA-Z0-9\-_]/g, '').slice(0, 64);

    // Check if this key is already registered
    const existing = db.prepare('SELECT * FROM peers WHERE public_key = ?').get(publicKey);
    if (existing) {
      return res.status(200).json({
        id:             existing.id,
        assignedIP:     `${existing.assigned_ip}/32`,
        serverPublicKey: process.env.SERVER_PUBLIC_KEY,
        serverEndpoint:  process.env.SERVER_ENDPOINT,
        dns:            [process.env.DNS1 || '1.1.1.1', process.env.DNS2 || '1.0.0.1'],
        reregistered:   true,
      });
    }

    // Assign IP
    const assignedIP = assignIP();
    if (!assignedIP) {
      console.error('Peer pool exhausted');
      return res.status(503).json({ error: 'Server peer capacity reached' });
    }

    const id = uuidv4();

    // Add to WireGuard interface
    try {
      await wg.addPeer(publicKey, `${assignedIP}/32`);
    } catch (err) {
      console.error('wg addPeer failed:', err.message);
      return res.status(500).json({ error: 'Failed to configure VPN peer on server' });
    }

    // Persist
    db.prepare(`
      INSERT INTO peers (id, public_key, assigned_ip, platform, device_id)
      VALUES (?, ?, ?, ?, ?)
    `).run(id, publicKey, assignedIP, safePlatform, safeDeviceId);

    console.log(`New peer registered: id=${id} ip=${assignedIP} platform=${safePlatform}`);

    return res.status(201).json({
      id,
      assignedIP:      `${assignedIP}/32`,
      serverPublicKey: process.env.SERVER_PUBLIC_KEY,
      serverEndpoint:  process.env.SERVER_ENDPOINT,
      dns:             [process.env.DNS1 || '1.1.1.1', process.env.DNS2 || '1.0.0.1'],
    });
  }
);

// ─── GET /api/peers ───────────────────────────────────────────────────────────

router.get('/', auth.requireAdmin, (req, res) => {
  const rows = db.prepare('SELECT id, assigned_ip, platform, device_id, created_at, last_seen FROM peers ORDER BY created_at DESC').all();
  res.json({ peers: rows, total: rows.length });
});

// ─── DELETE /api/peers/:id ────────────────────────────────────────────────────

router.delete('/:id', auth.requireAdmin, async (req, res) => {
  const { id } = req.params;

  // Only allow valid UUID format
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(id)) {
    return res.status(400).json({ error: 'Invalid peer ID format' });
  }

  const peer = db.prepare('SELECT * FROM peers WHERE id = ?').get(id);
  if (!peer) {
    return res.status(404).json({ error: 'Peer not found' });
  }

  // Remove from WireGuard
  try {
    await wg.removePeer(peer.public_key);
  } catch (err) {
    console.error('wg removePeer failed:', err.message);
    // Continue — remove from DB even if wg command fails
  }

  db.prepare('DELETE FROM peers WHERE id = ?').run(id);
  console.log(`Peer removed: id=${id} ip=${peer.assigned_ip}`);
  res.json({ removed: true, id });
});

module.exports = router;
