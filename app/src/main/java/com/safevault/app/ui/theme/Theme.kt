package com.safevault.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF1E4FD8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = Color(0xFF0A2A82),
    secondary = Color(0xFF545F71),
    secondaryContainer = Color(0xFFDDE3F5),
    onSecondaryContainer = Color(0xFF1A2333),
    tertiary = Color(0xFF0F7A57),
    tertiaryContainer = Color(0xFFCDEFDF),
    onTertiaryContainer = Color(0xFF00382A),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8F9FC),
    surface = Color(0xFFF8F9FC),
    surfaceContainer = Color(0xFFEEF0F6),
    surfaceContainerHigh = Color(0xFFE7E9F0),
    surfaceContainerLow = Color(0xFFF2F4F9),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB3C5FF),
    onPrimary = Color(0xFF002A78),
    primaryContainer = Color(0xFF1E3E9E),
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFFBCC7DC),
    secondaryContainer = Color(0xFF3C4758),
    onSecondaryContainer = Color(0xFFDDE3F5),
    tertiary = Color(0xFF8BD6B4),
    tertiaryContainer = Color(0xFF005139),
    onTertiaryContainer = Color(0xFFCDEFDF),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111318),
    surface = Color(0xFF111318),
    surfaceContainer = Color(0xFF1D2026),
    surfaceContainerHigh = Color(0xFF272A31),
    surfaceContainerLow = Color(0xFF191C21),
)

/**
 * SafeVault brand gradient, used behind the logo and the Home header so the app is
 * recognisable at a glance. Starts at the launcher icon's blue.
 */
object BrandColors {
    val gradientLight = listOf(Color(0xFF1E4FD8), Color(0xFF2A3BB7), Color(0xFF15237A))
    val gradientDark = listOf(Color(0xFF1E3E9E), Color(0xFF1A2C78), Color(0xFF0E1645))
}

/** Colours for the strength meter and warnings, readable in light and dark mode. */
object StatusColors {
    val weak = Color(0xFFD9483B)
    val fair = Color(0xFFE09A1F)
    val strong = Color(0xFF2E9E6A)
}

@Composable
fun SafeVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = Typography(),
        content = content,
    )
}
