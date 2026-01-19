package com.freedomvpn.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.freedomvpn.ui.theme.*
import com.freedomvpn.viewmodel.SettingsViewModel

/**
 * Protocol options for VPN connection
 */
enum class VpnProtocol(val displayName: String) {
    WIREGUARD("WireGuard"),
    OPENVPN_UDP("OpenVPN (UDP)"),
    OPENVPN_TCP("OpenVPN (TCP)"),
    AUTO("Auto (Recommended)")
}

/**
 * Obfuscation level options
 */
enum class ObfuscationLevel(val displayName: String) {
    OFF("Off"),
    LOW("Low - Basic XOR"),
    MEDIUM("Medium - Traffic Padding"),
    HIGH("High - TLS Camouflage"),
    FULL("Maximum - Full Obfuscation")
}

/**
 * Settings screen for VPN configuration
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val killSwitchEnabled by viewModel.killSwitchEnabled.collectAsState()
    val autoConnect by viewModel.autoConnect.collectAsState()
    val selectedProtocol by viewModel.selectedProtocol.collectAsState()
    val obfuscationLevel by viewModel.obfuscationLevel.collectAsState()
    val splitTunnelingEnabled by viewModel.splitTunnelingEnabled.collectAsState()
    val dnsLeakProtection by viewModel.dnsLeakProtection.collectAsState()
    val ipv6LeakProtection by viewModel.ipv6LeakProtection.collectAsState()
    val startOnBoot by viewModel.startOnBoot.collectAsState()
    
    var showProtocolDialog by remember { mutableStateOf(false) }
    var showObfuscationDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary
                )
            )
        },
        containerColor = BackgroundDark
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection section
            item {
                SettingsSection(title = "Connection") {
                    // Protocol selection
                    SettingsItem(
                        icon = Icons.Default.Security,
                        title = "Protocol",
                        subtitle = selectedProtocol.displayName,
                        onClick = { showProtocolDialog = true }
                    )
                    
                    Divider(color = TextDisabled.copy(alpha = 0.2f))
                    
                    // Auto-connect
                    SettingsToggle(
                        icon = Icons.Default.Autorenew,
                        title = "Auto-connect",
                        subtitle = "Connect automatically when app starts",
                        checked = autoConnect,
                        onCheckedChange = { viewModel.setAutoConnect(it) }
                    )
                    
                    Divider(color = TextDisabled.copy(alpha = 0.2f))
                    
                    // Start on boot
                    SettingsToggle(
                        icon = Icons.Default.PowerSettingsNew,
                        title = "Start on boot",
                        subtitle = "Start FreedomVPN when device boots",
                        checked = startOnBoot,
                        onCheckedChange = { viewModel.setStartOnBoot(it) }
                    )
                }
            }
            
            // Security section
            item {
                SettingsSection(title = "Security") {
                    // Kill switch
                    SettingsToggle(
                        icon = Icons.Default.Shield,
                        title = "Kill Switch",
                        subtitle = "Block all traffic if VPN disconnects",
                        checked = killSwitchEnabled,
                        onCheckedChange = { viewModel.setKillSwitch(it) },
                        iconTint = if (killSwitchEnabled) Connected else TextSecondary
                    )
                    
                    Divider(color = TextDisabled.copy(alpha = 0.2f))
                    
                    // DNS leak protection
                    SettingsToggle(
                        icon = Icons.Default.Dns,
                        title = "DNS Leak Protection",
                        subtitle = "Use secure DNS servers",
                        checked = dnsLeakProtection,
                        onCheckedChange = { viewModel.setDnsLeakProtection(it) }
                    )
                    
                    Divider(color = TextDisabled.copy(alpha = 0.2f))
                    
                    // IPv6 leak protection
                    SettingsToggle(
                        icon = Icons.Default.Language,
                        title = "IPv6 Leak Protection",
                        subtitle = "Block IPv6 traffic to prevent leaks",
                        checked = ipv6LeakProtection,
                        onCheckedChange = { viewModel.setIpv6LeakProtection(it) }
                    )
                }
            }
            
            // Censorship bypass section
            item {
                SettingsSection(title = "Censorship Bypass") {
                    // Obfuscation level
                    SettingsItem(
                        icon = Icons.Default.VisibilityOff,
                        title = "Traffic Obfuscation",
                        subtitle = obfuscationLevel.displayName,
                        onClick = { showObfuscationDialog = true }
                    )
                    
                    // Info text
                    Text(
                        text = "Higher obfuscation helps bypass DPI but may reduce speed",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            
            // Advanced section
            item {
                SettingsSection(title = "Advanced") {
                    // Split tunneling
                    SettingsToggle(
                        icon = Icons.Default.CallSplit,
                        title = "Split Tunneling",
                        subtitle = "Choose which apps use VPN",
                        checked = splitTunnelingEnabled,
                        onCheckedChange = { viewModel.setSplitTunneling(it) }
                    )
                    
                    if (splitTunnelingEnabled) {
                        Divider(color = TextDisabled.copy(alpha = 0.2f))
                        SettingsItem(
                            icon = Icons.Default.Apps,
                            title = "Manage Apps",
                            subtitle = "Select apps to include/exclude",
                            onClick = { /* TODO: Navigate to app selection */ }
                        )
                    }
                }
            }
            
            // About section
            item {
                SettingsSection(title = "About") {
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "Version",
                        subtitle = "1.0.0 (Alpha)"
                    )
                    
                    Divider(color = TextDisabled.copy(alpha = 0.2f))
                    
                    SettingsItem(
                        icon = Icons.Default.Help,
                        title = "Help & Support",
                        subtitle = "Get help using FreedomVPN",
                        onClick = { /* TODO */ }
                    )
                    
                    Divider(color = TextDisabled.copy(alpha = 0.2f))
                    
                    SettingsItem(
                        icon = Icons.Default.PrivacyTip,
                        title = "Privacy Policy",
                        onClick = { /* TODO */ }
                    )
                }
            }
            
            // Footer
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Made with ❤️ for freedom",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
    
    // Protocol selection dialog
    if (showProtocolDialog) {
        SelectionDialog(
            title = "Select Protocol",
            options = VpnProtocol.values().toList(),
            selectedOption = selectedProtocol,
            onOptionSelected = { 
                viewModel.setProtocol(it)
                showProtocolDialog = false
            },
            onDismiss = { showProtocolDialog = false },
            optionLabel = { it.displayName }
        )
    }
    
    // Obfuscation selection dialog
    if (showObfuscationDialog) {
        SelectionDialog(
            title = "Obfuscation Level",
            options = ObfuscationLevel.values().toList(),
            selectedOption = obfuscationLevel,
            onOptionSelected = {
                viewModel.setObfuscationLevel(it)
                showObfuscationDialog = false
            },
            onDismiss = { showObfuscationDialog = false },
            optionLabel = { it.displayName }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = Primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark)
                .padding(vertical = 4.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    iconTint: Color = TextSecondary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
        
        if (onClick != null) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Open",
                tint = TextSecondary
            )
        }
    }
}

@Composable
private fun SettingsToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconTint: Color = TextSecondary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Primary,
                checkedTrackColor = Primary.copy(alpha = 0.5f),
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = TextDisabled
            )
        )
    }
}

@Composable
private fun <T> SelectionDialog(
    title: String,
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    onDismiss: () -> Unit,
    optionLabel: (T) -> String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOptionSelected(option) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == selectedOption,
                            onClick = { onOptionSelected(option) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Primary,
                                unselectedColor = TextSecondary
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = optionLabel(option),
                            color = if (option == selectedOption) TextPrimary else TextSecondary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Primary)
            }
        },
        containerColor = SurfaceDark,
        titleContentColor = TextPrimary
    )
}
