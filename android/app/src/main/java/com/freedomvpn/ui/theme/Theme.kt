package com.freedomvpn.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Primary colors
val Blue500 = Color(0xFF1E88E5)
val Blue700 = Color(0xFF1565C0)
val Blue200 = Color(0xFF64B5F6)

// Accent colors
val Green500 = Color(0xFF4CAF50)
val Green700 = Color(0xFF388E3C)

// Background colors
val BackgroundLight = Color(0xFFF5F5F5)
val SurfaceLight = Color(0xFFFFFFFF)
val BackgroundDark = Color(0xFF121212)
val SurfaceDark = Color(0xFF1E1E1E)

// Status colors
val Connected = Color(0xFF4CAF50)
val Disconnected = Color(0xFF9E9E9E)
val Connecting = Color(0xFFFFC107)
val Error = Color(0xFFD32F2F)

private val LightColorScheme = lightColorScheme(
    primary = Blue500,
    onPrimary = Color.White,
    primaryContainer = Blue200,
    onPrimaryContainer = Blue700,
    secondary = Green500,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8E6C9),
    onSecondaryContainer = Green700,
    background = BackgroundLight,
    onBackground = Color(0xFF212121),
    surface = SurfaceLight,
    onSurface = Color(0xFF212121),
    surfaceVariant = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFF616161),
    error = Error,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = Blue200,
    onPrimary = Blue700,
    primaryContainer = Blue700,
    onPrimaryContainer = Blue200,
    secondary = Color(0xFF81C784),
    onSecondary = Green700,
    secondaryContainer = Green700,
    onSecondaryContainer = Color(0xFFC8E6C9),
    background = BackgroundDark,
    onBackground = Color.White,
    surface = SurfaceDark,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2D2D2D),
    onSurfaceVariant = Color(0xFFBDBDBD),
    error = Color(0xFFEF5350),
    onError = Color.White
)

@Composable
fun FreedomVPNTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
