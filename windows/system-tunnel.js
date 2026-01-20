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

const { exec, spawn, execSync } = require('child_process');
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
     * Generate WireGuard keypair
     */
    static generateKeyPair() {
        const privateKey = crypto.randomBytes(32);
        // Clamp for Curve25519
        privateKey[0] &= 248;
        privateKey[31] &= 127;
        privateKey[31] |= 64;
        
        // For proper public key derivation, we'd need curve25519
        // In production, use the actual WireGuard tools or a proper crypto lib
        return {
            privateKey: privateKey.toString('base64'),
            // Public key would be derived from private key via Curve25519
            // This is a placeholder - real implementation uses wg genkey | wg pubkey
            publicKey: crypto.createHash('sha256').update(privateKey).digest().toString('base64')
        };
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

        const configPath = path.join(configDir, 'wg0.conf');
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

        // Generate or use provided keys
        const keyPair = options.privateKey 
            ? { privateKey: options.privateKey }
            : SystemWideTunnel.generateKeyPair();

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
                shell: true
            });

            proc.stdout.on('data', (data) => {
                console.log(`[WireGuard] ${data}`);
            });

            proc.stderr.on('data', (data) => {
                console.error(`[WireGuard] ${data}`);
            });

            proc.on('close', (code) => {
                if (code === 0) {
                    this.isConnected = true;
                    this.stats.startTime = Date.now();
                    console.log('[FreedomVPN] System-wide tunnel established!');
                    
                    // Enable kill switch
                    if (this.killSwitchEnabled) {
                        this.enableFirewallKillSwitch();
                    }
                    
                    resolve({ success: true, tunnelType: 'WireGuard' });
                } else {
                    reject(new Error(`WireGuard exited with code ${code}`));
                }
            });

            proc.on('error', (err) => {
                reject(err);
            });

            this.tunnelProcess = proc;
        });
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
            // Create VPN profile using PowerShell
            const psScript = `
                $ServerAddress = "${serverAddress}"
                $ConnectionName = "${serverName}"
                
                # Remove existing profile if exists
                try {
                    Remove-VpnConnection -Name $ConnectionName -Force -ErrorAction SilentlyContinue
                } catch {}
                
                # Create new VPN connection (IKEv2 with EAP)
                Add-VpnConnection -Name $ConnectionName \`
                    -ServerAddress $ServerAddress \`
                    -TunnelType IKEv2 \`
                    -AuthenticationMethod MachineCertificate \`
                    -EncryptionLevel Maximum \`
                    -SplitTunneling $false \`
                    -RememberCredential \`
                    -PassThru
                
                # Set DNS to prevent leaks
                Set-VpnConnection -Name $ConnectionName \`
                    -DnsSuffix "" \`
                    -SplitTunneling $false
                
                # Force all traffic through VPN (no split tunneling)
                Set-VpnConnectionIPsecConfiguration -ConnectionName $ConnectionName \`
                    -AuthenticationTransformConstants SHA256128 \`
                    -CipherTransformConstants AES256 \`
                    -EncryptionMethod AES256 \`
                    -IntegrityCheckMethod SHA256 \`
                    -PfsGroup PFS2048 \`
                    -DHGroup Group14 \`
                    -PassThru -Force
                
                # Connect
                rasdial $ConnectionName
            `;

            exec(`powershell -Command "${psScript.replace(/"/g, '\\"')}"`, { 
                shell: 'powershell'
            }, (error, stdout, stderr) => {
                if (error) {
                    console.error('[FreedomVPN] Windows VPN error:', stderr);
                    reject(error);
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
        const script = `
            # Disable IPv6 on all adapters
            Get-NetAdapterBinding -ComponentID ms_tcpip6 | Disable-NetAdapterBinding -ComponentID ms_tcpip6
            
            # Disable IPv6 tunnel adapters
            netsh interface teredo set state disabled
            netsh interface 6to4 set state disabled
            netsh interface isatap set state disabled
        `;

        try {
            execSync(script, { shell: 'powershell', stdio: 'ignore' });
            this.isEnabled = true;
            console.log('[FreedomVPN] IPv6 leak protection enabled');
        } catch (e) {
            console.error('[FreedomVPN] Failed to disable IPv6:', e);
        }
    }

    disable() {
        const script = `
            # Re-enable IPv6 on all adapters
            Get-NetAdapterBinding -ComponentID ms_tcpip6 | Enable-NetAdapterBinding -ComponentID ms_tcpip6
        `;

        try {
            execSync(script, { shell: 'powershell', stdio: 'ignore' });
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
