package com.freedomvpn.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedomvpn.ui.theme.*

/**
 * VPN Connection states
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING
}

/**
 * Main power button for VPN connection
 */
@Composable
fun PowerButton(
    connectionState: ConnectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Animated scale when pressed
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "buttonScale"
    )
    
    // Ring animation for connecting state
    val infiniteTransition = rememberInfiniteTransition(label = "connectingRing")
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringRotation"
    )
    
    // Pulse animation for connected state
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    // Color based on state
    val buttonColor by animateColorAsState(
        targetValue = when (connectionState) {
            ConnectionState.CONNECTED -> Connected
            ConnectionState.CONNECTING, ConnectionState.DISCONNECTING -> Connecting
            ConnectionState.DISCONNECTED -> Primary
        },
        animationSpec = tween(300),
        label = "buttonColor"
    )
    
    val outerRingColor by animateColorAsState(
        targetValue = when (connectionState) {
            ConnectionState.CONNECTED -> Connected.copy(alpha = 0.3f)
            ConnectionState.CONNECTING, ConnectionState.DISCONNECTING -> Connecting.copy(alpha = 0.3f)
            ConnectionState.DISCONNECTED -> Primary.copy(alpha = 0.2f)
        },
        animationSpec = tween(300),
        label = "outerRingColor"
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(scale * if (connectionState == ConnectionState.CONNECTED) pulse else 1f),
        contentAlignment = Alignment.Center
    ) {
        // Outer glow ring
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        outerRingColor,
                        outerRingColor.copy(alpha = 0.1f),
                        Color.Transparent
                    )
                ),
                radius = size.toPx() / 2
            )
        }
        
        // Connecting ring animation
        if (connectionState == ConnectionState.CONNECTING || 
            connectionState == ConnectionState.DISCONNECTING) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                drawArc(
                    color = buttonColor,
                    startAngle = ringRotation,
                    sweepAngle = 90f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        
        // Main button
        Box(
            modifier = Modifier
                .size(size - 48.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            SurfaceDark,
                            SurfaceDarkElevated
                        )
                    )
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = rememberRipple(bounded = true, color = buttonColor)
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            // Power icon
            PowerIcon(
                color = buttonColor,
                modifier = Modifier.size(64.dp)
            )
        }
        
        // Inner ring
        Canvas(
            modifier = Modifier
                .size(size - 40.dp)
        ) {
            drawCircle(
                color = buttonColor,
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}

/**
 * Custom power icon drawn with Canvas
 */
@Composable
private fun PowerIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 6.dp.toPx()
        val centerX = size.width / 2
        val centerY = size.height / 2
        val radius = size.minDimension / 2.5f
        
        // Draw arc (power symbol circle part)
        drawArc(
            color = color,
            startAngle = -60f,
            sweepAngle = 300f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            topLeft = Offset(centerX - radius, centerY - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
        )
        
        // Draw vertical line
        drawLine(
            color = color,
            start = Offset(centerX, centerY - radius),
            end = Offset(centerX, centerY - radius * 0.2f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Connection status text with animation
 */
@Composable
fun ConnectionStatusText(
    connectionState: ConnectionState,
    serverName: String?,
    modifier: Modifier = Modifier
) {
    val statusText = when (connectionState) {
        ConnectionState.DISCONNECTED -> "Not Protected"
        ConnectionState.CONNECTING -> "Connecting..."
        ConnectionState.CONNECTED -> "Protected"
        ConnectionState.DISCONNECTING -> "Disconnecting..."
    }
    
    val statusColor by animateColorAsState(
        targetValue = when (connectionState) {
            ConnectionState.CONNECTED -> Connected
            ConnectionState.CONNECTING, ConnectionState.DISCONNECTING -> Connecting
            ConnectionState.DISCONNECTED -> Disconnected
        },
        animationSpec = tween(300),
        label = "statusColor"
    )
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = statusColor
        )
        
        if (connectionState == ConnectionState.CONNECTED && serverName != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Connected to $serverName",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

/**
 * Shield icon that changes based on protection status
 */
@Composable
fun ProtectionShield(
    isProtected: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp
) {
    val shieldColor by animateColorAsState(
        targetValue = if (isProtected) Connected else ShieldInactive,
        animationSpec = tween(500),
        label = "shieldColor"
    )
    
    Canvas(modifier = modifier.size(size)) {
        val width = size.toPx()
        val height = size.toPx()
        
        // Shield path
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(width / 2, 0f)
            lineTo(width, height * 0.25f)
            lineTo(width, height * 0.55f)
            quadraticBezierTo(width, height, width / 2, height)
            quadraticBezierTo(0f, height, 0f, height * 0.55f)
            lineTo(0f, height * 0.25f)
            close()
        }
        
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = listOf(
                    shieldColor,
                    shieldColor.copy(alpha = 0.7f)
                )
            )
        )
    }
}
