package monster.greyde.kachalochka.ui.machine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.components.AccentButton
import monster.greyde.kachalochka.ui.components.OutlineButton
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.friends.OfflineNotice
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.photos.PhotoStrip
import monster.greyde.kachalochka.ui.photos.PhotoViewer
import monster.greyde.kachalochka.ui.photos.ShownPhoto
import monster.greyde.kachalochka.ui.share.TextCopying
import monster.greyde.kachalochka.ui.stats.MachineStats
import monster.greyde.kachalochka.ui.stats.PeriodChips
import monster.greyde.kachalochka.ui.strings.strings
import monster.greyde.kachalochka.ui.visit.TagChip
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration.Companion.seconds

@Composable
fun FriendMachineScreen(
    machineId: MachineId,
    ownerId: UserId,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onTaken: (MachineId) -> Unit,
    onLinkToMine: (name: String) -> Unit = {},
    onOpenMachine: (MachineId) -> Unit = {},
    onOpenFriendMachine: (MachineId, UserId) -> Unit = { _, _ -> },
    /** True once "Привязать к моему" has written a link, so the links are read again. */
    linked: Boolean = false,
    onLinkedConsumed: () -> Unit = {},
    onOpenVisit: (CalendarDay) -> Unit = {},
    onNewPlan: (MachineId) -> Unit = {},
) {
    val viewModel: FriendMachineViewModel = koinViewModel { parametersOf(machineId, ownerId) }
    LaunchedEffect(Unit) { viewModel.speak() }
    LaunchedEffect(linked) {
        if (linked) {
            viewModel.refresh()
            onLinkedConsumed()
        }
    }
    val state by viewModel.state.collectAsState()
    val offline by viewModel.offline.collectAsState()
    var opened by remember { mutableStateOf<ShownPhoto?>(null) }
    val placement: MachinePlacementViewModel = koinViewModel()
    // Taking is offered only while no own machine stands in, so only then is a copy written.
    val onStoodIn = { if (state?.canTake == true) viewModel.refresh() }
    // The view model already loads once created: it follows accounts.activeId from init.
    Screen(
        strings().friendMachine,
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        actions = {
            if (state != null && !offline) {
                MachineMenu(
                    onToPlan = { placement.choosePlan(viewModel.placed()) },
                    onToday = { placement.planToday(viewModel.placed(), onStoodIn, onOpenVisit) },
                )
            }
        },
    ) {
        val current = state
        PlacementNotice(placement, machineId, Modifier.padding(horizontal = 16.dp))
        if (offline) {
            OfflineNotice(onRetry = viewModel::refresh)
        } else if (current != null) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            current.name,
                            modifier =
                                Modifier
                                    .weight(
                                        1f,
                                        fill = false,
                                    ).testTag("friend-machine-name"),
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        CopyButton(current.name, "copy-machine-name")
                    }
                    Text(
                        current.owner,
                        modifier = Modifier.testTag("friend-machine-owner"),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                if (current.linkedWith.isNotEmpty()) {
                    LinkedMachines(current.linkedWith, "friend-machine-linked") {
                        if (it.own) {
                            onOpenMachine(it.machineId)
                        } else {
                            onOpenFriendMachine(it.machineId, it.owner.userId)
                        }
                    }
                }
                if (current.tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        current.tags.forEach {
                            TagChip(it, Modifier.testTag("friend-machine-tag-$it"))
                        }
                    }
                }
                PhotoStrip(current.photos, launchers = null, onOpen = { opened = it })
                if (current.note.isNotBlank()) {
                    Setting(
                        strings().setupNote,
                        current.note,
                        "friend-machine-note",
                        copyable = true,
                    )
                }
                Setting(strings().howWeightCounts, current.caption, "friend-machine-caption")
                current.platform?.let {
                    Setting(
                        strings().platformWeight,
                        it,
                        "friend-machine-platform",
                    )
                }
                PeriodChips(current.period, viewModel::choosePeriod)
                Column { MachineStats(current.stats) }
            }
            if (current.canTake) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlineButton(
                        strings().linkToMine,
                        PhosphorIcons.LinkSimple,
                        { onLinkToMine(current.name) },
                        Modifier.fillMaxWidth().testTag("link-to-mine"),
                    )
                    AccentButton(
                        strings().takeForMyself,
                        PhosphorIcons.Copy,
                        { viewModel.take(onTaken) },
                        Modifier.testTag("take-machine"),
                    )
                }
            }
        }
    }
    PlanChooser(placement, onNewPlan, onStoodIn)
    opened?.let { PhotoViewer(state?.photos.orEmpty(), it, onClose = { opened = null }) }
}

@Composable
private fun Setting(
    label: String,
    value: String,
    tag: String,
    copyable: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Column {
        Text(label, fontSize = 12.sp, color = colors.onBackground.copy(alpha = 0.48f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                value,
                modifier = Modifier.weight(1f, fill = false).testTag(tag),
                fontSize = 16.sp,
                color = colors.onBackground,
            )
            if (copyable) CopyButton(value, "copy-$tag")
        }
    }
}

/** How long a copy button shows its check after a copy. */
private val COPIED_SHOWN = 2.seconds

/** Puts [text] on the clipboard; its check confirms the copy, since the web shows nothing. */
@Composable
private fun CopyButton(
    text: String,
    tag: String,
) {
    val copying: TextCopying = koinInject()
    val scope = rememberCoroutineScope()
    var copied by remember(text) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_SHOWN)
            copied = false
        }
    }
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable { scope.launch { copied = copying.copy(text) } }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (copied) PhosphorIcons.Check else PhosphorIcons.Copy,
            strings().copyText,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp),
        )
    }
}
