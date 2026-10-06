package com.justplay.pos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgDark = Color(0xFF0F111A)
val CardDark = Color(0xFF1A1C29)
val SurfaceDark = Color(0xFF24283B)
val BorderDark = Color(0xFF333852)

val AccentLime = Color(0xFFDEFF9A)
val AccentLimeDark = Color(0xFFB5E655)
val AccentGreen = Color(0xFF00E676)
val AlertRed = Color(0xFFFF5252)
val WarningYellow = Color(0xFFFFC107)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFA0A3BD)
val TextMuted = Color(0xFF6B708D)

private val DarkColorScheme = darkColorScheme(
    primary = AccentLime,
    onPrimary = Color.Black,
    secondary = AccentGreen,
    onSecondary = Color.Black,
    tertiary = WarningYellow,
    background = BgDark,
    onBackground = TextPrimary,
    surface = CardDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = TextSecondary,
    error = AlertRed,
    onError = Color.White
)

@Composable
fun JustPlayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}
