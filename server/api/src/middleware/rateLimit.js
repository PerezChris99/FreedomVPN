'use strict';
/**
 * Rate limiting middleware
 * =========================
 * Uses in-memory store — suitable for single-server deployment.
 * For multi-server: swap to Redis store via `rate-limit-redis`.
 */

const rateLimit = require('express-rate-limit');

// Standard window / max values used across limiters
const WINDOW_15MIN = 15 * 60 * 1000;
const WINDOW_1HOUR = 60 * 60 * 1000;

/**
 * Global limiter: 100 requests per 15 minutes per IP.
 * Applies to all routes.
 */
const global = rateLimit({
  windowMs:         WINDOW_15MIN,
  max:              100,
  standardHeaders:  true,
  legacyHeaders:    false,
  message:          { error: 'Too many requests — please slow down' },
  // Use X-Forwarded-For when behind Nginx (trust proxy)
  keyGenerator:     (req) => req.headers['x-forwarded-for']?.split(',')[0].trim() || req.ip,
});

/**
 * Registration limiter: 10 peer registrations per hour per IP.
 * Prevents a single IP from exhausting the peer pool.
 */
const register = rateLimit({
  windowMs:         WINDOW_1HOUR,
  max:              10,
  standardHeaders:  true,
  legacyHeaders:    false,
  message:          { error: 'Too many registrations from this IP — try again in 1 hour' },
  keyGenerator:     (req) => req.headers['x-forwarded-for']?.split(',')[0].trim() || req.ip,
});

module.exports = { global, register };
