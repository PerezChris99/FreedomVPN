'use strict';
/**
 * GET /api/servers
 * Returns the list of available VPN server locations.
 * No auth required — clients call this to build the server picker UI.
 *
 * In a single-server deployment this returns one entry.
 * In a multi-server deployment, add entries to SERVERS_JSON env var
 * or extend this module to read from a database.
 */

const express = require('express');
const router  = express.Router();

// Build server entry from environment
function buildSelf() {
  const endpoint = process.env.SERVER_ENDPOINT || '';
  const [host]   = endpoint.split(':');

  return {
    id:          process.env.SERVER_ID || 'primary',
    name:        process.env.SERVER_NAME || 'Primary Server',
    country:     process.env.SERVER_COUNTRY || 'Unknown',
    countryCode: process.env.SERVER_COUNTRY_CODE || 'XX',
    city:        process.env.SERVER_CITY || '',
    endpoint:    endpoint,
    publicKey:   process.env.SERVER_PUBLIC_KEY || '',
    capacity:    253,       // /24 subnet minus server IP
    online:      true,
    latencyHint: null,      // populated by client-side ping
  };
}

router.get('/', (req, res) => {
  // Support additional servers via EXTRA_SERVERS_JSON env var
  let extra = [];
  try {
    if (process.env.EXTRA_SERVERS_JSON) {
      extra = JSON.parse(process.env.EXTRA_SERVERS_JSON);
    }
  } catch (err) {
    console.error('Failed to parse EXTRA_SERVERS_JSON:', err.message);
  }

  const servers = [buildSelf(), ...extra];
  res.json({ servers, total: servers.length });
});

module.exports = router;
