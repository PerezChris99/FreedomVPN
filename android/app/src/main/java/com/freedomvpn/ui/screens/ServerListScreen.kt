package com.freedomvpn.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.freedomvpn.ui.theme.*
import com.freedomvpn.viewmodel.ServerListViewModel
import com.freedomvpn.data.model.VpnServer

/**
 * Server tab categories
 */
enum class ServerTab {
    ALL, FAVORITES, RECENT
}

/**
 * Server list screen for browsing and selecting VPN servers
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(
    viewModel: ServerListViewModel = hiltViewModel(),
    onServerSelected: (VpnServer) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val servers by viewModel.servers.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val recents by viewModel.recents.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val serverPings by viewModel.serverPings.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Server", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshServers() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary,
                    actionIconContentColor = TextPrimary
                )
            )
        },
        containerColor = BackgroundDark
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            SearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            // Tab row
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = BackgroundDark,
                contentColor = Primary,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                        color = Primary
                    )
                }
            ) {
                ServerTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { viewModel.setSelectedTab(tab) },
                        text = {
                            Text(
                                text = when (tab) {
                                    ServerTab.ALL -> "All Servers"
                                    ServerTab.FAVORITES -> "Favorites"
                                    ServerTab.RECENT -> "Recent"
                                },
                                color = if (selectedTab == tab) Primary else TextSecondary
                            )
                        }
                    )
                }
            }
            
            // Server list
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Primary)
                }
            } else {
                val displayServers = when (selectedTab) {
                    ServerTab.ALL -> servers
                    ServerTab.FAVORITES -> favorites
                    ServerTab.RECENT -> recents
                }
                
                if (displayServers.isEmpty()) {
                    EmptyServerList(selectedTab)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Group by country
                        val groupedServers = displayServers.groupBy { it.countryLong }
                        
                        groupedServers.forEach { (country, countryServers) ->
                            item {
                                CountryHeader(
                                    country = country,
                                    serverCount = countryServers.size,
                                    flag = countryServers.firstOrNull()?.countryShort
                                )
                            }
                            
                            items(countryServers) { server ->
                                ServerItem(
                                    server = server,
                                    ping = serverPings[server.id],
                                    isFavorite = favorites.any { it.id == server.id },
                                    onSelect = { 
                                        viewModel.addToRecent(server)
                                        onServerSelected(server) 
                                    },
                                    onToggleFavorite = { viewModel.toggleFavorite(server) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text("Search servers...", color = TextSecondary)
        },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                }
            }
        },
        colors = TextFieldDefaults.textFieldColors(
            containerColor = SurfaceDark,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = Primary,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun CountryHeader(
    country: String,
    serverCount: Int,
    flag: String?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (flag != null) {
            Text(
                text = countryCodeToEmoji(flag),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = country,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "($serverCount)",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun ServerItem(
    server: VpnServer,
    ping: Int?,
    isFavorite: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .clickable(onClick = onSelect)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Server icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceDarkElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Dns,
                contentDescription = "Server",
                tint = Primary,
                modifier = Modifier.size(24.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Server info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = server.hostname ?: server.ip,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Speed badge
                SpeedBadge(speedMbps = server.speed / 1_000_000f)
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Ping if available
                if (ping != null) {
                    PingBadge(ping = ping)
                }
            }
        }
        
        // Favorite button
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (isFavorite) Disconnected else TextSecondary
            )
        }
    }
}

@Composable
private fun SpeedBadge(speedMbps: Float) {
    val color = when {
        speedMbps >= 50 -> SpeedExcellent
        speedMbps >= 20 -> SpeedGood
        speedMbps >= 5 -> SpeedFair
        else -> SpeedPoor
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Icon(
            Icons.Default.Speed,
            contentDescription = "Speed",
            tint = color,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "${speedMbps.toInt()} Mbps",
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun PingBadge(ping: Int) {
    val color = when {
        ping < 50 -> PingExcellent
        ping < 100 -> PingGood
        ping < 200 -> PingFair
        else -> PingPoor
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "${ping}ms",
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun EmptyServerList(tab: ServerTab) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                when (tab) {
                    ServerTab.ALL -> Icons.Default.CloudOff
                    ServerTab.FAVORITES -> Icons.Default.FavoriteBorder
                    ServerTab.RECENT -> Icons.Default.History
                },
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = when (tab) {
                    ServerTab.ALL -> "No servers found"
                    ServerTab.FAVORITES -> "No favorite servers"
                    ServerTab.RECENT -> "No recent connections"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
        }
    }
}

private fun countryCodeToEmoji(countryCode: String): String {
    if (countryCode.length != 2) return "🌍"
    val firstChar = Character.codePointAt(countryCode.uppercase(), 0) - 0x41 + 0x1F1E6
    val secondChar = Character.codePointAt(countryCode.uppercase(), 1) - 0x41 + 0x1F1E6
    return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
}
