#!/usr/bin/env bash
# =============================================================================
# FreedomVPN Server — One-Command Setup
# Tested on Ubuntu 22.04 LTS and Debian 12
# Run as root: bash setup.sh
# =============================================================================
set -euo pipefail

###############################################################################
# CONFIG — edit these before running or pass as env vars
###############################################################################
: "${SERVER_DOMAIN:=""}"              # e.g. vpn.example.com  (used for TLS cert)
: "${ADMIN_TOKEN:=""}"                # Bearer token for admin API endpoints
: "${VPN_PORT:="51820"}"             # WireGuard UDP port
: "${API_PORT:="3000"}"              # Internal Node API port
: "${VPN_SUBNET:="10.8.0.0/24"}"    # Internal WireGuard subnet
: "${VPN_SERVER_IP:="10.8.0.1"}"    # Server's WireGuard IP
: "${WG_IFACE:="wg0"}"              # WireGuard interface name
: "${DNS1:="1.1.1.1"}"              # DNS pushed to peers (CloudFlare)
: "${DNS2:="1.0.0.1"}"              # DNS pushed to peers (CloudFlare secondary)

###############################################################################
# Validation
###############################################################################
validate() {
  if [[ -z "$SERVER_DOMAIN" ]]; then
    echo "ERROR: SERVER_DOMAIN is required (e.g. vpn.example.com)"
    echo "  Run: SERVER_DOMAIN=vpn.example.com bash setup.sh"
    exit 1
  fi
  if [[ -z "$ADMIN_TOKEN" ]]; then
    ADMIN_TOKEN=$(openssl rand -hex 32)
    echo "INFO: Generated ADMIN_TOKEN=$ADMIN_TOKEN"
    echo "INFO: Save this — you won't see it again"
  fi
}

###############################################################################
# System dependencies
###############################################################################
install_deps() {
  echo "[1/8] Installing system dependencies..."
  apt-get update -qq
  apt-get install -y -qq \
    wireguard wireguard-tools \
    curl gnupg lsb-release \
    nginx certbot python3-certbot-nginx \
    ufw iptables sqlite3 \
    build-essential
  
  # Node.js 20 LTS
  if ! command -v node >/dev/null 2>&1; then
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
    apt-get install -y nodejs
  fi
  echo "  Node $(node --version), npm $(npm --version)"
}

###############################################################################
# WireGuard setup
###############################################################################
setup_wireguard() {
  echo "[2/8] Configuring WireGuard..."
  
  # Generate server keys
  mkdir -p /etc/wireguard
  chmod 700 /etc/wireguard
  
  if [[ ! -f /etc/wireguard/server_private.key ]]; then
    wg genkey | tee /etc/wireguard/server_private.key | wg pubkey > /etc/wireguard/server_public.key
    chmod 600 /etc/wireguard/server_private.key
  fi
  
  SERVER_PRIVATE_KEY=$(cat /etc/wireguard/server_private.key)
  SERVER_PUBLIC_KEY=$(cat /etc/wireguard/server_public.key)
  
  # Detect public-facing interface
  PUBLIC_IFACE=$(ip route get 1.1.1.1 | grep -oP 'dev \K\S+')
  
  cat > "/etc/wireguard/${WG_IFACE}.conf" << WGCONF
[Interface]
PrivateKey = ${SERVER_PRIVATE_KEY}
Address = ${VPN_SERVER_IP}/24
ListenPort = ${VPN_PORT}
SaveConfig = false

# Route VPN traffic through the server
PostUp   = iptables -t nat -A POSTROUTING -s ${VPN_SUBNET} -o ${PUBLIC_IFACE} -j MASQUERADE
PostUp   = iptables -A FORWARD -i ${WG_IFACE} -j ACCEPT
PostUp   = iptables -A FORWARD -o ${WG_IFACE} -j ACCEPT
PostDown = iptables -t nat -D POSTROUTING -s ${VPN_SUBNET} -o ${PUBLIC_IFACE} -j MASQUERADE
PostDown = iptables -D FORWARD -i ${WG_IFACE} -j ACCEPT
PostDown = iptables -D FORWARD -o ${WG_IFACE} -j ACCEPT
WGCONF
  
  chmod 600 "/etc/wireguard/${WG_IFACE}.conf"
  
  # Enable IP forwarding
  echo "net.ipv4.ip_forward=1" >> /etc/sysctl.d/99-wireguard.conf
  echo "net.ipv6.conf.all.disable_ipv6=1" >> /etc/sysctl.d/99-wireguard.conf
  sysctl -p /etc/sysctl.d/99-wireguard.conf
  
  systemctl enable --now "wg-quick@${WG_IFACE}"
  echo "  Server public key: ${SERVER_PUBLIC_KEY}"
}

###############################################################################
# Firewall
###############################################################################
setup_firewall() {
  echo "[3/8] Configuring firewall..."
  ufw default deny incoming
  ufw default allow outgoing
  ufw allow ssh
  ufw allow 80/tcp
  ufw allow 443/tcp
  ufw allow "${VPN_PORT}/udp"
  ufw --force enable
}

###############################################################################
# API server
###############################################################################
install_api() {
  echo "[4/8] Installing peer management API..."
  
  mkdir -p /opt/freedomvpn-api
  cp -r "$(dirname "$0")/api/"* /opt/freedomvpn-api/
  
  cd /opt/freedomvpn-api
  npm install --omit=dev --silent
  
  SERVER_PUBLIC_KEY=$(cat /etc/wireguard/server_public.key)
  PUBLIC_IP=$(curl -s https://checkip.amazonaws.com/ || curl -s https://ipinfo.io/ip)
  
  cat > /opt/freedomvpn-api/.env << ENVFILE
NODE_ENV=production
PORT=${API_PORT}
ADMIN_TOKEN=${ADMIN_TOKEN}
WG_IFACE=${WG_IFACE}
VPN_SUBNET=${VPN_SUBNET}
VPN_SERVER_IP=${VPN_SERVER_IP}
VPN_PORT=${VPN_PORT}
SERVER_PUBLIC_KEY=${SERVER_PUBLIC_KEY}
SERVER_ENDPOINT=${PUBLIC_IP}:${VPN_PORT}
DNS1=${DNS1}
DNS2=${DNS2}
DB_PATH=/opt/freedomvpn-api/data/peers.db
ENVFILE
  chmod 600 /opt/freedomvpn-api/.env
  
  # systemd service
  cat > /etc/systemd/system/freedomvpn-api.service << SVCFILE
[Unit]
Description=FreedomVPN Peer Management API
After=network.target wg-quick@${WG_IFACE}.service
Requires=wg-quick@${WG_IFACE}.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/freedomvpn-api
EnvironmentFile=/opt/freedomvpn-api/.env
ExecStart=/usr/bin/node src/index.js
Restart=always
RestartSec=5
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
SVCFILE
  
  systemctl daemon-reload
  systemctl enable --now freedomvpn-api
}

###############################################################################
# Nginx + TLS
###############################################################################
setup_nginx() {
  echo "[5/8] Configuring Nginx and TLS..."
  
  cp "$(dirname "$0")/nginx/nginx.conf" /etc/nginx/sites-available/freedomvpn
  sed -i "s/SERVER_DOMAIN/${SERVER_DOMAIN}/g" /etc/nginx/sites-available/freedomvpn
  sed -i "s/API_PORT/${API_PORT}/g" /etc/nginx/sites-available/freedomvpn
  
  ln -sf /etc/nginx/sites-available/freedomvpn /etc/nginx/sites-enabled/freedomvpn
  rm -f /etc/nginx/sites-enabled/default
  nginx -t
  systemctl reload nginx
  
  # Obtain TLS cert
  certbot --nginx -d "${SERVER_DOMAIN}" --non-interactive --agree-tos \
    --email "admin@${SERVER_DOMAIN}" --redirect
  
  # Auto-renew
  systemctl enable --now certbot.timer
}

###############################################################################
# Monitoring
###############################################################################
setup_monitoring() {
  echo "[6/8] Setting up monitoring..."
  
  install -m 755 "$(dirname "$0")/scripts/healthcheck.sh" /usr/local/bin/freedomvpn-healthcheck
  
  # Run healthcheck every 5 minutes
  (crontab -l 2>/dev/null; echo "*/5 * * * * /usr/local/bin/freedomvpn-healthcheck >> /var/log/freedomvpn-health.log 2>&1") | crontab -
}

###############################################################################
# Summary
###############################################################################
print_summary() {
  SERVER_PUBLIC_KEY=$(cat /etc/wireguard/server_public.key)
  PUBLIC_IP=$(curl -s https://checkip.amazonaws.com/ 2>/dev/null || echo "UNKNOWN")
  echo ""
  echo "=========================================="
  echo "  FreedomVPN Server Setup Complete"
  echo "=========================================="
  echo "  Domain:         https://${SERVER_DOMAIN}"
  echo "  Public IP:      ${PUBLIC_IP}"
  echo "  WireGuard port: UDP ${VPN_PORT}"
  echo "  Server pubkey:  ${SERVER_PUBLIC_KEY}"
  echo "  Admin token:    ${ADMIN_TOKEN}"
  echo ""
  echo "  API endpoints:"
  echo "    GET  https://${SERVER_DOMAIN}/api/health"
  echo "    GET  https://${SERVER_DOMAIN}/api/servers"
  echo "    POST https://${SERVER_DOMAIN}/api/peers/register"
  echo ""
  echo "  Logs:  journalctl -u freedomvpn-api -f"
  echo "         journalctl -u wg-quick@${WG_IFACE} -f"
  echo "=========================================="
}

###############################################################################
# Entry point
###############################################################################
main() {
  validate
  install_deps
  setup_wireguard
  setup_firewall
  install_api
  setup_nginx
  setup_monitoring
  print_summary
}

main "$@"
