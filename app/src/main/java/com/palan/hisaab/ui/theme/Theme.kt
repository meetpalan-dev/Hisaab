package com.palan.hisaab.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Hisaab palette — ink + gold, matches the हिसाब wordmark.
// Each surface tier is a step lighter than the last, so stacked cards/sheets/rows
// read as distinct tonal layers instead of identical dark rectangles.
val Ink = Color(0xFF0F1117)                 // background — darkest
val InkSurface = Color(0xFF15171F)          // surface
val InkSurfaceContainerLow = Color(0xFF191C25)
val InkSurfaceContainer = Color(0xFF1E212B)
val InkSurfaceContainerHigh = Color(0xFF262A36)
val InkSurfaceContainerHighest = Color(0xFF2F3341)
val InkSurfaceVariant = Color(0xFF232733)   // kept for any remaining callers
val InkSurfaceElevated = InkSurfaceContainerHigh // kept for any remaining callers
val Gold = Color(0xFFDFA859)
val GoldSoft = Color(0xFFE8BC7A)
val Cream = Color(0xFFF0EADE)
val GreenReceived = Color(0xFF3DBE7A)
val RedSpent = Color(0xFFE0604F)
val MutedText = Color(0xFF9AA0AE)
val Outline = Color(0xFF3A3F4D)

private val HisaabDarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = Ink,
    primaryContainer = Color(0xFF4A3413),
    onPrimaryContainer = GoldSoft,
    secondary = GoldSoft,
    onSecondary = Ink,
    background = Ink,
    onBackground = Cream,
    surface = InkSurface,
    onSurface = Cream,
    surfaceVariant = InkSurfaceContainer,
    onSurfaceVariant = MutedText,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = InkSurfaceContainerLow,
    surfaceContainer = InkSurfaceContainer,
    surfaceContainerHigh = InkSurfaceContainerHigh,
    surfaceContainerHighest = InkSurfaceContainerHighest,
    outline = Outline,
    outlineVariant = Color(0xFF2A2E39),
    error = RedSpent,
    onError = Ink,
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
    useMaterialYou: Boolean = false,
    // Default is always-dark per the app's design, regardless of system setting.
    // Only when Material You is on do we follow the system light/dark setting,
    // since dynamic color is meant to react to the device's whole appearance.
    darkTheme: Boolean = if (useMaterialYou) isSystemInDarkTheme() else true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = when {
        useMaterialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> HisaabDarkColors
        else -> HisaabLightColors
    }
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

// Consistent spacing scale used across the redesigned screens.
object Spacing {
    val tight = 8.dp
    val internal = 12.dp
    val normal = 16.dp
    val section = 24.dp
}
