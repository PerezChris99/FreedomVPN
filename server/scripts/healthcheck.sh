#!/usr/bin/env bash
# FreedomVPN server health check script
# Runs every 5 minutes via cron, logs to /var/log/freedomvpn-health.log
set -euo pipefail

WG_IFACE="${WG_IFACE:-wg0}"
API_PORT="${API_PORT:-3000}"
TIMESTAMP=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
FAIL=0

log() { echo "${TIMESTAMP} $*"; }

# 1 — WireGuard interface up
if wg show "${WG_IFACE}" > /dev/null 2>&1; then
  WG_PEERS=$(wg show "${WG_IFACE}" peers | wc -l)
  log "OK  WireGuard ${WG_IFACE} up — ${WG_PEERS} peers"
else
  log "ERR WireGuard ${WG_IFACE} is DOWN — attempting restart"
  systemctl restart "wg-quick@${WG_IFACE}" || true
  FAIL=1
fi

# 2 — API server responds
if curl -sf "http://127.0.0.1:${API_PORT}/api/health" > /dev/null 2>&1; then
  log "OK  API server responding on :${API_PORT}"
else
  log "ERR API server not responding — attempting restart"
  systemctl restart freedomvpn-api || true
  FAIL=1
fi

# 3 — Disk space > 10% free
DISK_FREE=$(df / | tail -1 | awk '{print $5}' | tr -d '%')
if [[ "$DISK_FREE" -lt 90 ]]; then
  log "OK  Disk ${DISK_FREE}% used"
else
  log "WARN Disk ${DISK_FREE}% used — consider cleanup"
fi

# 4 — IP forwarding enabled
if [[ "$(cat /proc/sys/net/ipv4/ip_forward)" == "1" ]]; then
  log "OK  IP forwarding enabled"
else
  log "ERR IP forwarding disabled — re-enabling"
  echo 1 > /proc/sys/net/ipv4/ip_forward
  FAIL=1
fi

exit $FAIL
