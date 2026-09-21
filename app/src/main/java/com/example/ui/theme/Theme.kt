package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = CyberBlueDarkPrimary,
    onPrimary = CyberBlueDarkOnPrimary,
    primaryContainer = CyberBlueDarkPrimaryContainer,
    onPrimaryContainer = CyberBlueDarkOnPrimaryContainer,
    secondary = CyberTealDarkSecondary,
    onSecondary = CyberTealDarkOnSecondary,
    secondaryContainer = CyberTealDarkSecondaryContainer,
    onSecondaryContainer = CyberTealDarkOnSecondaryContainer,
    tertiary = EmeraldDarkTertiary,
    onTertiary = EmeraldDarkOnTertiary,
    tertiaryContainer = EmeraldDarkTertiaryContainer,
    onTertiaryContainer = EmeraldDarkOnTertiaryContainer,
    background = DarkNavyBackground,
    onBackground = DarkNavyOnBackground,
    surface = DarkNavySurface,
    onSurface = DarkNavyOnSurface,
    surfaceVariant = DarkNavySurfaceVariant,
    onSurfaceVariant = DarkNavyOnSurfaceVariant,
    outline = DarkNavyOutline
)

private val LightColorScheme = lightColorScheme(
    primary = CyberBluePrimary,
    onPrimary = CyberBlueOnPrimary,
    primaryContainer = CyberBluePrimaryContainer,
    onPrimaryContainer = CyberBlueOnPrimaryContainer,
    secondary = CyberTealSecondary,
    onSecondary = CyberTealOnSecondary,
    secondaryContainer = CyberTealSecondaryContainer,
    onSecondaryContainer = CyberTealOnSecondaryContainer,
    tertiary = EmeraldTertiary,
    onTertiary = EmeraldOnTertiary,
    tertiaryContainer = EmeraldTertiaryContainer,
    onTertiaryContainer = EmeraldOnTertiaryContainer,
    background = SlateBackground,
    onBackground = SlateOnBackground,
    surface = SlateSurface,
    onSurface = SlateOnSurface,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = SlateOnSurfaceVariant,
    outline = SlateOutline
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
