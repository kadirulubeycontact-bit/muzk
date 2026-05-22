package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Color definitions matching Siyah/Antrasit + Soft Emerald Green
val MasivaBlack = Color(0xFF070707)       // Pitch black backdrop
val MasivaCharcoal = Color(0xFF0F110F)    // Dark antrasit card
val MasivaGrey = Color(0xFF171A18)        // Elevated glass-look cards
val MasivaGlassBorder = Color(0x3310B981) // Translucent emerald border
val MasivaEmerald = Color(0xFF10B981)      // Soft emerald green primary
val MasivaMint = Color(0xFF34D399)         // Glowing secondary highlight
val MasivaMutedText = Color(0xFF9EABA2)    // Slate green text color

private val DarkColorScheme = darkColorScheme(
    primary = MasivaEmerald,
    onPrimary = Color.Black,
    primaryContainer = Color(0x1E10B981),
    onPrimaryContainer = MasivaMint,
    secondary = MasivaMint,
    onSecondary = Color.Black,
    background = MasivaBlack,
    onBackground = Color.White,
    surface = MasivaCharcoal,
    onSurface = Color.White,
    surfaceVariant = MasivaGrey,
    onSurfaceVariant = MasivaMutedText
)

val MasivaTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun MasivaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = MasivaTypography,
        content = content
    )
}
