package monster.greyde.kachalochka.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.Sex
import monster.greyde.kachalochka.ui.account.PersonAvatar
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.format.formatNumber
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.strings.AppLanguage
import monster.greyde.kachalochka.ui.strings.Strings
import monster.greyde.kachalochka.ui.strings.strings
import monster.greyde.kachalochka.ui.theme.ThemeMode
import monster.greyde.kachalochka.ui.timer.RestSound
import monster.greyde.kachalochka.ui.timer.RestSoundChooser
import monster.greyde.kachalochka.ui.timer.RestSoundLaunchers
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private enum class Sheet { Theme, Language, Animation, RestSound }

/** A short list: rare settings show their value and open a choice in a bottom sheet. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenProfile: () -> Unit = {},
    onOpenPeople: () -> Unit = {},
    onOpenAccount: () -> Unit = {},
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val hub by viewModel.hub.collectAsState()
    val device by viewModel.device.collectAsState()
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    // Outside the sheet, so a result that comes back after it closes still lands.
    val chooser: RestSoundChooser = koinInject()
    val launchers = chooser.rememberLaunchers(viewModel::pickRestSound)
    // Back from a screen opened here, what it changed shows.
    LaunchedEffect(Unit) { viewModel.refresh() }
    Screen(strings().settings, onBack = onBack, onOpenSettings = null) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 17.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            hub.card?.let { ProfileCard(it, onOpenProfile) }
            SettingsGroup(strings().appSection, labelTag = "settings-title") {
                SettingsCard {
                    SettingsRow(
                        PhosphorIcons.Moon,
                        strings().theme,
                        "settings-theme",
                        { sheet = Sheet.Theme },
                        value = themeName(device.theme),
                    )
                    RowDivider()
                    SettingsRow(
                        PhosphorIcons.Translate,
                        strings().language,
                        "settings-language",
                        { sheet = Sheet.Language },
                        value = languageName(device.language),
                    )
                    RowDivider()
                    SettingsRow(
                        PhosphorIcons.Sparkle,
                        strings().animation,
                        "settings-animation",
                        { sheet = Sheet.Animation },
                        value = presetName(device.transition),
                    )
                    RowDivider()
                    SettingsRow(
                        PhosphorIcons.Bell,
                        strings().restSound,
                        "settings-rest-sound",
                        { sheet = Sheet.RestSound },
                        value =
                            if (device.restSound.enabled) {
                                strings().restSoundOn
                            } else {
                                strings().restSoundOff
                            },
                    )
                }
            }
            hub.people?.let { people ->
                SettingsGroup(strings().people) {
                    SettingsCard {
                        SettingsRow(
                            PhosphorIcons.UsersThree,
                            strings().peopleRow,
                            "settings-people",
                            onOpenPeople,
                            subtitle = peopleCounts(people).takeIf { it.isNotEmpty() },
                        )
                    }
                }
            }
            hub.email?.let { email ->
                SettingsGroup(strings().account) {
                    SettingsCard {
                        SettingsRow(
                            PhosphorIcons.UserCircle,
                            strings().account,
                            "settings-account",
                            onOpenAccount,
                            value = email,
                        )
                    }
                }
            }
        }
    }
    when (sheet) {
        Sheet.Theme -> {
            val modes = listOf(ThemeMode.System, ThemeMode.Light, ThemeMode.Dark)
            ChoiceSheet(
                strings().theme,
                listOf(
                    SheetOption(strings().themeSystem, "theme-system"),
                    SheetOption(strings().themeLight, "theme-light"),
                    SheetOption(strings().themeDark, "theme-dark"),
                ),
                selected = modes.indexOf(device.theme),
                onSelect = { viewModel.chooseTheme(modes[it]) },
                onDismiss = { sheet = null },
            )
        }
        Sheet.Language -> {
            val languages = listOf(AppLanguage.System, AppLanguage.English, AppLanguage.Russian)
            ChoiceSheet(
                strings().language,
                listOf(
                    SheetOption(strings().languageSystem, "language-system"),
                    SheetOption("English", "language-english"),
                    SheetOption("Русский", "language-russian"),
                ),
                selected = languages.indexOf(device.language),
                onSelect = { viewModel.chooseLanguage(languages[it]) },
                onDismiss = { sheet = null },
            )
        }
        Sheet.Animation -> {
            val presets = TransitionPreset.entries
            ChoiceSheet(
                strings().transitions,
                presets.map {
                    SheetOption(
                        presetName(it),
                        "transition-${it.name.lowercase()}",
                        note = it.millis.takeIf { ms -> ms > 0 }?.let(strings()::millis),
                    )
                },
                selected = presets.indexOf(device.transition),
                onSelect = { viewModel.chooseTransition(presets[it]) },
                onDismiss = { sheet = null },
            )
        }
        Sheet.RestSound ->
            RestSoundSheet(device.restSound, launchers, viewModel, onDismiss = { sheet = null })
        null -> Unit
    }
}

@Composable
private fun RestSoundSheet(
    sound: RestSound,
    launchers: RestSoundLaunchers?,
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    ChoiceSheet(
        strings().restSound,
        listOf(
            SheetOption(strings().restSoundOff, "rest-sound-off"),
            SheetOption(strings().restSoundOn, "rest-sound-on"),
        ),
        selected = if (sound.enabled) 1 else 0,
        onSelect = {
            val on = it == 1
            if (on && !sound.enabled) launchers?.allowNotifications?.invoke()
            viewModel.chooseRestSound(on)
        },
        onDismiss = onDismiss,
    ) {
        val colors = MaterialTheme.colorScheme
        val muted = colors.onSurface.copy(alpha = 0.55f)
        if (sound.enabled && launchers != null) {
            val name by produceState("", sound.uri, launchers) {
                value = launchers.nameOf(sound.uri)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable { launchers.pick(sound.uri) }
                    .padding(12.dp)
                    .testTag("rest-sound-pick"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(strings().restSoundChoice, fontSize = 16.sp, color = colors.onSurface)
                    Text(
                        name,
                        Modifier.testTag("rest-sound-name"),
                        fontSize = 14.sp,
                        color = muted,
                    )
                }
                Icon(
                    PhosphorIcons.CaretRight,
                    null,
                    tint = muted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Text(
            strings().restSoundHint,
            Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            fontSize = 13.5.sp,
            color = muted,
        )
    }
}

@Composable
private fun ProfileCard(
    card: ProfileCardUi,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val name = card.name.ifEmpty { strings().profile }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(GroupShape)
            .background(colors.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(14.dp)
            .testTag("settings-profile"),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(card.owner, name, card.avatar, size = 56.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                name,
                Modifier.testTag("settings-profile-name"),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onBackground,
            )
            Text(
                profileLine(card, strings()),
                Modifier.testTag("settings-profile-line"),
                fontSize = 14.sp,
                color = colors.onBackground.copy(alpha = 0.55f),
            )
        }
        Icon(
            PhosphorIcons.CaretRight,
            null,
            tint = colors.onBackground.copy(alpha = 0.45f),
            modifier = Modifier.size(18.dp),
        )
    }
}

/** "Мужской · 39 лет · 174 см · кг", leaving out what is not set. */
private fun profileLine(
    card: ProfileCardUi,
    strings: Strings,
): String =
    listOfNotNull(
        card.sex?.let { if (it == Sex.Male) strings.male else strings.female },
        card.age?.let(strings::years),
        card.heightCm?.let { "${formatNumber(it)} ${strings.cm}" },
        when (card.weightUnit) {
            PreferredWeightUnit.Kg -> strings.kg
            PreferredWeightUnit.Lb -> "lb"
            PreferredWeightUnit.Mixed -> strings.mixedUnits
        },
    ).joinToString(" · ")

@Composable
private fun peopleCounts(people: PeopleUi): String =
    listOfNotNull(
        people.trainers?.takeIf { it > 0 }?.let(strings()::trainersCount),
        people.trainees?.takeIf { it > 0 }?.let(strings()::traineesCount),
        people.children?.takeIf { it > 0 }?.let(strings()::childrenCount),
        people.guardians?.takeIf { it > 0 }?.let(strings()::guardiansCount),
    ).joinToString(" · ")

@Composable
private fun themeName(mode: ThemeMode): String =
    when (mode) {
        ThemeMode.System -> strings().themeSystem
        ThemeMode.Light -> strings().themeLight
        ThemeMode.Dark -> strings().themeDark
    }

@Composable
private fun languageName(language: AppLanguage): String =
    when (language) {
        AppLanguage.System -> strings().languageSystem
        AppLanguage.English -> "English"
        AppLanguage.Russian -> "Русский"
    }

@Composable
private fun presetName(preset: TransitionPreset): String =
    when (preset) {
        TransitionPreset.None -> strings().transitionNone
        TransitionPreset.Fast -> strings().transitionFast
        TransitionPreset.Normal -> strings().transitionNormal
        TransitionPreset.Smooth -> strings().transitionSmooth
    }
