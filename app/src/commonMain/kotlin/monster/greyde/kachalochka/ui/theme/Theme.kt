package monster.greyde.kachalochka.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
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

// Teal, amber, coral, lime, sky, magenta, sand, green: hues kept clear of the violet primary.
private val LightFriendColors =
    listOf(
        Color(0xFF00897B),
        Color(0xFFD99100),
        Color(0xFFE0603F),
        Color(0xFF7FA000),
        Color(0xFF1E88E5),
        Color(0xFFC2187A),
        Color(0xFFA0825A),
        Color(0xFF2E8B57),
    )

private val DarkFriendColors =
    listOf(
        Color(0xFF4DD0BE),
        Color(0xFFFFC247),
        Color(0xFFFF8A6B),
        Color(0xFFC6E05A),
        Color(0xFF64B5F6),
        Color(0xFFF06AB4),
        Color(0xFFD9BE8F),
        Color(0xFF5FCB85),
    )

private val LocalFriendColors = staticCompositionLocalOf { LightFriendColors }

/** Eight hues readable on both schemes; friends' calendar dots take them by index. */
@Composable
fun friendColor(index: Int): Color {
    val palette = LocalFriendColors.current
    return palette[index.mod(palette.size)]
}

@Composable
fun KachalochkaTheme(
    mode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val dark = mode.resolvesToDark(isSystemInDarkTheme())
    CompositionLocalProvider(
        LocalFriendColors provides if (dark) DarkFriendColors else LightFriendColors,
    ) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
    }
}
