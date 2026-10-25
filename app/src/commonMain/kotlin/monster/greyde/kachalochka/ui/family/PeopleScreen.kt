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
            when {
                children == null || guardians == null ->
                    Text(
                        strings().loading,
                        Modifier.testTag("people-loading"),
                        fontSize = 15.sp,
                        color = faded,
                    )
                children.isEmpty() && guardians.isEmpty() ->
                    Text(
                        strings().noPeople,
                        Modifier.testTag("people-empty"),
                        fontSize = 15.sp,
                        color = faded,
                    )
                else -> {
                    PeopleGroup(strings().children, children, "people-children", viewModel)
                    PeopleGroup(strings().guardians, guardians, "people-guardians", viewModel)
                }
            }
            state.notice?.let {
                Text(it, Modifier.testTag("people-notice"), fontSize = 15.sp, color = faded)
            }
        }
    }
    if (adding) {
        AddSheet(
            onChild = {
                adding = false
                viewModel.addChild()
            },
            onGuardian = {
                adding = false
                viewModel.addGuardian()
            },
            onDismiss = { adding = false },
        )
    }
    state.childInvite?.let { ChildInviteDialog(it, viewModel) }
    state.guardianEntry?.let { GuardianEntryDialog(it, viewModel) }
    state.removing?.let { person ->
        val child = state.children.orEmpty().any { it.userId == person.userId }
        ConfirmDialog(
            if (child) strings().removeChildTitle else strings().removeGuardianTitle,
            if (child) strings().removeChildText else strings().removeGuardianText,
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
                    PersonRow(person, onRemove = { viewModel.askToRemove(person) })
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

@Composable
private fun AddSheet(
    onChild: () -> Unit,
    onGuardian: () -> Unit,
    onDismiss: () -> Unit,
) = BottomSheet(strings().whomToAdd, onDismiss) {
    AddOption(
        PhosphorIcons.Baby,
        strings().addChildChoice,
        strings().addChildChoiceHint,
        "add-child",
        onChild,
    )
    AddOption(
        PhosphorIcons.Users,
        strings().addGuardianChoice,
        strings().addGuardianChoiceHint,
        "add-guardian",
        onGuardian,
    )
}

@Composable
private fun AddOption(
    icon: ImageVector,
    title: String,
    hint: String,
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
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 16.sp, color = colors.onSurface)
            Text(
                hint,
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                color = colors.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun ChildInviteDialog(
    invite: ChildInvite,
    viewModel: PeopleViewModel,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = viewModel::closeChildInvite,
        title = { Text(strings().addChild) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    strings().childCode(invite.code),
                    Modifier.testTag("child-code"),
                    fontSize = 17.sp,
                )
                invite.link?.let {
                    Text(it, Modifier.testTag("child-code-link"), fontSize = 14.sp)
                }
                Text(
                    strings().childCodeHint,
                    Modifier.testTag("child-code-hint"),
                    fontSize = 14.sp,
                    color = colors.onSurface.copy(alpha = 0.6f),
                )
                invite.notice?.let {
                    Text(it, Modifier.testTag("child-code-notice"), fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = viewModel::share,
                modifier = Modifier.testTag("child-code-share"),
            ) { Text(strings().share) }
        },
        dismissButton = {
            TextButton(
                onClick = viewModel::closeChildInvite,
                modifier = Modifier.testTag("child-code-close"),
            ) { Text(strings().done) }
        },
    )
}

@Composable
private fun GuardianEntryDialog(
    entry: GuardianEntry,
    viewModel: PeopleViewModel,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = viewModel::closeGuardianEntry,
        title = { Text(strings().addGuardian) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    strings().guardianRights,
                    Modifier.testTag("guardian-rights"),
                    fontSize = 15.sp,
                    color = colors.onSurface.copy(alpha = 0.6f),
                )
                TextInput(
                    entry.code,
                    strings().guardianCode,
                    viewModel::type,
                    Modifier.fillMaxWidth().testTag("guardian-code-field"),
                )
                entry.error?.let {
                    Text(it, Modifier.testTag("guardian-code-error"), color = colors.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = viewModel::linkGuardian,
                enabled = entry.canAdd,
                modifier = Modifier.testTag("link-guardian"),
            ) { Text(strings().add) }
        },
        dismissButton = {
            TextButton(
                onClick = viewModel::closeGuardianEntry,
                modifier = Modifier.testTag("guardian-code-cancel"),
            ) { Text(strings().cancel) }
        },
    )
}
