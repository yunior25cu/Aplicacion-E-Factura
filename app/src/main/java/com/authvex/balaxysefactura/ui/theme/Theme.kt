package com.authvex.balaxysefactura.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = BalaxysPrimary,
    onPrimary = BalaxysOnPrimary,
    primaryContainer = BalaxysOnPrimaryContainer,
    secondary = BalaxysSecondary,
    onSecondary = BalaxysOnSecondary,
    background = BalaxysBackground,
    surface = BalaxysSurface,
    error = BalaxysError
)

private val LightColorScheme = lightColorScheme(
    primary = BalaxysPrimary,
    onPrimary = BalaxysOnPrimary,
    primaryContainer = BalaxysPrimaryContainer,
    onPrimaryContainer = BalaxysOnPrimaryContainer,
    secondary = BalaxysSecondary,
    onSecondary = BalaxysOnSecondary,
    secondaryContainer = BalaxysSecondaryContainer,
    onSecondaryContainer = BalaxysOnSecondaryContainer,
    background = BalaxysBackground,
    onBackground = BalaxysOnBackground,
    surface = BalaxysSurface,
    onSurface = BalaxysOnSurface,
    surfaceVariant = BalaxysSurfaceVariant,
    onSurfaceVariant = BalaxysOnSurfaceVariant,
    outline = BalaxysOutline,
    error = BalaxysError,
    onError = BalaxysOnError
)

@Composable
fun BalaxysEfacturaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
