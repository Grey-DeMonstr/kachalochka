package monster.greyde.kachalochka.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    mode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
) {
    Screen("Настройки", onBack = onBack, onOpenSettings = null) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Тема",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.testTag("settings-title"),
            )
            ThemeMode.entries.forEach { option ->
                FilterChip(
                    selected = option == mode,
                    onClick = { onModeChange(option) },
                    label = { Text(themeModeLabel(option)) },
                    modifier = Modifier.testTag("theme-${option.name.lowercase()}"),
                )
            }
        }
    }
}

private fun themeModeLabel(mode: ThemeMode): String =
    when (mode) {
        ThemeMode.System -> "Системная"
        ThemeMode.Light -> "Светлая"
        ThemeMode.Dark -> "Тёмная"
    }
