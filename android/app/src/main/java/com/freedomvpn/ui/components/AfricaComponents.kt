package com.freedomvpn.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedomvpn.localization.LanguageManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Panic Button Component
 * 
 * Big, obvious panic button for emergency situations.
 * Long press to trigger panic mode.
 */
@Composable
fun PanicButton(
    onPanicTrigger: () -> Unit,
    languageManager: LanguageManager,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(100)
    )

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )

    val borderColor = if (progress > 0) {
        Color(0xFFFF5252).copy(alpha = 0.5f + progress * 0.5f)
    } else {
        Color(0xFFFF5252).copy(alpha = 0.3f)
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            // Long press detection - 1 second hold
            while (isPressed && progress < 1f) {
                delay(50)
                progress += 0.05f
                if (progress >= 0.5f && progress < 0.55f) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
            if (progress >= 1f) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onPanicTrigger()
            }
            progress = 0f
        } else {
            progress = 0f
        }
    }

    Box(
        modifier = modifier
            .scale(scale)
            .size(100.dp)
            .clip(CircleShape)
            .border(
                width = 3.dp,
                brush = Brush.sweepGradient(
                    0f to borderColor,
                    animatedProgress to Color(0xFFFF5252),
                    animatedProgress + 0.01f to borderColor,
                    1f to borderColor
                ),
                shape = CircleShape
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFF5252).copy(alpha = 0.3f),
                        Color(0xFFFF5252).copy(alpha = 0.1f)
                    )
                )
            )
            .clickable(
                onClick = { },
                onClickLabel = "Panic Button"
            ),
        contentAlignment = Alignment.Center
    ) {
        // Progress ring
        if (progress > 0) {
            CircularProgressIndicator(
                progress = animatedProgress,
                modifier = Modifier.size(90.dp),
                color = Color(0xFFFF5252),
                strokeWidth = 4.dp
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Panic",
                tint = Color(0xFFFF5252),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when (languageManager.currentLanguage.value) {
                    LanguageManager.Language.SWAHILI -> "DHARURA"
                    LanguageManager.Language.LUGANDA -> "BWANGU"
                    LanguageManager.Language.FRENCH -> "PANIQUE"
                    else -> "PANIC"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF5252)
            )
        }
    }

    // Instruction text
    if (progress > 0) {
        Text(
            text = when (languageManager.currentLanguage.value) {
                LanguageManager.Language.SWAHILI -> "Shikilia ili kuanzisha..."
                LanguageManager.Language.LUGANDA -> "Nyigira okusobola..."
                LanguageManager.Language.FRENCH -> "Maintenez pour déclencher..."
                else -> "Hold to trigger..."
            },
            fontSize = 12.sp,
            color = Color(0xFFFF5252).copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/**
 * Data Savings Card
 * 
 * Shows how much data (and money) user has saved
 */
@Composable
fun DataSavingsCard(
    dataSavedMB: Float,
    moneySavedUGX: Float,
    compressionRatio: Float,
    languageManager: LanguageManager,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1B263B)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Data saved
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "%.1f MB".format(dataSavedMB),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = languageManager["data_saved"],
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(50.dp)
                    .background(Color.White.copy(alpha = 0.2f))
            )

            // Money saved (in UGX)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Savings,
                    contentDescription = null,
                    tint = Color(0xFFFFD54F),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${moneySavedUGX.toInt()} UGX",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = when (languageManager.currentLanguage.value) {
                        LanguageManager.Language.SWAHILI -> "Iliyookolewa"
                        LanguageManager.Language.LUGANDA -> "Byeterekeddwa"
                        LanguageManager.Language.FRENCH -> "Économisé"
                        else -> "Saved"
                    },
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(50.dp)
                    .background(Color.White.copy(alpha = 0.2f))
            )

            // Compression ratio
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Compress,
                    contentDescription = null,
                    tint = Color(0xFF00D9FF),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${compressionRatio.toInt()}%",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = when (languageManager.currentLanguage.value) {
                        LanguageManager.Language.SWAHILI -> "Imepunguzwa"
                        LanguageManager.Language.LUGANDA -> "Byenyigiddwa"
                        LanguageManager.Language.FRENCH -> "Compressé"
                        else -> "Compressed"
                    },
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * Network Quality Indicator
 * 
 * Shows current network quality with visual indicator
 */
@Composable
fun NetworkQualityIndicator(
    quality: String,
    expectedSpeed: String,
    modifier: Modifier = Modifier
) {
    val (color, icon) = when {
        quality.contains("EXCELLENT") || quality.contains("GOOD") -> 
            Color(0xFF00E676) to Icons.Default.SignalCellular4Bar
        quality.contains("MODERATE") || quality.contains("SLOW") -> 
            Color(0xFFFFD54F) to Icons.Default.SignalCellularAlt
        else -> 
            Color(0xFFFF5252) to Icons.Default.SignalCellularConnectedNoInternet0Bar
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = quality.replace("_", " "),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = color
            )
            Text(
                text = expectedSpeed,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * Connection Status Banner
 * 
 * Shows shutdown detection and warnings
 */
@Composable
fun ConnectionStatusBanner(
    status: String,
    suggestions: List<String>,
    isWarning: Boolean,
    languageManager: LanguageManager,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isWarning) {
        Color(0xFFFF5252).copy(alpha = 0.1f)
    } else {
        Color(0xFF00E676).copy(alpha = 0.1f)
    }

    val iconColor = if (isWarning) Color(0xFFFF5252) else Color(0xFF00E676)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isWarning) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = status,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }

            if (suggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                suggestions.forEach { suggestion ->
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "• ",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = suggestion,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Quick Settings Tile
 * 
 * Simple toggle for common settings
 */
@Composable
fun QuickSettingTile(
    title: String,
    description: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) {
                Color(0xFF00D9FF).copy(alpha = 0.1f)
            } else {
                Color(0xFF1B263B)
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(!isEnabled) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isEnabled) Color(0xFF00D9FF).copy(alpha = 0.2f)
                        else Color.White.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF00D9FF),
                    checkedTrackColor = Color(0xFF00D9FF).copy(alpha = 0.3f),
                    uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                )
            )
        }
    }
}
