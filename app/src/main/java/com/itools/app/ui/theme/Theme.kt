package com.itools.app.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Paleta inspirada en it-tools: verde #18a058 sobre grises fríos.
val Brand = Color(0xFF18A058)
private val BrandDark = Color(0xFF36AD6A)

private val Light = lightColorScheme(
    primary = Brand, onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F0E1), onPrimaryContainer = Color(0xFF0B3D22),
    secondary = Color(0xFF3B6B52), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3EEE7), onSecondaryContainer = Color(0xFF1C3326),
    background = Color(0xFFF1F5F9), onBackground = Color(0xFF1F2328),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFEEF2F5), onSurfaceVariant = Color(0xFF5B6570),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8FAFB),
    surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFF3F5F7),
    surfaceContainerHighest = Color(0xFFEBEEF1),
    outline = Color(0xFFC9D0D6), outlineVariant = Color(0xFFE0E5EA),
    error = Color(0xFFD03050),
)
private val Dark = darkColorScheme(
    primary = BrandDark, onPrimary = Color(0xFF00210F),
    primaryContainer = Color(0xFF1D4A30), onPrimaryContainer = Color(0xFFBDEBD0),
    secondary = Color(0xFF9CC7AE), onSecondary = Color(0xFF0E2A1B),
    secondaryContainer = Color(0xFF2B3A31), onSecondaryContainer = Color(0xFFD7E8DD),
    background = Color(0xFF1C1C1C), onBackground = Color(0xFFE6E6E6),
    surface = Color(0xFF232323), onSurface = Color(0xFFE6E6E6),
    surfaceVariant = Color(0xFF2C2C2C), onSurfaceVariant = Color(0xFFA9ADB2),
    surfaceContainerLowest = Color(0xFF181818), surfaceContainerLow = Color(0xFF202020),
    surfaceContainer = Color(0xFF262626), surfaceContainerHigh = Color(0xFF2E2E2E),
    surfaceContainerHighest = Color(0xFF363636),
    outline = Color(0xFF4A4A4A), outlineVariant = Color(0xFF383838),
    error = Color(0xFFE88080),
)

@Composable
fun IToolsTheme(dark: Boolean, dynamic: Boolean, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
