package com.example.uzhavan.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val AgriColorScheme = lightColorScheme(
    primary              = AgriGreen,
    onPrimary            = AgriWhite,
    primaryContainer     = AgriGreenSurface,
    onPrimaryContainer   = AgriGreenDark,
    secondary            = AgriEarth,
    onSecondary          = AgriWhite,
    secondaryContainer   = AgriYellowLight,
    onSecondaryContainer = AgriEarth,
    tertiary             = AgriYellow,
    onTertiary           = AgriWhite,
    background           = AgriBackground,
    onBackground         = AgriGreenDark,
    surface              = AgriSurface,
    onSurface            = AgriOnSurface,
    surfaceVariant       = AgriGreenSurface,
    onSurfaceVariant     = AgriGray,
    outline              = AgriDivider,
    error                = AgriError,
    onError              = AgriWhite,
)

@Composable
fun UzhavanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AgriColorScheme,
        typography  = Typography,
        content     = content
    )
}
