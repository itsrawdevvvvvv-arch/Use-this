package dev.rawdev.gaffer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Amber = Color(0xFFF5C451)
private val Ink = Color(0xFF0B0B0F)
private val Slate = Color(0xFF16161D)
private val Ash = Color(0xFF23232C)
private val Paper = Color(0xFFF6F4EF)

private val Dark = darkColorScheme(
    primary = Amber,
    onPrimary = Ink,
    secondary = Color(0xFF9AD1C8),
    background = Ink,
    onBackground = Color(0xFFEDEAE3),
    surface = Slate,
    onSurface = Color(0xFFEDEAE3),
    surfaceVariant = Ash,
    onSurfaceVariant = Color(0xFFB9B5AC),
    error = Color(0xFFFF8A80)
)

private val Light = lightColorScheme(
    primary = Color(0xFF8A6400),
    onPrimary = Color.White,
    secondary = Color(0xFF2A6F66),
    background = Paper,
    onBackground = Color(0xFF1A1A1A),
    surface = Color.White,
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFEFEBE2),
    onSurfaceVariant = Color(0xFF565149),
    error = Color(0xFFB3261E)
)

@Composable
fun GafferTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content
    )
}
