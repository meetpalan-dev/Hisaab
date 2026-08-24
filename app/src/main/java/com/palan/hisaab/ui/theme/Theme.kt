package com.palan.hisaab.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Hisaab palette — ink + gold, matches the हिसाब wordmark
val Ink = Color(0xFF12141C)
val InkSurface = Color(0xFF1B1E28)
val InkSurfaceElevated = Color(0xFF232733)
val Gold = Color(0xFFDFA859)
val GoldSoft = Color(0xFFE8BC7A)
val Cream = Color(0xFFF0EADE)
val GreenReceived = Color(0xFF3DBE7A)
val RedSpent = Color(0xFFE0604F)
val MutedText = Color(0xFF9AA0AE)

private val HisaabDarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = Ink,
    secondary = GoldSoft,
    background = Ink,
    onBackground = Cream,
    surface = InkSurface,
    onSurface = Cream,
    surfaceVariant = InkSurfaceElevated,
    onSurfaceVariant = MutedText,
    error = RedSpent,
)

private val HisaabLightColors = lightColorScheme(
    primary = Color(0xFF8A5A20),
    onPrimary = Color.White,
    background = Cream,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
)

@Composable
fun HisaabTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Default to dark theme regardless of system setting, per product spec.
    val colors = HisaabDarkColors
    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography.copy(
            headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        ),
        content = content
    )
}

val BalanceTextStyle = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold)
