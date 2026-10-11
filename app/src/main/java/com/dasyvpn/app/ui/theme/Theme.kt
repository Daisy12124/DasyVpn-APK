package com.dasyvpn.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val Violet = Color(0xFF7C5CFF)
val VioletDark = Color(0xFF5B3FD9)
val Teal = Color(0xFF00D4AA)
val BgDark = Color(0xFF101216)
val SurfaceDark = Color(0xFF171A21)
val OnBgDark = Color(0xFFE6E1EF)

private val DarkColors = darkColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = VioletDark,
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Teal,
    onSecondary = Color.Black,
    background = BgDark,
    onBackground = OnBgDark,
    surface = SurfaceDark,
    onSurface = OnBgDark,
    surfaceVariant = Color(0xFF23262F),
    onSurfaceVariant = Color(0xFFC5C0D0),
    error = Color(0xFFFF6B6B),
    outline = Color(0xFF3A3E4A)
)

private val LightColors = lightColorScheme(
    primary = VioletDark,
    onPrimary = Color.White,
    secondary = Color(0xFF00896F),
    background = Color(0xFFFDFBFF),
    surface = Color(0xFFFDFBFF)
)

@Composable
fun DasyTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
