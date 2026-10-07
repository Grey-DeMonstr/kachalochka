package monster.greyde.kachalochka.ui.machine

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.gym.MachineSort
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.ui.account.ProfileChoice
import monster.greyde.kachalochka.ui.account.UnsavedChoices
import monster.greyde.kachalochka.ui.components.ChipRow
import monster.greyde.kachalochka.ui.components.IconChoiceChip
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.strings.strings
import kotlin.time.Clock

/** The account's machine sort, kept in `profile.machine_sort` (spec §4.5). */
fun machineSortChoice(
    profiles: ProfileRepository,
    currentUser: CurrentUser,
    clock: Clock,
    sync: SyncTrigger,
    unsaved: UnsavedChoices,
    scope: CoroutineScope,
): ProfileChoice<MachineSort> =
    ProfileChoice(
        profiles,
        currentUser,
        clock,
        sync,
        unsaved,
        scope,
        key = "machine_sort",
        stored = { it?.machineSort ?: MachineSort.Recent },
        written = { profile, sort -> profile.copy(machineSort = sort) },
    )

@Composable
fun machineSortLabels(): List<Pair<MachineSort, String>> {
    val s = strings()
    return listOf(
        MachineSort.Recent to s.recent,
        MachineSort.Name to s.sortName,
        MachineSort.Frequent to s.sortFrequent,
    )
}

/**
 * The sort orders as chips, drawn like the tags; with [onlySuggested] given, a link chip follows
 * them, which keeps only the machines with a suggested link.
 */
@Composable
fun SortChips(
    chosen: MachineSort,
    onChoose: (MachineSort) -> Unit,
    modifier: Modifier = Modifier,
    onlySuggested: Boolean? = null,
    onToggleSuggested: () -> Unit = {},
) = ChipRow(
    machineSortLabels(),
    chosen,
    onChoose,
    modifier,
    edge = 16.dp,
    extra = {
        onlySuggested?.let {
            IconChoiceChip(
                PhosphorIcons.LinkSimple,
                strings().suggestedLinks,
                it,
                "only-suggested",
                onToggleSuggested,
            )
        }
    },
) {
    "sort-${it.name.lowercase()}"
}
