package com.freedomvpn.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.freedomvpn.ui.components.*
import com.freedomvpn.ui.theme.*
import com.freedomvpn.viewmodel.MainViewModel

/**
 * Main connection screen - the primary UI for VPN control
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateToServers: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val currentServer by viewModel.currentServer.collectAsState()
    val stats by viewModel.connectionStats.collectAsState()
    val speedHistory by viewModel.speedHistory.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "FreedomVPN",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = BackgroundDark
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            BackgroundDark,
                            BackgroundDarkSecondary
                        )
                    )
                )
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Connection status text
            ConnectionStatusText(
                connectionState = connectionState,
                serverName = currentServer?.countryLong
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Main power button
            PowerButton(
                connectionState = connectionState,
                onClick = { viewModel.toggleConnection() },
                size = 220.dp
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Server selection card
            ServerSelectionCard(
                serverName = currentServer?.countryLong ?: "Select Server",
                serverIp = currentServer?.ip ?: "Tap to choose",
                flag = currentServer?.countryShort,
                onClick = onNavigateToServers
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Stats display (only visible when connected)
            AnimatedVisibility(
                visible = connectionState == ConnectionState.CONNECTED,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    StatsRow(stats = stats)
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Speed graph
                    Text(
                        text = "Network Activity",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                    SpeedGraph(
                        speedHistory = speedHistory,
                        maxSpeed = speedHistory.maxOrNull() ?: 10f,
                        color = Primary
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    SessionInfoCard(stats = stats)
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Quick connect hint (when disconnected)
            AnimatedVisibility(
                visible = connectionState == ConnectionState.DISCONNECTED,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                QuickConnectHint(
                    onQuickConnect = { viewModel.quickConnect() }
                )
            }
        }
    }
}

/**
 * Server selection card
 */
@Composable
private fun ServerSelectionCard(
    serverName: String,
    serverIp: String,
    flag: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Flag emoji or icon
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceDarkElevated),
            contentAlignment = Alignment.Center
        ) {
            if (flag != null) {
                Text(
                    text = countryCodeToEmoji(flag),
                    style = MaterialTheme.typography.headlineMedium
                )
            } else {
                Icon(
                    Icons.Default.Public,
                    contentDescription = "Server",
                    tint = TextSecondary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = serverName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = serverIp,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = "Select",
            tint = TextSecondary
        )
    }
}

/**
 * Quick connect hint at bottom
 */
@Composable
private fun QuickConnectHint(
    onQuickConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark.copy(alpha = 0.7f))
            .clickable(onClick = onQuickConnect)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Bolt,
            contentDescription = "Quick Connect",
            tint = Primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Quick Connect",
            style = MaterialTheme.typography.labelLarge,
            color = Primary,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = " - Auto-select fastest server",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
    }
}

/**
 * Convert country code to flag emoji
 */
private fun countryCodeToEmoji(countryCode: String): String {
    if (countryCode.length != 2) return "🌍"
    val firstChar = Character.codePointAt(countryCode.uppercase(), 0) - 0x41 + 0x1F1E6
    val secondChar = Character.codePointAt(countryCode.uppercase(), 1) - 0x41 + 0x1F1E6
    return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
}
