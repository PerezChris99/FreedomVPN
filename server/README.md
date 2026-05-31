# FreedomVPN Server Infrastructure

## Architecture

```
[Client] ──WireGuard── [VPN Server :51820]
              │
      [Peer API :3000] ── [SQLite DB]
              │
      [Nginx :443/80] ── [SSL TLS 1.3]
```

## Requirements

- Ubuntu 22.04 LTS (recommended) or Debian 12
- 1 vCPU / 512 MB RAM minimum
- Public static IPv4 address
- Open UDP port 51820 (WireGuard)
- Open TCP ports 80/443 (Nginx/API)

## Quick Start

```bash
# As root on a fresh Ubuntu 22.04 server:
bash setup.sh
```

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/health | Server health + stats |
| GET | /api/servers | Server list (no auth) |
| POST | /api/peers/register | Register a new peer |
| DELETE | /api/peers/:id | Remove a peer (admin only) |
| GET | /api/peers | List active peers (admin only) |

## Peer Registration Flow

1. Client generates WireGuard key pair locally (private key never leaves client)
2. Client POSTs `{publicKey, platform, deviceId}` to `/api/peers/register`
3. Server assigns a `/32` IP from `10.8.0.0/24` pool
4. Server adds `wg set wg0 peer <pubkey> allowed-ips <assignedIP>/32`
5. Server returns `{assignedIP, serverPublicKey, serverEndpoint, dns}`
6. Client builds WireGuard config and starts tunnel

## Security

- Admin token via `ADMIN_TOKEN` environment variable (set in `.env`)
- Rate limiting: 10 registrations per IP per hour
- Peer IPs from pool `10.8.0.2` – `10.8.0.254` (253 max concurrent peers)
- All API traffic over HTTPS (Nginx TLS 1.3 + cert)
- WireGuard UDP only on port 51820

## Directory Layout

```
server/
  setup.sh              # One-command installer
  docker-compose.yml    # Alternative: Docker deployment
  api/
    package.json
    src/
      index.js          # Express app entry
      db.js             # SQLite setup
      routes/
        peers.js        # Peer CRUD
        health.js       # Health check
        servers.js      # Server list
      middleware/
        auth.js         # Bearer token auth
        rateLimit.js    # express-rate-limit
      utils/
        wireguard.js    # wg CLI wrapper
  nginx/
    nginx.conf          # SSL termination + rate limiting
  scripts/
    healthcheck.sh      # Monitoring script
    backup.sh           # DB backup script
```
