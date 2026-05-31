'use strict';
/**
 * SQLite database setup via better-sqlite3
 * ==========================================
 * Schema:
 *   peers     — one row per registered WireGuard peer
 *   audit_log — immutable append-only security event log
 *
 * peers columns:
 *   id          TEXT PRIMARY KEY  — UUID v4
 *   public_key  TEXT UNIQUE       — WireGuard base64 public key (44 chars)
 *   assigned_ip TEXT UNIQUE       — IP from VPN pool, e.g. "10.8.0.2"
 *   platform    TEXT              — "android" | "windows" | "extension" | "web"
 *   device_id   TEXT              — client-provided opaque device identifier
 *   created_at  INTEGER           — Unix timestamp (seconds)
 *   last_seen   INTEGER           — Updated on each /health check-in
 *
 * audit_log columns:
 *   id          INTEGER PRIMARY KEY AUTOINCREMENT
 *   event       TEXT    — "register" | "reregister" | "delete" | "expire"
 *   peer_id     TEXT    — UUID of the peer (may be null for bulk expiry)
 *   client_ip   TEXT    — Request IP (anonymised to /24 for privacy)
 *   platform    TEXT    — platform tag from the peer registration
 *   detail      TEXT    — extra context (JSON string, optional)
 *   created_at  INTEGER — Unix timestamp (seconds)
 */

const Database = require('better-sqlite3');
const path     = require('path');
const fs       = require('fs');

const DB_PATH = process.env.DB_PATH || path.join(__dirname, '../../data/peers.db');

// Ensure data directory exists
const dbDir = path.dirname(DB_PATH);
if (!fs.existsSync(dbDir)) {
  fs.mkdirSync(dbDir, { recursive: true, mode: 0o700 });
}

const db = new Database(DB_PATH, { fileMustExist: false });

// Enable WAL mode for better concurrent read performance
db.pragma('journal_mode = WAL');
db.pragma('synchronous = NORMAL');
// Restrict DB file access — only owner can read
fs.chmodSync(DB_PATH, 0o600);

// ─── Schema ──────────────────────────────────────────────────────────────────
db.exec(`
  CREATE TABLE IF NOT EXISTS peers (
    id          TEXT    NOT NULL PRIMARY KEY,
    public_key  TEXT    NOT NULL UNIQUE,
    assigned_ip TEXT    NOT NULL UNIQUE,
    platform    TEXT    NOT NULL DEFAULT 'unknown',
    device_id   TEXT    NOT NULL DEFAULT '',
    created_at  INTEGER NOT NULL DEFAULT (unixepoch()),
    last_seen   INTEGER NOT NULL DEFAULT (unixepoch())
  );

  CREATE INDEX IF NOT EXISTS idx_peers_public_key ON peers(public_key);
  CREATE INDEX IF NOT EXISTS idx_peers_assigned_ip ON peers(assigned_ip);

  CREATE TABLE IF NOT EXISTS audit_log (
    id         INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    event      TEXT    NOT NULL,
    peer_id    TEXT,
    client_ip  TEXT    NOT NULL DEFAULT '',
    platform   TEXT    NOT NULL DEFAULT '',
    detail     TEXT    NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL DEFAULT (unixepoch())
  );

  CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_log(created_at);
  CREATE INDEX IF NOT EXISTS idx_audit_event   ON audit_log(event);
`);

// ─── Helpers ─────────────────────────────────────────────────────────────────

/**
 * Anonymise a client IP to /24 (IPv4) or /48 (IPv6) before storing.
 * This preserves enough to detect abuse without storing full IPs.
 */
function anonymiseIP(ip) {
  if (!ip) return '';
  // Strip IPv6-mapped IPv4 prefix
  const raw = ip.startsWith('::ffff:') ? ip.slice(7) : ip;
  if (raw.includes('.')) {
    // IPv4 — keep first 3 octets
    const parts = raw.split('.');
    return `${parts[0]}.${parts[1]}.${parts[2]}.0/24`;
  }
  // IPv6 — keep first 6 groups (48-bit prefix)
  const groups = raw.split(':').slice(0, 6);
  return `${groups.join(':')}::/48`;
}

const _insertAudit = db.prepare(`
  INSERT INTO audit_log (event, peer_id, client_ip, platform, detail)
  VALUES (?, ?, ?, ?, ?)
`);

/**
 * Write an immutable audit log entry.
 * @param {'register'|'reregister'|'delete'|'expire'} event
 * @param {string|null} peerId
 * @param {string} clientIp   — raw IP from req.ip; will be anonymised
 * @param {string} platform
 * @param {object} detail     — extra context; will be JSON-stringified
 */
function auditLog(event, peerId, clientIp, platform = '', detail = {}) {
  try {
    _insertAudit.run(
      event,
      peerId || null,
      anonymiseIP(clientIp),
      platform,
      JSON.stringify(detail),
    );
  } catch (err) {
    // Audit failures must never crash the request path — log and continue
    console.error('[audit] write failed:', err.message);
  }
}

db.auditLog    = auditLog;
db.anonymiseIP = anonymiseIP;

module.exports = db;
