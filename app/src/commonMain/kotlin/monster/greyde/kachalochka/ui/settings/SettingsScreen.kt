package monster.greyde.kachalochka.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.navigation.MAX_TRANSITION_MILLIS
import monster.greyde.kachalochka.navigation.transitionMillisOrNull
import monster.greyde.kachalochka.ui.components.AccentButton
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.theme.ThemeMode
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(
    mode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit,
    transitionMillis: Int,
    onTransitionMillisChange: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val nickname by viewModel.nickname.collectAsState()
    Screen("Настройки", onBack = onBack, onOpenSettings = null) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            nickname?.let { ui ->
                NicknameSection(ui, onType = viewModel::type, onSave = viewModel::save)
            }
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
            TransitionSection(transitionMillis, onTransitionMillisChange)
        }
    }
}

@Composable
private fun TransitionSection(
    millis: Int,
    onChange: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(millis.toString()) }
    Text(
        text = "Анимация переходов",
        style = MaterialTheme.typography.headlineSmall,
    )
    SettingsField(
        value = text,
        placeholder = "0",
        onValueChange = { typed ->
            transitionMillisOrNull(typed)?.let {
                text = typed
                onChange(it)
            }
        },
        modifier = Modifier.testTag("transition-millis"),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    Text(
        text = "Миллисекунды, до $MAX_TRANSITION_MILLIS; 0 — без анимации",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.64f),
    )
}

@Composable
private fun NicknameSection(
    nickname: NicknameUi,
    onType: (String) -> Unit,
    onSave: () -> Unit,
) {
    Text(
        text = "Ник",
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.testTag("nickname-title"),
    )
    SettingsField(
        nickname.text,
        nickname.placeholder,
        onType,
        Modifier.testTag("nickname"),
    )
    AccentButton(
        "Сохранить",
        PhosphorIcons.Check,
        onSave,
        Modifier.testTag("save-nickname"),
        enabled = nickname.canSave,
    )
}

@Composable
private fun SettingsField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clip(shape)
            .background(colors.surfaceVariant)
            .border(1.dp, colors.onBackground.copy(alpha = 0.16f), shape)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, fontSize = 17.sp, color = colors.onBackground.copy(alpha = 0.48f))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 17.sp, color = colors.onBackground),
            cursorBrush = SolidColor(colors.secondary),
            keyboardOptions = keyboardOptions,
            modifier = modifier.fillMaxWidth(),
        )
    }
}

private fun themeModeLabel(mode: ThemeMode): String =
    when (mode) {
        ThemeMode.System -> "Системная"
        ThemeMode.Light -> "Светлая"
        ThemeMode.Dark -> "Тёмная"
    }
