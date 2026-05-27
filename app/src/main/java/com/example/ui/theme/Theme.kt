package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val QRWakeDarkColorScheme = darkColorScheme(
    primary = AmberOrange,
    onPrimary = Color.Black,
    secondary = SoftOrange,
    onSecondary = Color.Black,
    tertiary = TealSuccess,
    background = DarkBg,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceCard,
    onSurfaceVariant = TextSecondary,
    error = ErrorColor
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force Dark mode default as requested
    dynamicColor: Boolean = false, // Disable to maintain custom Amber style
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = QRWakeDarkColorScheme,
        typography = Typography,
        content = content
    )
}
