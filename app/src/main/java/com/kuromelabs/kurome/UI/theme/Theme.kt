package com.kuromelabs.kurome.UI.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorPalette = darkColors(
    primary = kurome_dark_primary,
    primaryVariant = Purple700,
    secondary = kurome_orange,
    onPrimary = kurome_dark_on_primary,
    surface = kurome_dark_surface,
    onSurface = kurome_dark_on_surface,
    background = Color.Black
)

private val LightColorPalette = lightColors(
    primary = kurome_light_primary,
    primaryVariant = Purple700,
    secondary = kurome_orange,
    onPrimary = Color.White,
    background = Color.White,
    surface = kurome_light_surface,
    onSurface = kurome_light_on_surface
    /* Other default colors to override

    surface = Color.White,

    onSecondary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black,
    */
)

@Composable
fun KuromeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) {
        DarkColorPalette
    } else {
        LightColorPalette
    }

    MaterialTheme(
        colors = colors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}