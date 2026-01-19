package com.freedomvpn.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.freedomvpn.ui.theme.*
import kotlin.math.roundToInt

/**
 * Connection statistics data
 */
data class ConnectionStats(
    val downloadSpeed: Float = 0f,    // Mbps
    val uploadSpeed: Float = 0f,      // Mbps
    val totalDownload: Long = 0L,     // bytes
    val totalUpload: Long = 0L,       // bytes
    val connectedTime: Long = 0L,     // seconds
    val ping: Int = 0                 // ms
)

/**
 * Stats display row with download/upload speeds
 */
@Composable
fun StatsRow(
    stats: ConnectionStats,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Download speed
        StatItem(
            icon = Icons.Default.ArrowDownward,
            label = "Download",
            value = formatSpeed(stats.downloadSpeed),
            iconColor = Primary,
            modifier = Modifier.weight(1f)
        )
        
        // Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(50.dp)
                .background(TextDisabled)
        )
        
        // Upload speed
        StatItem(
            icon = Icons.Default.ArrowUpward,
            label = "Upload",
            value = formatSpeed(stats.uploadSpeed),
            iconColor = Secondary,
            modifier = Modifier.weight(1f)
        )
        
        // Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(50.dp)
                .background(TextDisabled)
        )
        
        // Ping
        StatItem(
            icon = Icons.Default.Speed,
            label = "Ping",
            value = "${stats.ping} ms",
            iconColor = getPingColor(stats.ping),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
    label: String,
    value: String,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

/**
 * Session info card showing connected time and data usage
 */
@Composable
fun SessionInfoCard(
    stats: ConnectionStats,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Connected time
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = "Time",
                tint = Primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatDuration(stats.connectedTime),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Session Time",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
        
        // Total download
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowDownward,
                contentDescription = "Downloaded",
                tint = Primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatDataSize(stats.totalDownload),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Downloaded",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
        
        // Total upload
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowUpward,
                contentDescription = "Uploaded",
                tint = Secondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatDataSize(stats.totalUpload),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Uploaded",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

/**
 * Animated speed graph showing real-time bandwidth
 */
@Composable
fun SpeedGraph(
    speedHistory: List<Float>,
    maxSpeed: Float,
    color: Color = Primary,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark.copy(alpha = 0.5f))
    ) {
        if (speedHistory.isEmpty()) return@Canvas
        
        val width = size.width
        val height = size.height
        val stepX = width / (speedHistory.size - 1).coerceAtLeast(1)
        val effectiveMax = maxSpeed.coerceAtLeast(1f)
        
        // Draw grid lines
        for (i in 1..3) {
            val y = height * i / 4
            drawLine(
                color = TextDisabled.copy(alpha = 0.2f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1.dp.toPx()
            )
        }
        
        // Draw speed line
        for (i in 0 until speedHistory.size - 1) {
            val x1 = i * stepX
            val x2 = (i + 1) * stepX
            val y1 = height - (speedHistory[i] / effectiveMax * height).coerceIn(0f, height)
            val y2 = height - (speedHistory[i + 1] / effectiveMax * height).coerceIn(0f, height)
            
            drawLine(
                color = color,
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

// Helper functions
private fun formatSpeed(mbps: Float): String {
    return when {
        mbps >= 1000 -> "${(mbps / 1000).roundToOneDecimal()} Gbps"
        mbps >= 1 -> "${mbps.roundToOneDecimal()} Mbps"
        else -> "${(mbps * 1000).roundToInt()} Kbps"
    }
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, secs)
}

private fun formatDataSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> "${(bytes / 1_000_000_000.0).roundToOneDecimal()} GB"
        bytes >= 1_000_000 -> "${(bytes / 1_000_000.0).roundToOneDecimal()} MB"
        bytes >= 1000 -> "${(bytes / 1000.0).roundToOneDecimal()} KB"
        else -> "$bytes B"
    }
}

private fun Float.roundToOneDecimal(): String {
    return String.format("%.1f", this)
}

private fun Double.roundToOneDecimal(): String {
    return String.format("%.1f", this)
}

private fun getPingColor(ping: Int): Color {
    return when {
        ping < 50 -> PingExcellent
        ping < 100 -> PingGood
        ping < 200 -> PingFair
        else -> PingPoor
    }
}
