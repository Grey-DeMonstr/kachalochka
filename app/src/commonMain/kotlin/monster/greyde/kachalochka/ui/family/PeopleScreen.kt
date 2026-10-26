package monster.greyde.kachalochka.ui.family

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.family.FamilyMember
import monster.greyde.kachalochka.ui.account.PersonAvatar
import monster.greyde.kachalochka.ui.components.ConfirmDialog
import monster.greyde.kachalochka.ui.components.ControlShape
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.components.TextInput
import monster.greyde.kachalochka.ui.friends.OfflineNotice
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.settings.BottomSheet
import monster.greyde.kachalochka.ui.settings.SettingsCard
import monster.greyde.kachalochka.ui.settings.SettingsGroup
import monster.greyde.kachalochka.ui.strings.strings
import org.koin.compose.viewmodel.koinViewModel

/** Every person the account is linked to, by how they are linked, with one "Добавить". */
@Composable
fun PeopleScreen(onBack: () -> Unit) {
    val viewModel: PeopleViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    var adding by remember { mutableStateOf(false) }
    val faded = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
    Screen(
        strings().people,
        onBack = onBack,
        onOpenSettings = null,
        actions = { AddButton { adding = true } },
    ) {
        if (state.offline) {
            OfflineNotice(onRetry = viewModel::load)
            return@Screen
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 17.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            val children = state.children
            val guardians = state.guardians
            val trainers = state.trainers
            val trainees = state.trainees
            when {
                children == null || guardians == null || trainers == null || trainees == null ->
                    Text(
                        strings().loading,
                        Modifier.testTag("people-loading"),
                        fontSize = 15.sp,
                        color = faded,
                    )
                listOf(children, guardians, trainers, trainees).all { it.isEmpty() } ->
                    Text(
                        strings().noPeople,
                        Modifier.testTag("people-empty"),
                        fontSize = 15.sp,
                        color = faded,
                    )
                else -> {
                    val s = strings()
                    PeopleGroup(
                        s.trainers,
                        trainers,
                        Relation.Trainer,
                        "people-trainers",
                        viewModel,
                    )
                    PeopleGroup(
                        s.trainees,
                        trainees,
                        Relation.Trainee,
                        "people-trainees",
                        viewModel,
                    )
                    PeopleGroup(s.children, children, Relation.Child, "people-children", viewModel)
                    PeopleGroup(
                        s.guardians,
                        guardians,
                        Relation.Guardian,
                        "people-guardians",
                        viewModel,
                    )
                }
            }
            state.notice?.let {
                Text(it, Modifier.testTag("people-notice"), fontSize = 15.sp, color = faded)
            }
        }
    }
    if (adding) {
        AddSheet(
            onChosen = { add ->
                adding = false
                add(viewModel)
            },
            onDismiss = { adding = false },
        )
    }
    state.offer?.let { OfferDialog(it, viewModel) }
    state.entry?.let { EntryDialog(it, viewModel) }
    state.removing?.let { removal ->
        val s = strings()
        val (title, text) =
            when (removal.relation) {
                Relation.Child -> s.removeChildTitle to s.removeChildText
                Relation.Guardian -> s.removeGuardianTitle to s.removeGuardianText
                Relation.Trainee -> s.removeTraineeTitle to s.removeTraineeText
                Relation.Trainer -> s.removeTrainerTitle to s.removeTrainerText
            }
        ConfirmDialog(
            title,
            text,
            strings().removeLink,
            "person-remove-confirm",
            "person-remove-cancel",
            onConfirm = viewModel::confirmRemove,
            onCancel = viewModel::cancelRemove,
        )
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .height(48.dp)
            .clip(ControlShape)
            .border(1.dp, colors.primary, ControlShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp)
            .testTag("people-add"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            PhosphorIcons.Plus,
            null,
            tint = colors.onPrimaryContainer,
            modifier = Modifier.size(18.dp),
        )
        Text(strings().add, fontSize = 15.sp, color = colors.onPrimaryContainer)
    }
}

/** An empty group is left out. */
@Composable
private fun PeopleGroup(
    label: String,
    people: List<FamilyMember>,
    relation: Relation,
    tag: String,
    viewModel: PeopleViewModel,
) {
    if (people.isEmpty()) return
    Box(Modifier.testTag(tag)) {
        SettingsGroup(label) {
            SettingsCard {
                people.forEachIndexed { index, person ->
                    if (index > 0) {
                        Box(
                            Modifier
                                .padding(start = 62.dp)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                                ),
                        )
                    }
                    PersonRow(person, onRemove = { viewModel.askToRemove(person, relation) })
                }
            }
        }
    }
}

@Composable
private fun PersonRow(
    person: FamilyMember,
    onRemove: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val id = person.userId.value
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = 14.dp, end = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(person.userId, person.displayName, person.avatar, size = 36.dp)
        Text(
            person.displayName,
            Modifier.weight(1f),
            fontSize = 16.sp,
            color = colors.onBackground,
        )
        Box {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(ControlShape)
                    .clickable { menu = true }
                    .testTag("person-menu-$id"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    PhosphorIcons.DotsThreeVertical,
                    strings().more,
                    tint = colors.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(strings().removeLink, color = colors.error) },
                    leadingIcon = {
                        Icon(
                            PhosphorIcons.UserMinus,
                            null,
                            tint = colors.error,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = {
                        menu = false
                        onRemove()
                    },
                    modifier = Modifier.testTag("person-remove-$id"),
                )
            }
        }
    }
}

/** The roles in the design's order; [onChosen] gets what the chosen one starts. */
@Composable
private fun AddSheet(
    onChosen: (PeopleViewModel.() -> Unit) -> Unit,
    onDismiss: () -> Unit,
) = BottomSheet(strings().whomToAdd, onDismiss) {
    val s = strings()
    AddOption(PhosphorIcons.ChalkboardTeacher, s.addTrainerChoice, "add-trainer") {
        onChosen { addTrainer() }
    }
    AddOption(PhosphorIcons.Student, s.addTraineeChoice, "add-trainee") {
        onChosen { addTrainee() }
    }
    AddOption(PhosphorIcons.Baby, s.addChildChoice, "add-child") { onChosen { addChild() } }
    AddOption(PhosphorIcons.Users, s.addGuardianChoice, "add-guardian") {
        onChosen { addGuardian() }
    }
}

@Composable
private fun AddOption(
    icon: ImageVector,
    title: String,
    tag: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(12.dp)
            .testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .border(1.dp, colors.onSurface.copy(alpha = 0.18f), shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                null,
                tint = colors.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp),
            )
        }
        Text(title, Modifier.weight(1f), fontSize = 16.sp, color = colors.onSurface)
    }
}

/** The account's new code for a child or a trainee, with how to use it and "Поделиться". */
@Composable
private fun OfferDialog(
    offer: CodeOffer,
    viewModel: PeopleViewModel,
) {
    val colors = MaterialTheme.colorScheme
    val s = strings()
    val family = offer.kind == LinkKind.Family
    AlertDialog(
        onDismissRequest = viewModel::closeOffer,
        title = { Text(if (family) s.addChild else s.addTrainee) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (family) s.childCode(offer.code) else s.traineeCode(offer.code),
                    Modifier.testTag("offer-code"),
                    fontSize = 17.sp,
                )
                offer.link?.let {
                    Text(it, Modifier.testTag("offer-code-link"), fontSize = 14.sp)
                }
                Text(
                    if (family) s.childCodeHint else s.traineeCodeHint,
                    Modifier.testTag("offer-code-hint"),
                    fontSize = 14.sp,
                    color = colors.onSurface.copy(alpha = 0.6f),
                )
                offer.notice?.let {
                    Text(it, Modifier.testTag("offer-code-notice"), fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = viewModel::share,
                modifier = Modifier.testTag("offer-code-share"),
            ) { Text(s.share) }
        },
        dismissButton = {
            TextButton(
                onClick = viewModel::closeOffer,
                modifier = Modifier.testTag("offer-code-close"),
            ) { Text(s.done) }
        },
    )
}

/** A field for a parent's or a trainer's code, under what they can do once linked. */
@Composable
private fun EntryDialog(
    entry: CodeEntry,
    viewModel: PeopleViewModel,
) {
    val colors = MaterialTheme.colorScheme
    val s = strings()
    val family = entry.kind == LinkKind.Family
    AlertDialog(
        onDismissRequest = viewModel::closeEntry,
        title = { Text(if (family) s.addGuardian else s.addTrainer) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (family) s.guardianRights else s.trainerRights,
                    Modifier.testTag("entry-rights"),
                    fontSize = 15.sp,
                    color = colors.onSurface.copy(alpha = 0.6f),
                )
                TextInput(
                    entry.code,
                    if (family) s.guardianCode else s.trainerCode,
                    viewModel::type,
                    Modifier.fillMaxWidth().testTag("entry-code-field"),
                )
                entry.error?.let {
                    Text(it, Modifier.testTag("entry-code-error"), color = colors.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = viewModel::link,
                enabled = entry.canAdd,
                modifier = Modifier.testTag("entry-link"),
            ) { Text(s.add) }
        },
        dismissButton = {
            TextButton(
                onClick = viewModel::closeEntry,
                modifier = Modifier.testTag("entry-cancel"),
            ) { Text(s.cancel) }
        },
    )
}
