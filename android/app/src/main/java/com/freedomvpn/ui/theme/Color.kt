package com.freedomvpn.ui.theme

import androidx.compose.ui.graphics.Color

// Primary Colors - Deep blues and cyans for trust/security feel
val Primary = Color(0xFF00D9FF)        // Bright cyan - main accent
val PrimaryDark = Color(0xFF00A8CC)    // Darker cyan for pressed states
val PrimaryLight = Color(0xFF4DE8FF)   // Light cyan for highlights

// Secondary Colors - Purple accents for modern feel
val Secondary = Color(0xFF7C4DFF)      // Electric purple
val SecondaryDark = Color(0xFF651FFF)  // Deep purple
val SecondaryLight = Color(0xFFB388FF) // Light purple

// Background Colors - Dark theme
val BackgroundDark = Color(0xFF0A0E14)       // Almost black with blue tint
val BackgroundDarkSecondary = Color(0xFF121820) // Slightly lighter
val SurfaceDark = Color(0xFF1A2230)          // Card/surface color
val SurfaceDarkElevated = Color(0xFF232D3F)  // Elevated surfaces

// Status Colors
val Connected = Color(0xFF00E676)      // Bright green - connected
val Connecting = Color(0xFFFFAB00)     // Amber - connecting
val Disconnected = Color(0xFFFF5252)   // Red - disconnected
val Warning = Color(0xFFFF9100)        // Orange - warning

// Text Colors
val TextPrimary = Color(0xFFFFFFFF)    // White
val TextSecondary = Color(0xFFB0BEC5)  // Light gray
val TextDisabled = Color(0xFF546E7A)   // Dark gray
val TextOnPrimary = Color(0xFF0A0E14)  // Dark on bright backgrounds

// Gradient Colors
val GradientStart = Color(0xFF00D9FF)  // Cyan start
val GradientMiddle = Color(0xFF7C4DFF) // Purple middle
val GradientEnd = Color(0xFFFF00FF)    // Magenta end

// Country Flag Colors (for accent dots)
val FlagRed = Color(0xFFE53935)
val FlagGreen = Color(0xFF43A047)
val FlagBlue = Color(0xFF1E88E5)
val FlagYellow = Color(0xFFFDD835)
val FlagOrange = Color(0xFFFF9800)

// Speed Indicator Colors
val SpeedExcellent = Color(0xFF00E676) // 50+ Mbps
val SpeedGood = Color(0xFF4CAF50)      // 20-50 Mbps
val SpeedFair = Color(0xFFFFEB3B)      // 5-20 Mbps
val SpeedPoor = Color(0xFFFF5722)      // < 5 Mbps

// Ping Indicator Colors  
val PingExcellent = Color(0xFF00E676)  // < 50ms
val PingGood = Color(0xFF4CAF50)       // 50-100ms
val PingFair = Color(0xFFFFEB3B)       // 100-200ms
val PingPoor = Color(0xFFFF5722)       // > 200ms

// Uganda Colors (for special branding)
val UgandaBlack = Color(0xFF000000)
val UgandaYellow = Color(0xFFFCDC04)
val UgandaRed = Color(0xFFD90000)

// Shield/Security Icon Colors
val ShieldActive = Primary
val ShieldInactive = Color(0xFF37474F)
val ShieldProtected = Connected
val ShieldWarning = Warning
