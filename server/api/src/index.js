'use strict';
/**
 * FreedomVPN Peer Management API
 * ===============================
 * Minimal, secure Express server that:
 *  - Accepts WireGuard public keys from clients
 *  - Assigns /32 IPs from the VPN subnet
 *  - Calls `wg set` to add the peer to the live interface
 *  - Returns the server's WireGuard endpoint so clients can build their config
 */

require('dotenv').config();
const express    = require('express');
const helmet     = require('helmet');
const db         = require('./db');
const peers      = require('./routes/peers');
const health     = require('./routes/health');
const servers    = require('./routes/servers');
const rateLimit  = require('./middleware/rateLimit');

// ─── Validate required env ───────────────────────────────────────────────────
const REQUIRED_ENV = ['ADMIN_TOKEN', 'WG_IFACE', 'SERVER_PUBLIC_KEY', 'SERVER_ENDPOINT'];
for (const key of REQUIRED_ENV) {
  if (!process.env[key]) {
    console.error(`FATAL: environment variable ${key} is not set`);
    process.exit(1);
  }
}

// ─── App ─────────────────────────────────────────────────────────────────────
const app  = express();
const PORT = parseInt(process.env.PORT || '3000', 10);

// Security headers — never serve anything that a browser shouldn't need
app.use(helmet({
  contentSecurityPolicy: {
    directives: {
      defaultSrc: ["'self'"],
    },
  },
}));

// Body parsing — limit to 64 KB to prevent request flooding
app.use(express.json({ limit: '64kb' }));

// Remove express fingerprint
app.disable('x-powered-by');

// Global rate limit — 100 req/15min per IP
app.use(rateLimit.global);

// ─── Routes ──────────────────────────────────────────────────────────────────
app.use('/api/health',  health);
app.use('/api/servers', servers);
app.use('/api/peers',   peers);

// 404
app.use((req, res) => {
  res.status(404).json({ error: 'Not found' });
});

// Error handler
app.use((err, req, res, _next) => {
  console.error('Unhandled error:', err.message);
  res.status(500).json({ error: 'Internal server error' });
});

// ─── Start ───────────────────────────────────────────────────────────────────
app.listen(PORT, '127.0.0.1', () => {
  console.log(`FreedomVPN API listening on 127.0.0.1:${PORT}`);
  console.log(`WireGuard interface: ${process.env.WG_IFACE}`);
  console.log(`Server endpoint: ${process.env.SERVER_ENDPOINT}`);
});

// Clean shutdown
process.on('SIGTERM', () => {
  console.log('SIGTERM received — shutting down');
  db.close();
  process.exit(0);
});

// ─── Scheduled peer expiry ───────────────────────────────────────────────────
// Auto-expire peers inactive > PEER_EXPIRY_DAYS (default 30) every 24 hours.
// This runs in-process without a cron daemon dependency.

const wg           = require('./utils/wireguard');
const EXPIRY_DAYS  = parseInt(process.env.PEER_EXPIRY_DAYS || '30', 10);
const EXPIRY_MS    = 24 * 60 * 60 * 1000; // run every 24 hours

async function expireStale() {
  const cutoff = Math.floor(Date.now() / 1000) - EXPIRY_DAYS * 86400;
  const stale  = db.prepare('SELECT * FROM peers WHERE last_seen < ?').all(cutoff);
  if (stale.length === 0) return;

  let count = 0;
  for (const peer of stale) {
    try { await wg.removePeer(peer.public_key); } catch {}
    db.prepare('DELETE FROM peers WHERE id = ?').run(peer.id);
    db.auditLog('expire', peer.id, '127.0.0.1', peer.platform, {
      assignedIP: peer.assigned_ip,
      lastSeen:   peer.last_seen,
      cutoff,
      source:     'scheduler',
    });
    count++;
  }
  console.log(`[scheduler] Expired ${count} stale peer(s) inactive > ${EXPIRY_DAYS} days`);
}

// Run once at startup (catches peers missed during downtime), then every 24h
expireStale().catch(err => console.error('[scheduler] expiry error:', err.message));
setInterval(() => expireStale().catch(err =>
  console.error('[scheduler] expiry error:', err.message)
), EXPIRY_MS);

module.exports = app; // for tests
