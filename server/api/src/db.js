'use strict';
/**
 * SQLite database setup via better-sqlite3
 * ==========================================
 * Schema:
 *   peers — one row per registered WireGuard peer
 *
 * Columns:
 *   id          TEXT PRIMARY KEY  — UUID v4
 *   public_key  TEXT UNIQUE       — WireGuard base64 public key (44 chars)
 *   assigned_ip TEXT UNIQUE       — IP from VPN pool, e.g. "10.8.0.2"
 *   platform    TEXT              — "android" | "windows" | "extension" | "web"
 *   device_id   TEXT              — client-provided opaque device identifier
 *   created_at  INTEGER           — Unix timestamp (seconds)
 *   last_seen   INTEGER           — Updated on each /health check-in
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
`);

module.exports = db;
