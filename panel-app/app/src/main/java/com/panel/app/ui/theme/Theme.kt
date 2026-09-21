package com.panel.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkBackground = Color(0xFF0F1311)
val DarkSurface = Color(0xFF161B19)
val DarkSurfaceVariant = Color(0xFF1E2522)
val DarkPrimary = Color(0xFF7DB8B0)
val DarkSecondary = Color(0xFF6BAE7E)
val DarkTertiary = Color(0xFF9BA89F)
val DarkOnBackground = Color(0xFFE8EBE8)
val DarkOnSurface = Color(0xFFE8EBE8)

val LightBackground = Color(0xFFF2F5F1)
val LightSurface = Color(0xFFF8FAF8)
val LightSurfaceVariant = Color(0xFFE4EAE5)
val LightPrimary = Color(0xFF5B8C85)
val LightSecondary = Color(0xFF4A8C5E)
val LightTertiary = Color(0xFF8A9B95)
val LightOnBackground = Color(0xFF1A1F1C)
val LightOnSurface = Color(0xFF1A1F1C)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color(0xFF0E332D),
    primaryContainer = Color(0xFF35564F),
    onPrimaryContainer = Color(0xFFBFE9E0),
    secondary = DarkSecondary,
    onSecondary = Color(0xFF0B381C),
    secondaryContainer = Color(0xFF2F5E3D),
    onSecondaryContainer = Color(0xFFBCE9C6),
    tertiary = DarkTertiary,
    onTertiary = Color(0xFF1E2F29),
    tertiaryContainer = Color(0xFF3A4843),
    onTertiaryContainer = Color(0xFFD6E0D8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFB8C4BE),
    surfaceContainerLowest = Color(0xFF0A0D0C),
    surfaceContainerLow = Color(0xFF111614),
    surfaceContainer = Color(0xFF161B19),
    surfaceContainerHigh = Color(0xFF202825),
    surfaceContainerHighest = Color(0xFF2A3330),
    surfaceTint = DarkPrimary,
    inverseSurface = Color(0xFFE8EBE8),
    inverseOnSurface = Color(0xFF1A1F1C),
    inversePrimary = DarkPrimary,
    scrim = Color(0xFF000000),
    outline = Color(0xFF7A857E),
    outlineVariant = Color(0xFF445049)
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCEAE6),
    onPrimaryContainer = Color(0xFF1A3330),
    secondary = LightSecondary,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCEFE0),
    onSecondaryContainer = Color(0xFF1A3320),
    tertiary = LightTertiary,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE0E5E2),
    onTertiaryContainer = Color(0xFF1F2A26),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF5F6863),
    surfaceContainerLowest = Color(0xFFFBFCFA),
    surfaceContainerLow = Color(0xFFF2F5F1),
    surfaceContainer = Color(0xFFE8EDE6),
    surfaceContainerHigh = Color(0xFFDEE4DD),
    surfaceContainerHighest = Color(0xFFD3DAD3),
    surfaceTint = LightPrimary,
    inverseSurface = Color(0xFF2A302D),
    inverseOnSurface = Color(0xFFF2F5F1),
    inversePrimary = Color(0xFF7DB8B0),
    scrim = Color(0xFF000000),
    outline = Color(0xFF7A857E),
    outlineVariant = Color(0xFFC8D0CB)
)

@Composable
fun PanelAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
