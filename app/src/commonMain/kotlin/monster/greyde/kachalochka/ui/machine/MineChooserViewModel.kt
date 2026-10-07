package monster.greyde.kachalochka.ui.machine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkId
import monster.greyde.kachalochka.core.domain.gym.MachineLinkRepository
import monster.greyde.kachalochka.core.domain.gym.anyNameMatches
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.account.preferredUnit
import monster.greyde.kachalochka.ui.format.weightCaption
import monster.greyde.kachalochka.ui.friends.reading
import kotlin.time.Clock

data class MineChooserUiState(
    val query: String = "",
    val own: List<ChooserRowUi> = emptyList(),
)

/** The account's machines that [friendMachine], a friend's, can be linked to. */
class MineChooserViewModel(
    private val friendMachine: MachineId,
    name: String,
    private val links: MachineLinkRepository,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val clock: Clock,
    private val sync: SyncTrigger,
    private val profiles: ProfileRepository,
    private val catalogue: MachineCatalogue,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MineChooserUiState(query = name))
    val state: StateFlow<MineChooserUiState> = mutableState
    private val writes = WriteGuard(viewModelScope)

    private var shown: ShownMachines? = null
    private var preferred = PreferredWeightUnit.Kg
    private var shownFor: UserId? = null
    private var loading: Job? = null

    /** The screen follows whoever is active, wherever the switch came from. */
    init {
        viewModelScope.launch { accounts.activeId.collect { load() } }
    }

    /** Own machines show at once; the friends' links that join some of them follow online. */
    fun load() {
        loading?.cancel()
        loading =
            viewModelScope.launch {
                val owner = currentUser.id()
                val own = catalogue.own(owner)
                preferred = profiles.preferredUnit(owner)
                shownFor = owner
                shown = ShownMachines(own, null)
                publish()
                owner ?: return@launch
                val group = reading { catalogue.group(owner) }.getOrNull() ?: return@launch
                if (currentUser.id() != owner) return@launch
                shown = ShownMachines(own, group)
                publish()
            }
    }

    fun onQueryChange(query: String) {
        mutableState.value = mutableState.value.copy(query = query)
        publish()
    }

    fun choose(
        id: MachineId,
        onDone: () -> Unit,
    ) {
        val current = shown ?: return
        if (current.own.machines.none { it.id == id }) return
        if (current.clusters.sameMachine(id, friendMachine)) return
        val offeredTo = shownFor
        writes.launch {
            val owner = currentUser.id() ?: return@launch
            if (owner != offeredTo) return@launch
            val now = clock.now()
            links.upsert(MachineLink(MachineLinkId.random(), owner, id, friendMachine, now, false))
            sync.request()
            onDone()
        }
    }

    private fun publish() {
        val current = shown ?: return
        val needle = mutableState.value.query.trim()
        mutableState.value =
            mutableState.value.copy(
                own =
                    current.own.machines
                        .filter { !current.clusters.sameMachine(it.id, friendMachine) }
                        .filter {
                            anyNameMatches(
                                listOf(it.name) + current.linkedNames(it),
                                needle,
                            )
                        }.sortedBy { it.name.lowercase() }
                        .map {
                            ChooserRowUi(
                                it.id,
                                it.name,
                                weightCaption(it, preferred),
                                current.cover(it.id),
                                current.linkedWith(it.id),
                            )
                        },
            )
    }
}
