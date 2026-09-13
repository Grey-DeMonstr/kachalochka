package monster.greyde.kachalochka.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF3F6B4F),
        onPrimary = Color(0xFFFFFFFF),
        secondary = Color(0xFF52634F),
        background = Color(0xFFFBFDF7),
        onBackground = Color(0xFF191C19),
        surface = Color(0xFFFBFDF7),
        onSurface = Color(0xFF191C19),
        error = Color(0xFFBA1A1A),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFFA5D2A8),
        onPrimary = Color(0xFF0C3921),
        secondary = Color(0xFFB9CCB4),
        background = Color(0xFF191C19),
        onBackground = Color(0xFFE1E3DE),
        surface = Color(0xFF191C19),
        onSurface = Color(0xFFE1E3DE),
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
