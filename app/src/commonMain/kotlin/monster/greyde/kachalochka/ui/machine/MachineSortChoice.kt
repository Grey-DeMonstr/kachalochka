package monster.greyde.kachalochka.ui.machine

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.gym.MachineSort
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.ui.components.ChoiceChip
import monster.greyde.kachalochka.ui.strings.strings
import kotlin.time.Clock

/** The account's machine sort, kept in its profile so it follows the account (spec §4.6). */
class MachineSortChoice(
    private val profiles: ProfileRepository,
    private val currentUser: CurrentUser,
    private val clock: Clock,
    private val sync: SyncTrigger,
    private val scope: CoroutineScope,
) {
    var current: MachineSort = MachineSort.Recent
        private set

    /** Counts choices, so a profile read before the latest one cannot undo it. */
    private var choices = 0
    private var writing: Job? = null

    suspend fun read(owner: UserId?) {
        val asked = choices
        val stored = profiles.forOwner(owner)?.machineSort ?: MachineSort.Recent
        if (asked == choices) current = stored
    }

    fun choose(sort: MachineSort) {
        current = sort
        choices++
        val previous = writing
        writing =
            scope.launch {
                // One write at a time, so the last choice is the one the server keeps.
                previous?.join()
                val owner = currentUser.id()
                val now = clock.now()
                val stored = profiles.forOwner(owner) ?: Profile.new(owner, now)
                profiles.upsert(stored.copy(machineSort = sort, updatedAt = now))
                sync.request()
            }
    }
}

/** The sort orders as chips, drawn like the tags. */
@Composable
fun SortChips(
    chosen: MachineSort,
    onChoose: (MachineSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            MachineSort.Recent to s.recent,
            MachineSort.Name to s.sortName,
            MachineSort.Frequent to s.sortFrequent,
        ).forEach { (sort, label) ->
            ChoiceChip(label, sort == chosen, "sort-${sort.name.lowercase()}") { onChoose(sort) }
        }
    }
}
