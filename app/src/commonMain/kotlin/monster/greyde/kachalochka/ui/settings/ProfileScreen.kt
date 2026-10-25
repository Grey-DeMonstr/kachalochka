package monster.greyde.kachalochka.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import coil3.compose.AsyncImage
import monster.greyde.kachalochka.core.domain.measures.DEFAULT_CYCLE_LENGTH
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.Sex
import monster.greyde.kachalochka.ui.account.PersonAvatar
import monster.greyde.kachalochka.ui.components.Choice
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.components.SegmentRow
import monster.greyde.kachalochka.ui.components.TextInput
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.photos.PhotoCapture
import monster.greyde.kachalochka.ui.photos.photoLoader
import monster.greyde.kachalochka.ui.strings.strings
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** The profile form; every change is saved on its own, so there is nothing to confirm. */
@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val viewModel: ProfileViewModel = koinViewModel()
    val profile by viewModel.profile.collectAsState()
    val leave = {
        viewModel.leave()
        onBack()
    }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCompleted = leave,
    )
    Screen(strings().profile, onBack = leave, onOpenSettings = null) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 17.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            profile?.let { ProfileFields(it, viewModel) }
        }
        Row(
            Modifier
                .padding(
                    start = 17.dp,
                    end = 17.dp,
                    bottom = 20.dp,
                ).testTag("profile-saved-note"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                PhosphorIcons.CheckCircle,
                null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(16.dp),
            )
            Hint(strings().savedAtOnce)
        }
    }
}

@Composable
private fun ProfileFields(
    profile: ProfileUi,
    viewModel: ProfileViewModel,
) {
    val avatar by viewModel.avatar.collectAsState()
    profile.nickname?.let {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            avatar?.let { shown -> AvatarChooser(shown, viewModel) }
            Field(strings().nickname, Modifier.weight(1f)) {
                TextInput(it, profile.placeholder, viewModel::type, Modifier.testTag("nickname"))
            }
        }
    }
    Field(strings().sex) {
        val sexes = listOf(Sex.Male, Sex.Female)
        SegmentRow(
            listOf(Choice(strings().male, "sex-male"), Choice(strings().female, "sex-female")),
            selected = sexes.indexOf(profile.sex),
            onSelect = { viewModel.chooseSex(sexes[it]) },
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(strings().birthDate, Modifier.weight(1.4f)) {
                TextInput(
                    profile.birthDate,
                    strings().datePlaceholder,
                    viewModel::typeBirthDate,
                    Modifier.testTag("birth-date"),
                    KeyboardOptions(keyboardType = KeyboardType.Number),
                    valid = profile.birthDateValid,
                )
            }
            Field(strings().heightCm, Modifier.weight(1f)) {
                TextInput(
                    profile.height,
                    "",
                    viewModel::typeHeight,
                    Modifier.testTag("height"),
                    KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    valid = profile.heightValid,
                )
            }
        }
        Hint(strings().bodyFieldsHint)
    }
    if (profile.showsCycleLength) {
        Field(strings().cycleLength) {
            TextInput(
                profile.cycleLength,
                DEFAULT_CYCLE_LENGTH.toString(),
                viewModel::typeCycleLength,
                Modifier.testTag("cycle-length"),
                KeyboardOptions(keyboardType = KeyboardType.Number),
                valid = profile.cycleLengthValid,
            )
        }
    }
    Field(strings().weightUnits) {
        val units =
            listOf(PreferredWeightUnit.Kg, PreferredWeightUnit.Lb, PreferredWeightUnit.Mixed)
        SegmentRow(
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
}

@Composable
private fun Field(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
        )
        content()
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
    )
}

/** The avatar with a camera mark; tapping it offers what adding a machine's photo does, and more. */
@Composable
private fun AvatarChooser(
    avatar: AvatarUi,
    viewModel: ProfileViewModel,
) {
    val colors = MaterialTheme.colorScheme
    val capture: PhotoCapture = koinInject()
    val launchers = capture.rememberLaunchers(viewModel::chooseAvatar)
    var choosing by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .size(80.dp)
                .clip(CircleShape)
                .clickable(enabled = launchers != null || avatar.canRemove) { choosing = true }
                .testTag("profile-avatar"),
        ) {
            val chosen = avatar.chosen
            if (chosen == null) {
                PersonAvatar(avatar.owner, avatar.name, avatar.shown, size = 80.dp)
            } else {
                AsyncImage(
                    model = chosen,
                    contentDescription = null,
                    imageLoader = photoLoader(),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(28.dp)
                .clip(CircleShape)
                .background(colors.surfaceVariant)
                .border(1.dp, colors.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                PhosphorIcons.Camera,
                null,
                tint = colors.tertiary,
                modifier = Modifier.size(15.dp),
            )
        }
        DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
            launchers?.let {
                DropdownMenuItem(
                    text = { Text(strings().takePhoto) },
                    leadingIcon = { Icon(PhosphorIcons.Camera, null, Modifier.size(20.dp)) },
                    onClick = {
                        choosing = false
                        it.takePhoto()
                    },
                    modifier = Modifier.testTag("take-photo"),
                )
                DropdownMenuItem(
                    text = { Text(strings().fromGallery) },
                    leadingIcon = { Icon(PhosphorIcons.Image, null, Modifier.size(20.dp)) },
                    onClick = {
                        choosing = false
                        it.pickPhoto()
                    },
                    modifier = Modifier.testTag("pick-photo"),
                )
            }
            if (avatar.canRemove) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(strings().delete)
                            Text(
                                strings().googlePictureReturns,
                                fontSize = 12.sp,
                                color = colors.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    },
                    leadingIcon = { Icon(PhosphorIcons.Trash, null, Modifier.size(20.dp)) },
                    onClick = {
                        choosing = false
                        viewModel.removeAvatar()
                    },
                    modifier = Modifier.testTag("avatar-remove"),
                )
            }
        }
    }
}
