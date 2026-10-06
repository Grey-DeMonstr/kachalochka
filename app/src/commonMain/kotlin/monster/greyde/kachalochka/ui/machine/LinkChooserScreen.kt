package monster.greyde.kachalochka.ui.machine

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.ui.components.ControlShape
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.components.SectionLabel
import monster.greyde.kachalochka.ui.strings.strings
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun LinkChooserScreen(
    machineId: MachineId,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onMerged: (kept: MachineId) -> Unit,
    onLinked: (copyFrom: MachineId?) -> Unit,
) {
    val viewModel: LinkChooserViewModel = koinViewModel { parametersOf(machineId) }
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    Screen(strings().linkTo, onBack = onBack, onOpenSettings = onOpenSettings) {
        SearchBar(state.query, viewModel::onQueryChange)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            state.error?.let {
                Text(
                    it,
                    modifier =
                        Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("chooser-error"),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (state.own.isNotEmpty()) ChooserSection(strings().myMachines, "chooser-own")
            state.own.forEach { row ->
                MachineRow(row.name, row.detail, "chooser-own-${row.id.value}", row.photo) {
                    viewModel.chooseOwn(row.id)
                }
            }
            state.friendGroups?.takeIf { it.isNotEmpty() }?.let { groups ->
                ChooserSection(strings().friendsMachines, "chooser-friends")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(state.copySettings, onValueChange = viewModel::setCopySettings)
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("copy-settings"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = state.copySettings, onCheckedChange = null)
                    Text(strings().copySettings, Modifier.padding(start = 8.dp), fontSize = 15.sp)
                }
                groups.forEach { group ->
                    LinkedGroup(group.size > 1, "chooser-group-${group.first().id.value}") {
                        group.forEach { row ->
                            MachineRow(
                                row.name,
                                row.detail,
                                "chooser-friend-${row.id.value}",
                                row.photo,
                            ) { viewModel.chooseFriend(row.id, onLinked) }
                        }
                    }
                }
            }
        }
    }
    state.merge?.let { merge ->
        MergeDialog(
            merge,
            onKeep = viewModel::keep,
            onAdjust = viewModel::setAdjust,
            onConfirm = { viewModel.confirmMerge(onMerged) },
            onCancel = viewModel::cancelMerge,
        )
    }
}

@Composable
private fun MergeDialog(
    merge: MergeUi,
    onKeep: (MachineId) -> Unit,
    onAdjust: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(strings().mergeTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings().mergeKeep, fontSize = 13.sp, color = colors.onSurfaceVariant)
                merge.choices.forEach { choice ->
                    KeepChoice(choice, selected = choice.id == merge.kept) { onKeep(choice.id) }
                }
                Text(merge.text)
                merge.adjustText?.let {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(merge.adjust, onValueChange = onAdjust)
                            .testTag("merge-adjust"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = merge.adjust, onCheckedChange = null)
                        Text(it, Modifier.padding(start = 8.dp), fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm-merge")) {
                Text(strings().merge, color = colors.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("cancel-merge")) {
                Text(strings().cancel)
            }
        },
    )
}

@Composable
private fun KeepChoice(
    choice: MergeChoiceUi,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected, onClick = onSelect)
            .testTag("merge-keep-${choice.id.value}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 8.dp)) {
            Text(choice.name, fontSize = 15.sp)
            Text(choice.detail, fontSize = 13.sp, color = colors.onSurfaceVariant)
            if (choice.suggested) {
                Text(
                    strings().suggestedKeep,
                    Modifier.testTag("merge-suggested-${choice.id.value}"),
                    fontSize = 13.sp,
                    color = colors.tertiary,
                )
            }
        }
    }
}

/** Machines already linked to each other, framed as one physical machine. */
@Composable
private fun LinkedGroup(
    framed: Boolean,
    tag: String,
    content: @Composable () -> Unit,
) {
    if (!framed) {
        content()
        return
    }
    Column(
        Modifier
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(ControlShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, ControlShape)
            .testTag(tag),
    ) { content() }
}

@Composable
private fun ChooserSection(
    title: String,
    tag: String,
) = SectionLabel(
    title,
    modifier =
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            .testTag(tag),
)
