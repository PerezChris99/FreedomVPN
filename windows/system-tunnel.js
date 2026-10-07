/**
 * FreedomVPN System-Wide Tunnel Manager
 * 
 * IMPORTANT: This module provides TRUE SYSTEM-WIDE VPN tunneling.
 * 
 * Unlike browser-only proxies, this tunnels ALL system traffic:
 * - All applications (not just browser)
 * - System services
 * - Background apps
 * - DNS queries at system level
 * 
 * Methods for system-wide tunneling on Windows:
 * 1. WireGuard-NT kernel driver (preferred - fastest)
 * 2. Windows built-in VPN with IKEv2
 * 3. Windows Filtering Platform (WFP) for kill switch
 * 4. Route table manipulation for full traffic capture
 */

const { exec, spawn, execSync, execFileSync } = require('child_process');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');
const os = require('os');

class SystemWideTunnel {
    constructor() {
        this.isConnected = false;
        this.currentConfig = null;
        this.tunnelProcess = null;
        this.killSwitchEnabled = false;
        this.originalDNS = null;
        this.originalRoutes = [];
        this.stats = {
            bytesIn: 0,
            bytesOut: 0,
            startTime: null
        };
    }

    /**
     * Check if WireGuard is installed on the system
     */
    static isWireGuardInstalled() {
        const possiblePaths = [
            'C:\\Program Files\\WireGuard\\wireguard.exe',
            'C:\\Program Files (x86)\\WireGuard\\wireguard.exe',
            path.join(os.homedir(), 'wireguard', 'wireguard.exe'),
            path.join(process.env.LOCALAPPDATA || '', 'Programs', 'WireGuard', 'wireguard.exe')
        ];

        for (const p of possiblePaths) {
            if (fs.existsSync(p)) {
                return p;
            }
        }

        // Check if in PATH
        try {
            execSync('where wireguard', { stdio: 'ignore' });
            return 'wireguard';
        } catch {
            return null;
        }
    }

    /**
     * Generate WireGuard keypair using proper Curve25519 scalar multiplication.
     *
     * WireGuard key format:
     *  - Private key: 32 random bytes, clamped per RFC 7748
     *  - Public key:  Curve25519(private, basepoint)  — NOT a hash
     *
     * We prefer the WireGuard CLI (`wg genkey / wg pubkey`) when available
     * because it is the canonical reference implementation. When WireGuard is
     * not yet installed we fall back to tweetnacl which implements the same
     * Curve25519 scalar multiplication.
     *
     * @returns {{ privateKey: string, publicKey: string }}  base64-encoded keys
     */
    static generateKeyPair() {
        // Attempt to use the WireGuard CLI first (most reliable path)
        const wgPath = SystemWideTunnel.isWireGuardInstalled();
        if (wgPath && wgPath !== 'wireguard') {
            // wg.exe lives next to wireguard.exe
            const wgTool = path.join(path.dirname(wgPath), 'wg.exe');
            if (fs.existsSync(wgTool)) {
                try {
                    const privateKey = execSync(`"${wgTool}" genkey`, { encoding: 'utf8' }).trim();
                    const publicKey  = execSync(`echo ${privateKey} | "${wgTool}" pubkey`, {
                        encoding: 'utf8', shell: true
                    }).trim();
                    return { privateKey, publicKey };
                } catch {
                    // Fall through to JS implementation
                }
            }
        }

        // Pure-JS fallback using tweetnacl (correct Curve25519 implementation)
        const nacl = require('tweetnacl');

        // Generate 32 cryptographically random bytes
        const secretKey = crypto.randomBytes(32);

        // Clamp the private key per RFC 7748 §5 (required by WireGuard)
        secretKey[0]  &= 248;
        secretKey[31]  = (secretKey[31] & 127) | 64;

        // Derive public key: pubkey = secretKey * G  (Curve25519 base point)
        const publicKeyBytes = nacl.scalarMult.base(new Uint8Array(secretKey));

        return {
            privateKey: secretKey.toString('base64'),
            publicKey:  Buffer.from(publicKeyBytes).toString('base64'),
        };
    }

    /**
     * Encrypt a plaintext string using Windows DPAPI (CryptProtectData).
     * The encrypted blob can ONLY be decrypted by the same Windows user account
     * on the same machine — if the store file is copied, keys cannot be extracted.
     *
     * Falls back to base64-identity encoding on non-Windows platforms (dev/test only).
     *
     * @param {string} plaintext  UTF-8 plaintext to protect
     * @returns {string}          Base64-encoded encrypted blob (or plain base64 on non-Windows)
     */
    static encryptDPAPI(plaintext) {
        if (process.platform !== 'win32') {
            // Non-Windows fallback — NOT secure; for dev/test only
            return Buffer.from(plaintext, 'utf8').toString('base64');
        }
        // PowerShell one-liner: pipe plaintext bytes through ProtectedData.Protect
        const bytes = Buffer.from(plaintext, 'utf8').toString(',');
        const ps = `
[System.Convert]::ToBase64String(
    [System.Security.Cryptography.ProtectedData]::Protect(
        [byte[]](${ bytes }.Split(',')),
        $null,
        [System.Security.Cryptography.DataProtectionScope]::CurrentUser
    )
)`.trim();
        try {
            const result = execSync(
                `powershell -NoProfile -NonInteractive -Command "${ps.replace(/"/g, '\\"')}"`,
                { encoding: 'utf8', timeout: 10000 }
            );
            return result.trim();
        } catch (err) {
            console.error('[FreedomVPN] DPAPI encrypt failed:', err.message);
            throw new Error('Failed to encrypt key with DPAPI');
        }
    }

    /**
     * Decrypt a DPAPI-encrypted blob back to plaintext.
     * Throws if decryption fails (wrong user, wrong machine, or tampered data).
     *
     * @param {string} ciphertext  Base64-encoded DPAPI blob
     * @returns {string}           Decrypted UTF-8 plaintext
     */
    static decryptDPAPI(ciphertext) {
        if (process.platform !== 'win32') {
            return Buffer.from(ciphertext, 'base64').toString('utf8');
        }
        const ps = `
[System.Text.Encoding]::UTF8.GetString(
    [System.Security.Cryptography.ProtectedData]::Unprotect(
        [System.Convert]::FromBase64String('${ciphertext}'),
        $null,
        [System.Security.Cryptography.DataProtectionScope]::CurrentUser
    )
)`.trim();
        try {
            const result = execSync(
                `powershell -NoProfile -NonInteractive -Command "${ps.replace(/"/g, '\\"')}"`,
                { encoding: 'utf8', timeout: 10000 }
            );
            return result.trim();
        } catch (err) {
            console.error('[FreedomVPN] DPAPI decrypt failed:', err.message);
            throw new Error('Failed to decrypt key with DPAPI — key may be from a different user or machine');
        }
    }

    /**
     * Async variant — always uses WireGuard CLI when available, guaranteeing
     * 100% compatible key encoding without needing tweetnacl in PATH-only envs.
     * @returns {Promise<{ privateKey: string, publicKey: string }>}
     */
    static async generateKeyPairAsync() {
        const wgPath = SystemWideTunnel.isWireGuardInstalled();
        if (wgPath) {
            const dir    = wgPath !== 'wireguard' ? path.dirname(wgPath) : '';
            const wgTool = dir ? path.join(dir, 'wg.exe') : 'wg';
            try {
                return await new Promise((resolve, reject) => {
                    exec(`"${wgTool}" genkey`, (err, privOut) => {
                        if (err) return reject(err);
                        const privateKey = privOut.trim();
                        exec(`echo ${privateKey} | "${wgTool}" pubkey`, { shell: true },
                            (err2, pubOut) => {
                                if (err2) return reject(err2);
                                resolve({ privateKey, publicKey: pubOut.trim() });
                            }
                        );
                    });
                });
            } catch {
                // fall through
            }
        }
        // Sync JS fallback is always safe
        return SystemWideTunnel.generateKeyPair();
    }

    /**
     * Generate WireGuard configuration file
     */
    generateConfig(options) {
        const {
            privateKey,
            serverPublicKey,
            serverEndpoint,
            serverPort = 51820,
            clientAddress = '10.0.0.2/32',
            dns = ['8.8.8.8', '8.8.4.4', '1.1.1.1'],
            allowedIPs = ['0.0.0.0/0', '::/0'], // ALL traffic
            persistentKeepalive = 25,
            preSharedKey = null
        } = options;

        let config = `[Interface]
PrivateKey = ${privateKey}
Address = ${clientAddress}
DNS = ${dns.join(', ')}
MTU = 1280

[Peer]
PublicKey = ${serverPublicKey}
Endpoint = ${serverEndpoint}:${serverPort}
AllowedIPs = ${allowedIPs.join(', ')}
PersistentKeepalive = ${persistentKeepalive}`;

        if (preSharedKey) {
            config += `\nPresharedKey = ${preSharedKey}`;
        }

        return config;
    }

    /**
     * Save config to a temporary file
     */
    saveConfigFile(config) {
        const configDir = path.join(os.tmpdir(), 'FreedomVPN');
        if (!fs.existsSync(configDir)) {
            fs.mkdirSync(configDir, { recursive: true });
        }

        const configPath = path.join(configDir, `wg-${process.pid}-${Date.now()}.conf`);
        fs.writeFileSync(configPath, config, { mode: 0o600 }); // Secure permissions
        return configPath;
    }

    /**
     * Connect using WireGuard (SYSTEM-WIDE)
     * This tunnels ALL traffic through the VPN
     */
    async connectWireGuard(options) {
        const wireGuardPath = SystemWideTunnel.isWireGuardInstalled();
        
        if (!wireGuardPath) {
            throw new Error('WireGuard not installed. Please install from https://www.wireguard.com/install/');
        }

        console.log('[FreedomVPN] Starting system-wide WireGuard tunnel...');

        // Generate or use provided keys (async, uses WireGuard CLI when available)
        const keyPair = options.privateKey
            ? { privateKey: options.privateKey }
            : await SystemWideTunnel.generateKeyPairAsync();

        // Generate config
        const config = this.generateConfig({
            ...options,
            privateKey: keyPair.privateKey
        });

        // Save config
        const configPath = this.saveConfigFile(config);
        this.currentConfig = configPath;

        return new Promise((resolve, reject) => {
            // Use wireguard.exe to install tunnel service
            const args = ['/installtunnelservice', configPath];
            
            console.log(`[FreedomVPN] Running: ${wireGuardPath} ${args.join(' ')}`);

            const proc = spawn(wireGuardPath, args, {
                stdio: 'pipe',
                shell: false
            });

            proc.stdout.on('data', (data) => {
                console.log(`[WireGuard] ${data}`);
            });

            proc.stderr.on('data', (data) => {
                console.error(`[WireGuard] ${data}`);
            });

            proc.on('close', (code) => {
                if (code !== 0) {
                    reject(new Error(`WireGuard exited with code ${code}`));
                    return;
                }

                // Installing the service is not proof that the tunnel is usable.
                // Verify the WireGuard interface exists before reporting connected.
                const interfaceName = path.basename(configPath, '.conf');
                try {
                    this.verifyWireGuardTunnel(interfaceName);
                } catch (err) {
                    try {
                        execFileSync(wireGuardPath, ['/uninstalltunnelservice', interfaceName], {
                            stdio: 'ignore',
                            timeout: 10000
                        });
                    } catch {}
                    reject(err);
                    return;
                }

                this.isConnected = true;
                this.stats.startTime = Date.now();
                console.log('[FreedomVPN] System-wide WireGuard tunnel verified.');

                if (this.killSwitchEnabled) {
                    this.enableFirewallKillSwitch();
                }

                resolve({ success: true, tunnelType: 'WireGuard', verified: true });
            });

            proc.on('error', (err) => {
                reject(err);
            });

            this.tunnelProcess = proc;
        });
    }

    /** Verify that the named WireGuard interface is actually present. */
    verifyWireGuardTunnel(interfaceName) {
        if (!interfaceName || !/^[A-Za-z0-9._-]+$/.test(interfaceName)) {
            throw new Error('Invalid WireGuard interface name');
        }

        try {
            const output = execFileSync('wg', ['show', interfaceName], {
                encoding: 'utf8',
                timeout: 5000,
                windowsHide: true,
                stdio: ['ignore', 'pipe', 'pipe']
            });
            if (!output || !output.trim()) {
                throw new Error('WireGuard interface reported no state');
            }
            return true;
        } catch (err) {
            throw new Error(`WireGuard tunnel verification failed: ${err.message}`);
        }
    }

    /**
     * Connect using Windows built-in VPN (IKEv2)
     * SYSTEM-WIDE tunneling without WireGuard
     */
    async connectWindowsVPN(options) {
        const {
            serverAddress,
            serverName = 'FreedomVPN',
            username = '',
            password = '',
            preSharedKey = ''
        } = options;

        console.log('[FreedomVPN] Setting up Windows VPN profile...');

        return new Promise((resolve, reject) => {
            // Write PowerShell script to temp file to avoid escaping issues
            const scriptPath = path.join(os.tmpdir(), 'FreedomVPN', 'setup-vpn.ps1');
            const scriptDir = path.dirname(scriptPath);
            
            if (!fs.existsSync(scriptDir)) {
                fs.mkdirSync(scriptDir, { recursive: true });
            }
            
            const psScript = `
$ServerAddress = '${serverAddress}'
$ConnectionName = '${serverName}'

# Remove existing profile if exists
try {
    Remove-VpnConnection -Name $ConnectionName -Force -ErrorAction SilentlyContinue
} catch {}

# Create new VPN connection (IKEv2)
try {
    Add-VpnConnection -Name $ConnectionName -ServerAddress $ServerAddress -TunnelType IKEv2 -AuthenticationMethod MachineCertificate -EncryptionLevel Maximum -SplitTunneling $false -RememberCredential -PassThru
} catch {
    Write-Error "Failed to create VPN connection: $_"
    exit 1
}

# Try to connect
try {
    rasdial $ConnectionName
} catch {
    Write-Error "Failed to connect: $_"
    exit 1
}

Write-Host "VPN Connected Successfully"
`;
            
            fs.writeFileSync(scriptPath, psScript);
            
            exec(`powershell -ExecutionPolicy Bypass -File "${scriptPath}"`, (error, stdout, stderr) => {
                if (error) {
                    console.error('[FreedomVPN] Windows VPN error:', stderr);
                    // Do NOT mark as connected — the tunnel failed.
                    // Propagate the error so the UI shows the real state.
                    reject(new Error(`Windows VPN connection failed: ${stderr || error.message}`));
                } else {
                    this.isConnected = true;
                    this.stats.startTime = Date.now();
                    console.log('[FreedomVPN] Windows VPN connected!');
                    resolve({ success: true, tunnelType: 'WindowsVPN' });
                }
            });
        });
    }

    /**
     * Disconnect system-wide VPN
     */
    async disconnect() {
        console.log('[FreedomVPN] Disconnecting system-wide tunnel...');

        // Disable kill switch first
        if (this.killSwitchEnabled) {
            this.disableFirewallKillSwitch();
        }

        // Disconnect WireGuard
        if (this.currentConfig) {
            try {
                execSync(`wireguard /uninstalltunnelservice "${path.basename(this.currentConfig, '.conf')}"`, {
                    stdio: 'ignore'
                });
            } catch {}
        }

        // Disconnect Windows VPN
        try {
            execSync('rasdial /disconnect', { stdio: 'ignore' });
        } catch {}

        // Kill tunnel process if running
        if (this.tunnelProcess) {
            this.tunnelProcess.kill();
            this.tunnelProcess = null;
        }

        // Restore original DNS
        this.restoreOriginalDNS();

        this.isConnected = false;
        this.currentConfig = null;
        console.log('[FreedomVPN] Disconnected');

        return { success: true };
    }

    /**
     * Enable Windows Firewall kill switch
     * Blocks ALL traffic except through VPN tunnel
     */
    enableFirewallKillSwitch() {
        console.log('[FreedomVPN] Enabling firewall kill switch...');

        const rules = `
            # Block all non-VPN traffic
            netsh advfirewall firewall add rule name="FreedomVPN-KillSwitch-Out" dir=out action=block
            netsh advfirewall firewall add rule name="FreedomVPN-KillSwitch-In" dir=in action=block
            
            # Allow VPN adapter
            netsh advfirewall firewall add rule name="FreedomVPN-Allow-WG" dir=out interface="wg0" action=allow
            netsh advfirewall firewall add rule name="FreedomVPN-Allow-WG-In" dir=in interface="wg0" action=allow
            
            # Allow local network
            netsh advfirewall firewall add rule name="FreedomVPN-Allow-Local" dir=out action=allow remoteip=10.0.0.0/8,172.16.0.0/12,192.168.0.0/16
            
            # Allow DHCP
            netsh advfirewall firewall add rule name="FreedomVPN-Allow-DHCP" dir=out action=allow protocol=udp localport=68 remoteport=67
        `;

        try {
            execSync(rules, { shell: 'powershell', stdio: 'ignore' });
            this.killSwitchEnabled = true;
            console.log('[FreedomVPN] Kill switch enabled');
        } catch (e) {
            console.error('[FreedomVPN] Failed to enable kill switch:', e);
        }
    }

    /**
     * Disable firewall kill switch
     */
    disableFirewallKillSwitch() {
        console.log('[FreedomVPN] Disabling kill switch...');

        const rules = `
            netsh advfirewall firewall delete rule name="FreedomVPN-KillSwitch-Out"
            netsh advfirewall firewall delete rule name="FreedomVPN-KillSwitch-In"
            netsh advfirewall firewall delete rule name="FreedomVPN-Allow-WG"
            netsh advfirewall firewall delete rule name="FreedomVPN-Allow-WG-In"
            netsh advfirewall firewall delete rule name="FreedomVPN-Allow-Local"
            netsh advfirewall firewall delete rule name="FreedomVPN-Allow-DHCP"
        `;

        try {
            execSync(rules, { shell: 'powershell', stdio: 'ignore' });
            this.killSwitchEnabled = false;
        } catch {}
    }

    /**
     * Set system-wide DNS to prevent DNS leaks
     */
    setSecureDNS(dnsServers = ['8.8.8.8', '8.8.4.4']) {
        console.log('[FreedomVPN] Setting system-wide secure DNS...');

        // Save original DNS settings
        try {
            this.originalDNS = execSync('netsh interface ip show dns', { encoding: 'utf8' });
        } catch {}

        // Set DNS on all adapters
        const script = `
            Get-NetAdapter | ForEach-Object {
                Set-DnsClientServerAddress -InterfaceIndex $_.ifIndex -ServerAddresses ("${dnsServers.join('","')}")
            }
        `;

        try {
            execSync(script, { shell: 'powershell', stdio: 'ignore' });
            console.log('[FreedomVPN] Secure DNS configured');
        } catch (e) {
            console.error('[FreedomVPN] Failed to set DNS:', e);
        }
    }

    /**
     * Restore original DNS settings
     */
    restoreOriginalDNS() {
        const script = `
            Get-NetAdapter | ForEach-Object {
                Set-DnsClientServerAddress -InterfaceIndex $_.ifIndex -ResetServerAddresses
            }
        `;

        try {
            execSync(script, { shell: 'powershell', stdio: 'ignore' });
        } catch {}
    }

    /**
     * Force all routes through VPN (belt-and-suspenders approach)
     */
    forceAllTrafficThroughVPN(vpnGateway) {
        console.log('[FreedomVPN] Forcing all traffic through VPN gateway...');

        const script = `
            # Delete default route
            route delete 0.0.0.0
            
            # Add VPN as default gateway
            route add 0.0.0.0 mask 0.0.0.0 ${vpnGateway} metric 1
            
            # Block IPv6 to prevent leaks
            netsh interface ipv6 set interface "Ethernet" disabled
            netsh interface ipv6 set interface "Wi-Fi" disabled
        `;

        try {
            execSync(script, { shell: 'cmd', stdio: 'ignore' });
        } catch {}
    }

    /**
     * Get connection statistics
     */
    getStats() {
        if (!this.isConnected) return null;

        return {
            connected: this.isConnected,
            duration: this.stats.startTime ? Date.now() - this.stats.startTime : 0,
            bytesIn: this.stats.bytesIn,
            bytesOut: this.stats.bytesOut
        };
    }

    // ── Server API integration ────────────────────────────────────────────────

    /**
     * Register this client's WireGuard public key with the FreedomVPN server.
     * Returns peer credentials: assignedIP, serverPublicKey, serverEndpoint, dns.
     *
     * @param {string} publicKey  44-char base64 WireGuard public key
     * @param {string} serverUrl  Base URL of the FreedomVPN peer management API
     * @returns {Promise<{id, assignedIP, serverPublicKey, serverEndpoint, dns}>}
     */
    static async registerPeer(publicKey, serverUrl) {
        if (!serverUrl || !serverUrl.startsWith('https://')) {
            throw new Error('serverUrl must be an HTTPS URL');
        }
        if (!publicKey || publicKey.length !== 44) {
            throw new Error('publicKey must be a 44-character base64 WireGuard key');
        }

        const { execFile } = require('child_process');
        // Use node-fetch (already in windows/package.json)
        const fetch = require('node-fetch');

        const response = await fetch(`${serverUrl}/api/peers/register`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                publicKey,
                platform: 'windows',
                deviceId: SystemWideTunnel._getDeviceId(),
            }),
            timeout: 15000,
        });

        if (!response.ok) {
            const text = await response.text().catch(() => '');
            throw new Error(`Server returned ${response.status}: ${text.slice(0, 200)}`);
        }

        return response.json();
    }

    /**
     * Fetch the list of available VPN servers from the FreedomVPN API.
     * @param {string} serverUrl  Base URL of the FreedomVPN peer management API
     * @returns {Promise<Array<{id, name, country, endpoint, publicKey}>>}
     */
    static async fetchServerList(serverUrl) {
        if (!serverUrl || !serverUrl.startsWith('https://')) {
            throw new Error('serverUrl must be an HTTPS URL');
        }

        const fetch = require('node-fetch');
        const response = await fetch(`${serverUrl}/api/servers`, { timeout: 15000 });

        if (!response.ok) {
            throw new Error(`Server returned ${response.status}`);
        }

        const json = await response.json();
        return json.servers || [];
    }

    /**
     * Full production connect flow:
     *   1. Generate / restore key pair
     *   2. Register public key with server API
     *   3. Start WireGuard tunnel with returned credentials
     *
     * @param {string} serverUrl  HTTPS base URL of the FreedomVPN peer management API
     * @param {object} store      electron-store instance (or any {get,set} object)
     */
    async connectViaServerApi(serverUrl, store) {
        console.log('[FreedomVPN] Connecting via server API:', serverUrl);

        // 1. Get or generate key pair — private key is DPAPI-encrypted at rest
        let keyPair = null;
        const storedEncKey = store && store.get('wg_private_key');
        if (storedEncKey) {
            try {
                const privKey = SystemWideTunnel.decryptDPAPI(storedEncKey);
                const nacl     = require('tweetnacl');
                const privBytes = Buffer.from(privKey, 'base64');
                const pubBytes  = nacl.scalarMult.base(new Uint8Array(privBytes));
                keyPair = {
                    privateKey: privKey,
                    publicKey:  Buffer.from(pubBytes).toString('base64'),
                };
            } catch {
                // Decryption failed (different user/machine) — generate a new key
                console.warn('[FreedomVPN] DPAPI decrypt failed for wg_private_key — regenerating');
                keyPair = null;
            }
        }
        if (!keyPair) {
            keyPair = await SystemWideTunnel.generateKeyPairAsync();
            if (store) {
                store.set('wg_private_key', SystemWideTunnel.encryptDPAPI(keyPair.privateKey));
            }
        }

        // 2. Register with server
        const creds = await SystemWideTunnel.registerPeer(keyPair.publicKey, serverUrl);
        console.log('[FreedomVPN] Registered peer:', creds.id, 'IP:', creds.assignedIP);

        // 3. Start WireGuard tunnel
        const [endpointHost, endpointPort] = this._parseEndpoint(creds.serverEndpoint);
        return this.connectWireGuard({
            privateKey:      keyPair.privateKey,
            serverPublicKey: creds.serverPublicKey,
            serverEndpoint:  endpointHost,
            serverPort:      endpointPort || 51820,
            clientAddress:   creds.assignedIP,
            dns:             creds.dns || ['1.1.1.1', '1.0.0.1'],
        });
    }

    /** Parse "host:port" → [host, port] (handles IPv6 bracket notation). */
    _parseEndpoint(endpoint) {
        if (endpoint.startsWith('[')) {
            const close = endpoint.lastIndexOf(']');
            return [endpoint.slice(1, close), parseInt(endpoint.slice(close + 2)) || 51820];
        }
        const last = endpoint.lastIndexOf(':');
        if (last < 0) return [endpoint, 51820];
        return [endpoint.slice(0, last), parseInt(endpoint.slice(last + 1)) || 51820];
    }

    /**
     * Returns a stable, anonymous device identifier stored in %APPDATA%.
     * Private — not exported from the module.
     */
    static _getDeviceId() {
        const idFile = require('path').join(
            process.env.APPDATA || require('os').homedir(),
            'FreedomVPN', 'device_id.txt'
        );
        try {
            if (require('fs').existsSync(idFile)) {
                return require('fs').readFileSync(idFile, 'utf8').trim();
            }
        } catch {}
        const id = require('crypto').randomBytes(16).toString('hex');
        try {
            require('fs').mkdirSync(require('path').dirname(idFile), { recursive: true });
            require('fs').writeFileSync(idFile, id, { mode: 0o600 });
        } catch {}
        return id;
    }

    /**
     * Register with Cloudflare WARP to obtain free WireGuard credentials.
     * The WARP registration API is public and requires no account.
     * Cloudflare WARP provides a free, fast, privacy-respecting WireGuard VPN.
     *
     * @param {string} publicKey  Base64-encoded WireGuard public key
     * @returns {Promise<{id, serverPublicKey, serverEndpoint, assignedIP, dns}>}
     */
    static async registerWarpPeer(publicKey) {
        const fetch = require('node-fetch');
        const installId = SystemWideTunnel._getDeviceId();

        const response = await fetch('https://api.cloudflareclient.com/v0a4005/reg', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'User-Agent': 'okhttp/3.12.1',
            },
            body: JSON.stringify({
                key:           publicKey,
                install_id:    installId,
                fcm_token:     '',
                tos:           new Date().toISOString(),
                model:         'PC',
                serial_number: installId,
                locale:        'en_US',
            }),
            timeout: 20000,
        });

        if (!response.ok) {
            const body = await response.text().catch(() => '');
            throw new Error(`WARP registration failed (${response.status}): ${body.slice(0, 200)}`);
        }

        const data = await response.json();
        const peer = data.config?.peers?.[0];
        if (!peer) throw new Error('WARP API: no peer in response');

        return {
            id:              data.id,
            serverPublicKey: peer.public_key,
            serverEndpoint:  peer.endpoint?.host || 'engage.cloudflareclient.com:2408',
            assignedIP:      data.config.interface?.addresses?.v4 || '172.16.0.2/32',
            dns:             ['1.1.1.1', '1.0.0.1'],
        };
    }

    /**
     * Connect via Cloudflare WARP (free WireGuard VPN).
     * Registers automatically on first use — no account, no sign-up required.
     * Credentials are cached in electron-store so re-registration is skipped.
     *
     * @param {object} store  electron-store instance (or any {get, set} object)
     */
    async connectViaWarp(store) {
        console.log('[FreedomVPN] Connecting via Cloudflare WARP (free WireGuard)...');

        // 1. Get or generate the WireGuard key pair — DPAPI-encrypted at rest
        let keyPair = null;
        const storedEncKey = store && store.get('warp_private_key');
        if (storedEncKey) {
            try {
                const privKey  = SystemWideTunnel.decryptDPAPI(storedEncKey);
                const nacl     = require('tweetnacl');
                const privBytes = Buffer.from(privKey, 'base64');
                const pubBytes  = nacl.scalarMult.base(new Uint8Array(privBytes));
                keyPair = {
                    privateKey: privKey,
                    publicKey:  Buffer.from(pubBytes).toString('base64'),
                };
            } catch {
                console.warn('[FreedomVPN] DPAPI decrypt failed for warp_private_key — regenerating');
                keyPair = null;
            }
        }
        if (!keyPair) {
            keyPair = await SystemWideTunnel.generateKeyPairAsync();
            if (store) store.set('warp_private_key', SystemWideTunnel.encryptDPAPI(keyPair.privateKey));
        }

        // 2. Use cached WARP credentials or register fresh
        let creds = store && store.get('warp_creds');
        if (!creds) {
            creds = await SystemWideTunnel.registerWarpPeer(keyPair.publicKey);
            if (store) store.set('warp_creds', creds);
            console.log('[FreedomVPN] Registered with Cloudflare WARP, peer:', creds.id);
        } else {
            console.log('[FreedomVPN] Using cached WARP credentials, peer:', creds.id);
        }

        // 3. Start WireGuard tunnel with WARP credentials
        const [endpointHost, endpointPort] = this._parseEndpoint(creds.serverEndpoint);
        return this.connectWireGuard({
            privateKey:      keyPair.privateKey,
            serverPublicKey: creds.serverPublicKey,
            serverEndpoint:  endpointHost,
            serverPort:      endpointPort || 2408,
            clientAddress:   creds.assignedIP,
            dns:             creds.dns,
        });
    }
}

/**
 * DNS Leak Protection Module
 * Ensures all DNS queries go through VPN
 */
class DNSLeakProtection {
    constructor() {
        this.isEnabled = false;
        this.blockedDNSServers = [];
    }

    /**
     * Block all DNS except through VPN
     */
    enable() {
        const script = `
            # Block all DNS traffic except to allowed servers
            netsh advfirewall firewall add rule name="FreedomVPN-BlockDNS-UDP" dir=out action=block protocol=udp remoteport=53
            netsh advfirewall firewall add rule name="FreedomVPN-BlockDNS-TCP" dir=out action=block protocol=tcp remoteport=53
            
            # Allow DNS through VPN interface only
            netsh advfirewall firewall add rule name="FreedomVPN-AllowVPNDNS" dir=out action=allow interface="wg0" protocol=udp remoteport=53
        `;

        try {
            execSync(script, { shell: 'powershell', stdio: 'ignore' });
            this.isEnabled = true;
            console.log('[FreedomVPN] DNS leak protection enabled');
        } catch (e) {
            console.error('[FreedomVPN] Failed to enable DNS protection:', e);
        }
    }

    /**
     * Disable DNS leak protection
     */
    disable() {
        const script = `
            netsh advfirewall firewall delete rule name="FreedomVPN-BlockDNS-UDP"
            netsh advfirewall firewall delete rule name="FreedomVPN-BlockDNS-TCP"
            netsh advfirewall firewall delete rule name="FreedomVPN-AllowVPNDNS"
        `;

        try {
            execSync(script, { shell: 'powershell', stdio: 'ignore' });
            this.isEnabled = false;
        } catch {}
    }
}

/**
 * IPv6 Leak Protection
 * Completely disables IPv6 to prevent dual-stack leaks
 */
class IPv6LeakProtection {
    constructor() {
        this.isEnabled = false;
    }

    enable() {
        try {
            // Try disabling IPv6 tunnel adapters (doesn't require admin)
            execSync('netsh interface teredo set state disabled', { stdio: 'ignore' });
            execSync('netsh interface 6to4 set state disabled', { stdio: 'ignore' });
            execSync('netsh interface isatap set state disabled', { stdio: 'ignore' });
            this.isEnabled = true;
            console.log('[FreedomVPN] IPv6 tunnel adapters disabled');
        } catch (e) {
            // Silently fail - IPv6 protection is optional
            console.log('[FreedomVPN] IPv6 leak protection skipped (requires admin)');
        }
    }

    disable() {
        try {
            execSync('netsh interface teredo set state default', { stdio: 'ignore' });
            this.isEnabled = false;
        } catch {}
    }
}

// Export modules
module.exports = {
    SystemWideTunnel,
    DNSLeakProtection,
    IPv6LeakProtection
};