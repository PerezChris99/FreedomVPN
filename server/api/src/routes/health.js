'use strict';
/**
 * GET /api/health
 * Returns server health, uptime and current peer count.
 * No authentication required — used by monitoring and clients
 * to verify the server is reachable before attempting to connect.
 */

const express = require('express');
const { execSync } = require('child_process');
const db      = require('../db');

const router  = express.Router();
const START   = Date.now();

router.get('/', (req, res) => {
  const peerCount = db.prepare('SELECT COUNT(*) AS n FROM peers').get().n;

  let wgStatus = 'unknown';
  try {
    execSync(`wg show ${process.env.WG_IFACE || 'wg0'} 2>/dev/null`, { timeout: 3000 });
    wgStatus = 'up';
  } catch {
    wgStatus = 'down';
  }

  const uptime = Math.floor((Date.now() - START) / 1000);

  const healthy = wgStatus === 'up';
  res.status(healthy ? 200 : 503).json({
    status:       healthy ? 'ok' : 'degraded',
    wireguard:    wgStatus,
    peers:        peerCount,
    uptime,
    server:       process.env.SERVER_ENDPOINT || 'unknown',
    timestamp:    new Date().toISOString(),
  });
});

module.exports = router;
