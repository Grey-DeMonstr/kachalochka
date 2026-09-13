package monster.greyde.kachalochka.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import monster.greyde.kachalochka.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    mode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Theme",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.testTag("settings-title"),
            )
            ThemeMode.entries.forEach { option ->
                FilterChip(
                    selected = option == mode,
                    onClick = { onModeChange(option) },
                    label = { Text(option.name) },
                    modifier = Modifier.testTag("theme-${option.name.lowercase()}"),
                )
            }
        }
    }
}
