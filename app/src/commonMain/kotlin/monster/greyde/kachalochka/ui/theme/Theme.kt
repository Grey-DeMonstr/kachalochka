package monster.greyde.kachalochka.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF796CBF),
        onPrimary = Color(0xFFF5F4FF),
        secondary = Color(0xFF5D5294),
        tertiary = Color(0xFF5D5294),
        background = Color(0xFFF3F5FE),
        onBackground = Color(0xFF161826),
        surface = Color(0xFFF5F4FF),
        onSurface = Color(0xFF161826),
        surfaceVariant = Color(0xFFE4E7F5),
        surfaceContainerHighest = Color(0xFFCFD3E5),
        surfaceContainer = Color(0xFFF5F4FF),
        surfaceContainerHigh = Color(0xFFF5F4FF),
        surfaceContainerLow = Color(0xFFF5F4FF),
        onPrimaryContainer = Color(0xFF423A6A),
        outline = Color(0xFF75798C),
        outlineVariant = Color(0xFFCFD3E5),
        error = Color(0xFFBA1A1A),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF9184D9),
        onPrimary = Color(0xFF161826),
        secondary = Color(0xFFB5ABFC),
        tertiary = Color(0xFFD2CEFD),
        background = Color(0xFF161826),
        onBackground = Color(0xFFE9E9ED),
        surface = Color(0xFF1B1D2B),
        onSurface = Color(0xFFE9E9ED),
        surfaceVariant = Color(0xFF232532),
        surfaceContainerHighest = Color(0xFF292B31),
        surfaceContainer = Color(0xFF1B1D2B),
        surfaceContainerHigh = Color(0xFF1B1D2B),
        surfaceContainerLow = Color(0xFF1B1D2B),
        onPrimaryContainer = Color(0xFFE7E5FE),
        outline = Color(0xFF595D6C),
        outlineVariant = Color(0xFF3F424D),
        error = Color(0xFFFFB4AB),
    )

@Composable
fun KachalochkaTheme(
    mode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (mode.resolvesToDark(isSystemInDarkTheme())) DarkColors else LightColors
    MaterialTheme(colorScheme = colorScheme, content = content)
}
