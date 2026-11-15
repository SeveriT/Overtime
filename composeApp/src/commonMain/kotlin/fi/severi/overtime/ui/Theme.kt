package fi.severi.overtime.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF0B1A33),
    primaryContainer = Color(0xFF1E3A6E),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFFF6C453),
    background = Color(0xFF0E1013),
    onBackground = Color(0xFFE6E8EC),
    surface = Color(0xFF15181E),
    onSurface = Color(0xFFE6E8EC),
    surfaceVariant = Color(0xFF1F232B),
    onSurfaceVariant = Color(0xFFA3A8B3),
    surfaceContainer = Color(0xFF1A1E25),
    surfaceContainerHigh = Color(0xFF22272F),
    outline = Color(0xFF353B47),
    outlineVariant = Color(0xFF262B34),
    error = Color(0xFFFF7A7A),
)

@Composable
fun OvertimeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
