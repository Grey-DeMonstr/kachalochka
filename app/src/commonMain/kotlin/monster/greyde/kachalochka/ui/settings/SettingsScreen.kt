package monster.greyde.kachalochka.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.Sex
import monster.greyde.kachalochka.navigation.MAX_TRANSITION_MILLIS
import monster.greyde.kachalochka.ui.components.AccentButton
import monster.greyde.kachalochka.ui.components.Choice
import monster.greyde.kachalochka.ui.components.ChoiceRow
import monster.greyde.kachalochka.ui.components.ControlShape
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.components.TextInput
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.strings.AppLanguage
import monster.greyde.kachalochka.ui.strings.strings
import monster.greyde.kachalochka.ui.theme.ThemeMode
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = koinViewModel()
    val profile by viewModel.profile.collectAsState()
    val device by viewModel.device.collectAsState()
    val canApply by viewModel.canApply.collectAsState()
    val confirmingLeave by viewModel.confirmingLeave.collectAsState()
    val deletion by viewModel.deletion.collectAsState()
    val leave = { if (viewModel.requestLeave()) onBack() }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCompleted = leave,
    )
    Screen(strings().settings, onBack = leave, onOpenSettings = null) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            profile?.let { ProfileSection(it, viewModel) }
            SectionTitle(strings().theme, Modifier.testTag("settings-title"))
            val modes = listOf(ThemeMode.System, ThemeMode.Light, ThemeMode.Dark)
            ChoiceRow(
                listOf(
                    Choice(strings().themeSystem, "theme-system"),
                    Choice(strings().themeLight, "theme-light"),
                    Choice(strings().themeDark, "theme-dark"),
                ),
                selected = modes.indexOf(device.theme),
                onSelect = { viewModel.chooseTheme(modes[it]) },
            )
            SectionTitle(strings().language)
            val languages = listOf(AppLanguage.System, AppLanguage.English, AppLanguage.Russian)
            ChoiceRow(
                listOf(
                    Choice(strings().languageSystem, "language-system"),
                    Choice("English", "language-english"),
                    Choice("Русский", "language-russian"),
                ),
                selected = languages.indexOf(device.language),
                onSelect = { viewModel.chooseLanguage(languages[it]) },
            )
            SectionTitle(strings().transitions)
            TextInput(
                device.transition,
                "0",
                viewModel::typeTransition,
                Modifier.testTag("transition-millis"),
                KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Hint(strings().transitionsHint(MAX_TRANSITION_MILLIS))
            AccentButton(
                strings().apply,
                PhosphorIcons.Check,
                viewModel::apply,
                Modifier.testTag("apply-settings"),
                enabled = canApply,
            )
            if (deletion.available) AdvancedSection(deletion, viewModel)
        }
    }
    if (confirmingLeave) {
        AlertDialog(
            onDismissRequest = viewModel::stay,
            title = { Text(strings().applyTitle) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.apply()
                        viewModel.stay()
                        onBack()
                    },
                    enabled = canApply,
                    modifier = Modifier.testTag("leave-apply"),
                ) { Text(strings().apply) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.discard()
                        onBack()
                    },
                    modifier = Modifier.testTag("leave-discard"),
                ) { Text(strings().discard) }
            },
        )
    }
    if (deletion.confirming) DeleteDialog(deletion, viewModel)
}

@Composable
private fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text = text, style = MaterialTheme.typography.headlineSmall, modifier = modifier)
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.64f),
    )
}

@Composable
private fun AdvancedSection(
    deletion: DeletionUi,
    viewModel: SettingsViewModel,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clickable(onClick = viewModel::toggleAdvanced)
            .padding(vertical = 8.dp)
            .testTag("settings-advanced"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(strings().advanced, style = MaterialTheme.typography.headlineSmall)
        Icon(
            PhosphorIcons.CaretRight,
            null,
            tint = colors.onBackground.copy(alpha = 0.55f),
            modifier = Modifier.size(20.dp).rotate(if (deletion.advancedOpen) 90f else 0f),
        )
    }
    if (!deletion.advancedOpen) return
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(ControlShape)
            .border(1.dp, colors.error, ControlShape)
            .clickable(enabled = !deletion.running, onClick = viewModel::askToDelete)
            .testTag("delete-account"),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PhosphorIcons.Trash, null, tint = colors.error, modifier = Modifier.size(20.dp))
        Text(strings().deleteAccount, fontSize = 16.sp, color = colors.error)
    }
    deletion.error?.let {
        Text(
            it,
            modifier = Modifier.testTag("delete-account-error"),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.error,
        )
    }
}

@Composable
private fun DeleteDialog(
    deletion: DeletionUi,
    viewModel: SettingsViewModel,
) {
    AlertDialog(
        onDismissRequest = viewModel::cancelDelete,
        title = { Text(strings().deleteAccountTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings().deleteAccountText(DELETE_WORD))

                TextInput(
                    deletion.word,
                    DELETE_WORD,
                    viewModel::typeDeleteWord,
                    Modifier.testTag("delete-word"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = viewModel::confirmDelete,
                enabled = deletion.canConfirm,
                modifier = Modifier.testTag("confirm-delete-account"),
            ) { Text(strings().delete, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(
                onClick = viewModel::cancelDelete,
                modifier = Modifier.testTag("cancel-delete-account"),
            ) { Text(strings().cancel) }
        },
    )
}

@Composable
private fun ProfileSection(
    profile: ProfileUi,
    viewModel: SettingsViewModel,
) {
    SectionTitle(strings().profile, Modifier.testTag("profile-title"))
    profile.nickname?.let {
        FieldLabel(strings().nickname)
        TextInput(it, profile.placeholder, viewModel::type, Modifier.testTag("nickname"))
    }
    FieldLabel(strings().sex)
    val sexes = listOf(Sex.Male, Sex.Female)
    ChoiceRow(
        listOf(Choice(strings().male, "sex-male"), Choice(strings().female, "sex-female")),
        selected = sexes.indexOf(profile.sex),
        onSelect = { viewModel.chooseSex(sexes[it]) },
    )
    FieldLabel(strings().birthDate)
    TextInput(
        profile.birthDate,
        strings().datePlaceholder,
        viewModel::typeBirthDate,
        Modifier.testTag("birth-date"),
        KeyboardOptions(keyboardType = KeyboardType.Number),
        valid = profile.birthDateValid,
    )
    FieldLabel(strings().heightCm)
    TextInput(
        profile.height,
        "",
        viewModel::typeHeight,
        Modifier.testTag("height"),
        KeyboardOptions(keyboardType = KeyboardType.Decimal),
        valid = profile.heightValid,
    )
    Hint(strings().bodyFieldsHint)
    FieldLabel(strings().weightUnits)
    val units = listOf(PreferredWeightUnit.Kg, PreferredWeightUnit.Lb, PreferredWeightUnit.Mixed)
    ChoiceRow(
        listOf(
            Choice(strings().kg, "weight-unit-kg"),
            Choice("lb", "weight-unit-lb"),
            Choice(strings().mixedUnits, "weight-unit-mixed", weight = 1.6f),
        ),
        selected = units.indexOf(profile.weightUnit),
        onSelect = { viewModel.chooseWeightUnit(units[it]) },
    )
    Hint(strings().mixedUnitsHint)
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge)
}
