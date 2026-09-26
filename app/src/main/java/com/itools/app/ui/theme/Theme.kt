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
import com.itools.app.data.ThemeMode

// Palette inspired by it-tools: green #18a058 over cool grays.
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
// Dark blue: navy backgrounds with a light blue accent.
private val DarkBlue = darkColorScheme(
    primary = Color(0xFF6CB4FF), onPrimary = Color(0xFF002A52),
    primaryContainer = Color(0xFF123D6B), onPrimaryContainer = Color(0xFFD3E6FF),
    secondary = Color(0xFF9DB8DB), onSecondary = Color(0xFF102238),
    secondaryContainer = Color(0xFF22364F), onSecondaryContainer = Color(0xFFD6E3F5),
    background = Color(0xFF0B1628), onBackground = Color(0xFFE3EAF5),
    surface = Color(0xFF111E33), onSurface = Color(0xFFE3EAF5),
    surfaceVariant = Color(0xFF1A2A44), onSurfaceVariant = Color(0xFF9FB0C8),
    surfaceContainerLowest = Color(0xFF08111F), surfaceContainerLow = Color(0xFF0E1A2D),
    surfaceContainer = Color(0xFF13223A), surfaceContainerHigh = Color(0xFF1A2B46),
    surfaceContainerHighest = Color(0xFF223553),
    outline = Color(0xFF3A4E6E), outlineVariant = Color(0xFF26385A),
    error = Color(0xFFFF8A8A),
)

// Pure black for OLED screens (black pixels are turned off) with the it-tools green.
private val Black = darkColorScheme(
    primary = BrandDark, onPrimary = Color(0xFF00210F),
    primaryContainer = Color(0xFF1D4A30), onPrimaryContainer = Color(0xFFBDEBD0),
    secondary = Color(0xFF9CC7AE), onSecondary = Color(0xFF0E2A1B),
    secondaryContainer = Color(0xFF1E2A23), onSecondaryContainer = Color(0xFFD7E8DD),
    background = Color.Black, onBackground = Color(0xFFE6E6E6),
    surface = Color.Black, onSurface = Color(0xFFE6E6E6),
    surfaceVariant = Color(0xFF121212), onSurfaceVariant = Color(0xFFA0A0A0),
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF050505),
    surfaceContainer = Color(0xFF0A0A0A), surfaceContainerHigh = Color(0xFF141414),
    surfaceContainerHighest = Color(0xFF1A1A1A),
    outline = Color(0xFF3A3A3A), outlineVariant = Color(0xFF262626),
    error = Color(0xFFE88080),
)

// it-tools green: very dark green backgrounds with the it-tools green accent.
private val Green = darkColorScheme(
    primary = BrandDark, onPrimary = Color(0xFF00210F),
    primaryContainer = Color(0xFF1D4A30), onPrimaryContainer = Color(0xFFBDEBD0),
    secondary = Color(0xFF9CC7AE), onSecondary = Color(0xFF0E2A1B),
    secondaryContainer = Color(0xFF24402F), onSecondaryContainer = Color(0xFFD7E8DD),
    background = Color(0xFF0E1A14), onBackground = Color(0xFFE2EDE6),
    surface = Color(0xFF13231A), onSurface = Color(0xFFE2EDE6),
    surfaceVariant = Color(0xFF1B3024), onSurfaceVariant = Color(0xFFA3BBAC),
    surfaceContainerLowest = Color(0xFF09130E), surfaceContainerLow = Color(0xFF101E16),
    surfaceContainer = Color(0xFF16291E), surfaceContainerHigh = Color(0xFF1C3326),
    surfaceContainerHighest = Color(0xFF243D2F),
    outline = Color(0xFF3C5A48), outlineVariant = Color(0xFF274234),
    error = Color(0xFFE88080),
)

/** Custom palettes ([ThemeMode.hasOwnPalette]) take precedence over dynamic color. */
@Composable
fun IToolsTheme(mode: ThemeMode, dark: Boolean, dynamic: Boolean, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = when {
        mode == ThemeMode.DARK_BLUE -> DarkBlue
        mode == ThemeMode.BLACK -> Black
        mode == ThemeMode.GREEN -> Green
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
