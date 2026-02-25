package com.videdit.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val colors = lightColorScheme(
    primary = Accent,
    background = Background,
    surface = Surface,
    surfaceVariant = SurfaceAlt,
    onPrimary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
)

@Composable
fun VidEditTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content,
    )
}
