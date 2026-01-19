package com.freedomvpn.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedomvpn.vpn.FreedomVpnService
import com.freedomvpn.vpngate.ServerRegion
import com.freedomvpn.vpngate.ServerSortOption
import com.freedomvpn.vpngate.VpnGateServer

/**
 * Main screen composable for FreedomVPN
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onConnectClick: (VpnGateServer?) -> Unit,
    onDisconnectClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showServerList by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FreedomVPN") },
                actions = {
                    IconButton(onClick = { viewModel.loadServers(forceRefresh = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Connection Status Card
            ConnectionStatusCard(
                connectionState = uiState.connectionState,
                selectedServer = uiState.selectedServer,
                onConnectClick = { 
                    if (uiState.connectionState == FreedomVpnService.ConnectionState.CONNECTED) {
                        onDisconnectClick()
                    } else {
                        onConnectClick(uiState.selectedServer)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Selected Server Card
            SelectedServerCard(
                server = uiState.selectedServer,
                onChangeServer = { showServerList = true },
                onAutoSelect = { viewModel.autoSelectBestServer() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Server List
            if (showServerList) {
                ServerListSection(
                    servers = uiState.servers,
                    isLoading = uiState.isLoading,
                    error = uiState.error,
                    onServerClick = { server ->
                        viewModel.selectServer(server)
                        showServerList = false
                    },
                    onRetry = { viewModel.loadServers(forceRefresh = true) }
                )
            } else {
                // Quick Stats
                QuickStatsCard(
                    serverCount = uiState.servers.size,
                    connectionState = uiState.connectionState
                )
            }
        }
    }

    // Filter Bottom Sheet
    if (showFilterSheet) {
        FilterBottomSheet(
            currentRegion = uiState.selectedRegion,
            currentSort = uiState.sortOption,
            onRegionSelected = { viewModel.setRegion(it) },
            onSortSelected = { viewModel.setSortOption(it) },
            onDismiss = { showFilterSheet = false }
        )
    }

    // Error Snackbar
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // Show snackbar
        }
    }
}

/**
 * Connection status card with connect button
 */
@Composable
fun ConnectionStatusCard(
    connectionState: FreedomVpnService.ConnectionState,
    selectedServer: VpnGateServer?,
    onConnectClick: () -> Unit
) {
    val isConnected = connectionState == FreedomVpnService.ConnectionState.CONNECTED
    val isConnecting = connectionState == FreedomVpnService.ConnectionState.CONNECTING
    
    val gradientColors = when (connectionState) {
        FreedomVpnService.ConnectionState.CONNECTED -> listOf(Color(0xFF4CAF50), Color(0xFF2E7D32))
        FreedomVpnService.ConnectionState.CONNECTING -> listOf(Color(0xFFFFC107), Color(0xFFF57C00))
        FreedomVpnService.ConnectionState.DISCONNECTING -> listOf(Color(0xFFFF9800), Color(0xFFE65100))
        else -> listOf(Color(0xFF9E9E9E), Color(0xFF616161))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(gradientColors))
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Status Icon
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable { onConnectClick() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = if (isConnected) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Status Text
                Text(
                    text = when (connectionState) {
                        FreedomVpnService.ConnectionState.CONNECTED -> "Protected"
                        FreedomVpnService.ConnectionState.CONNECTING -> "Connecting..."
                        FreedomVpnService.ConnectionState.DISCONNECTING -> "Disconnecting..."
                        FreedomVpnService.ConnectionState.ERROR -> "Connection Error"
                        else -> "Not Protected"
                    },
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                if (isConnected && selectedServer != null) {
                    Text(
                        text = "${selectedServer.countryFlag} ${selectedServer.countryLong}",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Connect/Disconnect Button
                Button(
                    onClick = onConnectClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = gradientColors[0]
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.width(200.dp)
                ) {
                    Text(
                        text = if (isConnected) "Disconnect" else "Connect",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Selected server card
 */
@Composable
fun SelectedServerCard(
    server: VpnGateServer?,
    onChangeServer: () -> Unit,
    onAutoSelect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Selected Server",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                
                TextButton(onClick = onAutoSelect) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Auto Select")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (server != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onChangeServer() }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = server.countryFlag,
                        fontSize = 32.sp
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = server.countryLong,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${server.formattedSpeed} • ${server.ping}ms",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            } else {
                OutlinedButton(
                    onClick = onChangeServer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select a Server")
                }
            }
        }
    }
}

/**
 * Server list section
 */
@Composable
fun ServerListSection(
    servers: List<VpnGateServer>,
    isLoading: Boolean,
    error: String?,
    onServerClick: (VpnGateServer) -> Unit,
    onRetry: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Available Servers (${servers.size})",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            }
            servers.isEmpty() -> {
                Text(
                    text = "No servers available",
                    modifier = Modifier.padding(32.dp)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(servers.take(20)) { server ->
                        ServerListItem(
                            server = server,
                            onClick = { onServerClick(server) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual server list item
 */
@Composable
fun ServerListItem(
    server: VpnGateServer,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = server.countryFlag,
                fontSize = 28.sp
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.countryLong,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = server.hostName,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = server.formattedSpeed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${server.ping}ms",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Quick stats card
 */
@Composable
fun QuickStatsCard(
    serverCount: Int,
    connectionState: FreedomVpnService.ConnectionState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                icon = Icons.Default.Public,
                value = serverCount.toString(),
                label = "Servers"
            )
            StatItem(
                icon = Icons.Default.Speed,
                value = "∞",
                label = "Bandwidth"
            )
            StatItem(
                icon = Icons.Default.Security,
                value = "WireGuard",
                label = "Protocol"
            )
        }
    }
}

@Composable
fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Filter bottom sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    currentRegion: ServerRegion,
    currentSort: ServerSortOption,
    onRegionSelected: (ServerRegion) -> Unit,
    onSortSelected: (ServerSortOption) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Filter Servers",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Region", fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            
            ServerRegion.values().forEach { region ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRegionSelected(region) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = region == currentRegion,
                        onClick = { onRegionSelected(region) }
                    )
                    Text(region.displayName)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Sort By", fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            
            ServerSortOption.values().forEach { sort ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSortSelected(sort) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = sort == currentSort,
                        onClick = { onSortSelected(sort) }
                    )
                    Text(sort.displayName)
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
