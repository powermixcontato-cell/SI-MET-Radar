package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OledDarkScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color.Black,
    secondary = Color(0xFF60A5FA),
    onSecondary = Color.Black,
    tertiary = Color(0xFF10B981),
    background = Color(0xFF000000), // OLED PURO (Preto Absoluto #000000)
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF080808),    // Superfície OLED
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF121212), // Cartões e Painéis OLED
    onSurfaceVariant = Color(0xFFA1A1AA),
    outline = Color(0xFF27272A)
)

private val IpmetCyanDarkScheme = darkColorScheme(
    primary = IpmetCyan,
    onPrimary = Color.Black,
    secondary = IpmetSkyBlue,
    onSecondary = Color.Black,
    tertiary = IpmetGreenEcho,
    background = IpmetNavyBg,
    onBackground = TextPrimary,
    surface = IpmetNavySurface,
    onSurface = TextPrimary,
    surfaceVariant = IpmetNavyCard,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle
)

private val PhosphorGreenScheme = darkColorScheme(
    primary = PhosphorGreenAccent,
    onPrimary = Color.Black,
    secondary = Color(0xFF69F0AE),
    onSecondary = Color.Black,
    tertiary = Color(0xFFB9F6CA),
    background = PhosphorGreenBg,
    onBackground = TextPrimary,
    surface = PhosphorGreenSurface,
    onSurface = TextPrimary,
    surfaceVariant = PhosphorGreenCard,
    onSurfaceVariant = Color(0xFFA7D7B5),
    outline = Color(0xFF1E3A24)
)

private val PurpleStormScheme = darkColorScheme(
    primary = PurpleStormAccent,
    onPrimary = Color.Black,
    secondary = Color(0xFFD1C4E9),
    onSecondary = Color.Black,
    tertiary = Color(0xFF80D8FF),
    background = PurpleStormBg,
    onBackground = TextPrimary,
    surface = PurpleStormSurface,
    onSurface = TextPrimary,
    surfaceVariant = PurpleStormCard,
    onSurfaceVariant = Color(0xFFC7B9E2),
    outline = Color(0xFF332454)
)

private val AmberWarnScheme = darkColorScheme(
    primary = AmberWarnAccent,
    onPrimary = Color.Black,
    secondary = Color(0xFFFFD54F),
    onSecondary = Color.Black,
    tertiary = Color(0xFFFF7043),
    background = AmberWarnBg,
    onBackground = TextPrimary,
    surface = AmberWarnSurface,
    onSurface = TextPrimary,
    surfaceVariant = AmberWarnCard,
    onSurfaceVariant = Color(0xFFD4C1A5),
    outline = Color(0xFF3D2F1B)
)

@Composable
fun IpmetWeatherTheme(
    themeKey: String = "oled_dark",
    isEnergySaver: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        isEnergySaver -> OledDarkScheme
        themeKey == "oled_dark" -> OledDarkScheme
        themeKey == "ipmet_cyan" -> IpmetCyanDarkScheme
        themeKey == "phosphor_green" -> PhosphorGreenScheme
        themeKey == "purple_storm" -> PurpleStormScheme
        themeKey == "amber_warn" -> AmberWarnScheme
        else -> OledDarkScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
