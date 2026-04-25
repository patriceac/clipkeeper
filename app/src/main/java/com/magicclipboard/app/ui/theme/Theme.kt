package com.magicclipboard.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.magicclipboard.data.model.ThemeMode

private val LightScheme = lightColorScheme(
    primary = Color(0xFF235B53),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCFE8E1),
    onPrimaryContainer = Color(0xFF0B312B),
    secondary = Color(0xFF465E8E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE5FF),
    onSecondaryContainer = Color(0xFF162A55),
    tertiary = Color(0xFFB45F42),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDBCF),
    onTertiaryContainer = Color(0xFF4D1E0E),
    background = Color(0xFFF6F7F2),
    onBackground = Color(0xFF171D1B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFE1E7E2),
    onSurfaceVariant = Color(0xFF48504C),
    outline = Color(0xFF7A8580),
    outlineVariant = Color(0xFFC7D0CA),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF92D2C6),
    onPrimary = Color(0xFF07352F),
    primaryContainer = Color(0xFF164941),
    onPrimaryContainer = Color(0xFFB7F0E4),
    secondary = Color(0xFFB7C8FF),
    onSecondary = Color(0xFF172C59),
    secondaryContainer = Color(0xFF2E4677),
    onSecondaryContainer = Color(0xFFDCE5FF),
    tertiary = Color(0xFFFFB59E),
    onTertiary = Color(0xFF612712),
    tertiaryContainer = Color(0xFF87402A),
    onTertiaryContainer = Color(0xFFFFDBCF),
    background = Color(0xFF111615),
    onBackground = Color(0xFFE5ECE7),
    surface = Color(0xFF171D1B),
    onSurface = Color(0xFFE5ECE7),
    surfaceVariant = Color(0xFF37423E),
    onSurfaceVariant = Color(0xFFC5CEC8),
    outline = Color(0xFF8F9A94),
    outlineVariant = Color(0xFF3F4A45),
)

@Composable
fun MagicClipboardTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
