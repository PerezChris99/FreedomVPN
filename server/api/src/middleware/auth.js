'use strict';
/**
 * Admin authentication middleware
 * =================================
 * Expects:  Authorization: Bearer <ADMIN_TOKEN>
 *
 * Timing-safe comparison prevents timing attacks used to enumerate
 * valid token characters one byte at a time.
 */

const { timingSafeEqual } = require('crypto');

function requireAdmin(req, res, next) {
  const header = req.headers['authorization'] || '';
  const token  = header.startsWith('Bearer ') ? header.slice(7) : '';

  const expected = process.env.ADMIN_TOKEN || '';
  if (!expected) {
    // Misconfiguration — fail closed
    console.error('ADMIN_TOKEN not set — refusing admin request');
    return res.status(503).json({ error: 'Server misconfigured' });
  }

  if (!token) {
    return res.status(401).json({ error: 'Authorization header required' });
  }

  // Prevent timing attacks: pad to same length before comparing
  const a = Buffer.allocUnsafe(expected.length);
  const b = Buffer.allocUnsafe(expected.length);
  a.write(token.slice(0, expected.length).padEnd(expected.length, '\0'));
  b.write(expected);

  // Also check exact length to prevent longer token brute-forcing
  if (token.length !== expected.length || !timingSafeEqual(a, b)) {
    return res.status(403).json({ error: 'Forbidden' });
  }

  next();
}

module.exports = { requireAdmin };
