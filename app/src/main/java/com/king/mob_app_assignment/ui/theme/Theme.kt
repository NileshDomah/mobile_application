package com.king.mob_app_assignment.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors: ColorScheme = lightColorScheme(
    primary = CivicBlue40,
    onPrimary = CivicBlue100,
    primaryContainer = CivicBlue90,
    onPrimaryContainer = CivicBlue10,
    inversePrimary = CivicBlue80,

    secondary = CivicAmber40,
    onSecondary = CivicBlue100,
    secondaryContainer = CivicAmber90,
    onSecondaryContainer = CivicAmber10,

    tertiary = CivicTeal40,
    onTertiary = CivicBlue100,
    tertiaryContainer = CivicTeal90,
    onTertiaryContainer = CivicTeal10,

    error = CivicError40,
    onError = CivicBlue100,
    errorContainer = CivicError90,
    onErrorContainer = CivicError10,

    background = CivicNeutral99,
    onBackground = CivicNeutral10,
    surface = CivicNeutral99,
    onSurface = CivicNeutral10,
    surfaceVariant = CivicNeutralVariant90,
    onSurfaceVariant = CivicNeutralVariant30,

    outline = CivicNeutralVariant50,
    outlineVariant = CivicNeutralVariant80,

    surfaceTint = CivicBlue40,
    inverseSurface = CivicNeutral20,
    inverseOnSurface = CivicNeutral95,

    scrim = androidx.compose.ui.graphics.Color.Black,
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = CivicBlue80,
    onPrimary = CivicBlue20,
    primaryContainer = CivicBlue30,
    onPrimaryContainer = CivicBlue90,
    inversePrimary = CivicBlue40,

    secondary = CivicAmber80,
    onSecondary = CivicAmber20,
    secondaryContainer = CivicAmber30,
    onSecondaryContainer = CivicAmber90,

    tertiary = CivicTeal80,
    onTertiary = CivicTeal20,
    tertiaryContainer = CivicTeal30,
    onTertiaryContainer = CivicTeal90,

    error = CivicError80,
    onError = CivicError20,
    errorContainer = CivicError30,
    onErrorContainer = CivicError90,

    background = CivicNeutral10,
    onBackground = CivicNeutral90,
    surface = CivicNeutral10,
    onSurface = CivicNeutral90,
    surfaceVariant = CivicNeutralVariant30,
    onSurfaceVariant = CivicNeutralVariant80,

    outline = CivicNeutralVariant60,
    outlineVariant = CivicNeutralVariant30,

    surfaceTint = CivicBlue80,
    inverseSurface = CivicNeutral90,
    inverseOnSurface = CivicNeutral20,

    scrim = androidx.compose.ui.graphics.Color.Black,
)

/**
 * Civic/trustworthy Material 3 theme: deep blue/teal primary, amber accent reserved for
 * urgent report actions, generous rounded shape system, and a considered type hierarchy.
 * Replaces the previous inline private AppTheme in MainActivity.kt.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
