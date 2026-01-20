package com.freedomvpn.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedomvpn.ui.theme.*
import com.freedomvpn.vpn.FreedomVpnService
import com.freedomvpn.vpngate.VpnGateServer
import kotlinx.coroutines.delay

/**
 * Beautiful Main Screen - Matches Web Version Design
 * 
 * Features:
 * - Glassmorphism effects
 * - Animated connection button
 * - Beautiful stats cards
 * - Modern dark theme with gradients
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeautifulMainScreen(
    connectionState: FreedomVpnService.ConnectionState,
    selectedServer: VpnGateServer?,
    servers: List<VpnGateServer>,
    stats: ConnectionStats,
    currentIP: String?,
    isLoadingIP: Boolean,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onServerSelect: (VpnGateServer) -> Unit,
    onRefresh: () -> Unit
) {
    val isConnected = connectionState == FreedomVpnService.ConnectionState.CONNECTED
    val isConnecting = connectionState == FreedomVpnService.ConnectionState.CONNECTING
    
    // Background gradient
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0f172a),
                        Color(0xFF1e293b)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                BeautifulHeader(onRefresh = onRefresh)
            }
            
            // Connection Card with Big Button
            item {
                ConnectionCard(
                    connectionState = connectionState,
                    onConnectClick = if (isConnected) onDisconnectClick else onConnectClick
                )
            }
            
            // Selected Server Card
            item {
                AnimatedVisibility(
                    visible = selectedServer != null,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    selectedServer?.let { server ->
                        SelectedServerCard(server = server)
                    }
                }
            }
            
            // IP Address Card
            item {
                IPAddressCard(
                    isConnected = isConnected,
                    currentIP = currentIP,
                    isLoading = isLoadingIP
                )
            }
            
            // Stats Grid - Only when connected
            item {
                AnimatedVisibility(
                    visible = isConnected,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    StatsGrid(stats = stats)
                }
            }
            
            // Security Features Card
            item {
                SecurityFeaturesCard(isConnected = isConnected)
            }
            
            // Quick Server Selection
            item {
                QuickServerSelection(
                    servers = servers.take(10),
                    selectedServer = selectedServer,
                    onServerSelect = onServerSelect
                )
            }
            
            // Bottom padding
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

/**
 * Beautiful Header with App Name
 */
@Composable
fun BeautifulHeader(onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "FreedomVPN",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Secure • Private • Free",
                fontSize = 14.sp,
                color = TextSecondary
            )
        }
        
        IconButton(
            onClick = onRefresh,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(SurfaceDark.copy(alpha = 0.5f))
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "Refresh",
                tint = Primary
            )
        }
    }
}

/**
 * Connection Card with Animated Button
 */
@Composable
fun ConnectionCard(
    connectionState: FreedomVpnService.ConnectionState,
    onConnectClick: () -> Unit
) {
    val isConnected = connectionState == FreedomVpnService.ConnectionState.CONNECTED
    val isConnecting = connectionState == FreedomVpnService.ConnectionState.CONNECTING
    
    // Pulsing animation for connected state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    // Rotation for connecting state
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status Badge
            StatusBadge(connectionState = connectionState)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Big Connection Button
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .scale(if (isConnected) pulseScale else 1f)
                    .shadow(
                        elevation = 20.dp,
                        shape = CircleShape,
                        ambientColor = if (isConnected) Connected.copy(alpha = 0.5f) 
                                       else if (isConnecting) Connecting.copy(alpha = 0.5f)
                                       else Color.Transparent,
                        spotColor = if (isConnected) Connected.copy(alpha = 0.5f)
                                    else if (isConnecting) Connecting.copy(alpha = 0.5f)
                                    else Color.Transparent
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = when (connectionState) {
                                FreedomVpnService.ConnectionState.CONNECTED -> 
                                    listOf(Color(0xFF22c55e), Color(0xFF16a34a))
                                FreedomVpnService.ConnectionState.CONNECTING -> 
                                    listOf(Color(0xFFf59e0b), Color(0xFFd97706))
                                else -> listOf(Color(0xFF475569), Color(0xFF334155))
                            }
                        )
                    )
                    .clickable(enabled = !isConnecting) { onConnectClick() },
                contentAlignment = Alignment.Center
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(60.dp),
                        color = Color.White,
                        strokeWidth = 4.dp
                    )
                } else {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.Shield else Icons.Outlined.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = when (connectionState) {
                    FreedomVpnService.ConnectionState.CONNECTED -> "Protected"
                    FreedomVpnService.ConnectionState.CONNECTING -> "Connecting..."
                    FreedomVpnService.ConnectionState.DISCONNECTING -> "Disconnecting..."
                    FreedomVpnService.ConnectionState.ERROR -> "Connection Error"
                    else -> "Not Protected"
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = when (connectionState) {
                    FreedomVpnService.ConnectionState.CONNECTED -> Connected
                    FreedomVpnService.ConnectionState.CONNECTING -> Connecting
                    FreedomVpnService.ConnectionState.ERROR -> Disconnected
                    else -> TextSecondary
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (isConnected) "Tap to disconnect" else "Tap to connect",
                fontSize = 14.sp,
                color = TextSecondary
            )
        }
    }
}

/**
 * Status Badge
 */
@Composable
fun StatusBadge(connectionState: FreedomVpnService.ConnectionState) {
    val isConnected = connectionState == FreedomVpnService.ConnectionState.CONNECTED
    val isConnecting = connectionState == FreedomVpnService.ConnectionState.CONNECTING
    
    val (bgColor, borderColor, textColor) = when (connectionState) {
        FreedomVpnService.ConnectionState.CONNECTED -> 
            Triple(Connected.copy(alpha = 0.2f), Connected.copy(alpha = 0.3f), Connected)
        FreedomVpnService.ConnectionState.CONNECTING -> 
            Triple(Connecting.copy(alpha = 0.2f), Connecting.copy(alpha = 0.3f), Connecting)
        else -> 
            Triple(Disconnected.copy(alpha = 0.2f), Disconnected.copy(alpha = 0.3f), Disconnected)
    }
    
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Animated dot
        val infiniteTransition = rememberInfiniteTransition(label = "dot")
        val dotAlpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dotAlpha"
        )
        
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(textColor.copy(alpha = if (isConnected || isConnecting) dotAlpha else 1f))
        )
        
        Text(
            text = when (connectionState) {
                FreedomVpnService.ConnectionState.CONNECTED -> "Connected"
                FreedomVpnService.ConnectionState.CONNECTING -> "Connecting"
                FreedomVpnService.ConnectionState.DISCONNECTING -> "Disconnecting"
                FreedomVpnService.ConnectionState.ERROR -> "Error"
                else -> "Disconnected"
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

/**
 * Glass Card Component - Glassmorphism Effect
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1e293b).copy(alpha = 0.7f),
                        Color(0xFF0f172a).copy(alpha = 0.5f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.1f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        content()
    }
}

/**
 * Selected Server Card
 */
@Composable
fun SelectedServerCard(server: VpnGateServer) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = server.countryFlag,
                fontSize = 36.sp
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.countryLong,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = server.hostName,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${server.ping}ms",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Connected
                )
                Text(
                    text = server.formattedSpeed,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

/**
 * IP Address Card
 */
@Composable
fun IPAddressCard(
    isConnected: Boolean,
    currentIP: String?,
    isLoading: Boolean
) {
    val borderColor = if (isConnected) Connected.copy(alpha = 0.3f) else Disconnected.copy(alpha = 0.3f)
    
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Public,
                        contentDescription = null,
                        tint = if (isConnected) Connected else Disconnected,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (isConnected) "VPN IP Address" else "Your Real IP",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
                
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Primary,
                        strokeWidth = 2.dp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // IP Display Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0f172a).copy(alpha = 0.5f))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isConnected) Connected.copy(alpha = 0.2f) 
                                else Disconnected.copy(alpha = 0.2f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Dns,
                            contentDescription = null,
                            tint = if (isConnected) Connected else Disconnected,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column {
                        Text(
                            text = currentIP ?: "Detecting...",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isConnected) Connected else Color.White,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                        Text(
                            text = if (isConnected) "🛡️ Protected" else "⚠️ Exposed",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Stats Grid
 */
@Composable
fun StatsGrid(stats: ConnectionStats) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.CloudDownload,
                iconColor = Color(0xFF3b82f6),
                label = "Data Used",
                value = formatBytes(stats.bytesIn + stats.bytesOut)
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.TrendingDown,
                iconColor = Connected,
                label = "Data Saved",
                value = formatBytes((stats.bytesIn * 0.3).toLong())
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Schedule,
                iconColor = Color(0xFF8b5cf6),
                label = "Connected",
                value = formatDuration(stats.connectionDuration)
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Speed,
                iconColor = Color(0xFFf59e0b),
                label = "Speed",
                value = formatSpeed(stats.currentSpeed)
            )
        }
    }
}

/**
 * Individual Stat Card
 */
@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconColor: Color,
    label: String,
    value: String
) {
    GlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

/**
 * Security Features Card
 */
@Composable
fun SecurityFeaturesCard(isConnected: Boolean) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Security Features",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            SecurityFeatureRow(
                icon = Icons.Default.Lock,
                iconColor = Color(0xFF3b82f6),
                label = "Encryption",
                value = "AES-256"
            )
            SecurityFeatureRow(
                icon = Icons.Default.Public,
                iconColor = Color(0xFF8b5cf6),
                label = "Protocol",
                value = "WireGuard"
            )
            SecurityFeatureRow(
                icon = Icons.Default.Compress,
                iconColor = Color(0xFFf59e0b),
                label = "Compression",
                value = if (isConnected) "ON" else "OFF",
                valueColor = if (isConnected) Connected else TextSecondary
            )
            SecurityFeatureRow(
                icon = Icons.Default.VisibilityOff,
                iconColor = Color(0xFF06b6d4),
                label = "Stealth Mode",
                value = "ON",
                valueColor = Connected
            )
        }
    }
}

@Composable
fun SecurityFeatureRow(
    icon: ImageVector,
    iconColor: Color,
    label: String,
    value: String,
    valueColor: Color = Connected
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                fontSize = 14.sp,
                color = TextSecondary
            )
        }
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = valueColor
        )
    }
}

/**
 * Quick Server Selection
 */
@Composable
fun QuickServerSelection(
    servers: List<VpnGateServer>,
    selectedServer: VpnGateServer?,
    onServerSelect: (VpnGateServer) -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Quick Connect",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Text(
                text = "${servers.size} servers",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(servers) { server ->
                QuickServerCard(
                    server = server,
                    isSelected = server == selectedServer,
                    onClick = { onServerSelect(server) }
                )
            }
        }
    }
}

@Composable
fun QuickServerCard(
    server: VpnGateServer,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Primary else Color.Transparent
    
    Box(
        modifier = Modifier
            .width(100.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark.copy(alpha = 0.5f))
            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = server.countryFlag,
                fontSize = 32.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = server.countryShort,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Text(
                text = "${server.ping}ms",
                fontSize = 10.sp,
                color = Connected
            )
        }
    }
}

/**
 * Data class for connection stats
 */
data class ConnectionStats(
    val bytesIn: Long = 0,
    val bytesOut: Long = 0,
    val connectionDuration: Long = 0,
    val currentSpeed: Long = 0
)

// Utility functions
private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, secs)
        else -> String.format("%02d:%02d", minutes, secs)
    }
}

private fun formatSpeed(bytesPerSecond: Long): String {
    return when {
        bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
        bytesPerSecond < 1024 * 1024 -> "${bytesPerSecond / 1024} KB/s"
        else -> String.format("%.1f MB/s", bytesPerSecond / (1024.0 * 1024))
    }
}
