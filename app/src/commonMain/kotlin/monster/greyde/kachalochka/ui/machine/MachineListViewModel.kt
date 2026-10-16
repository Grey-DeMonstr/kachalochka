package monster.greyde.kachalochka.ui.machine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.MachineSort
import monster.greyde.kachalochka.core.domain.gym.tagKey
import monster.greyde.kachalochka.core.domain.gym.toggledTag
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.ui.account.UnsavedChoices
import monster.greyde.kachalochka.ui.account.preferredUnit
import monster.greyde.kachalochka.ui.format.UtcOffset
import kotlin.time.Clock

/** [own] is null until the account's machines are read. */
data class MachineListUiState(
    val own: List<MachineCardUi>? = null,
    val friendSections: List<FriendSectionUi> = emptyList(),
    val sort: MachineSort = MachineSort.Recent,
    val query: String = "",
    val tags: List<TagChoiceUi> = emptyList(),
    /** True when a search or a tag leaves nothing to list. */
    val nothingFound: Boolean = false,
    /** Some own machine could be merged with another. */
    val hasSuggestions: Boolean = false,
    /** Only the own machines with a suggestion are listed. */
    val onlySuggested: Boolean = false,
) {
    val friends: List<MachineCardUi> get() = friendSections.flatMap { it.cards }
}

class MachineListViewModel(
    private val reads: MachineReads,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val sync: SyncTrigger,
    private val profiles: ProfileRepository,
    private val clock: Clock,
    private val utcOffset: UtcOffset,
    unsaved: UnsavedChoices,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MachineListUiState())
    val state: StateFlow<MachineListUiState> = mutableState

    private var read: MachineRead? = null
    private var preferred = PreferredWeightUnit.Kg
    private var query = ""
    private var chosenTags: Set<String> = emptySet()
    private var onlySuggested = false
    private var loading: Job? = null
    private val sorting =
        machineSortChoice(profiles, currentUser, clock, sync, unsaved, viewModelScope)

    /** The list follows whoever is active, wherever the switch came from. */
    init {
        viewModelScope.launch { accounts.activeId.collect { load() } }
        viewModelScope.launch { sync.completed.collect { load() } }
    }

    /** Own machines show at once; friends' follow from the network, if it answers. */
    fun load() {
        loading?.cancel()
        loading =
            viewModelScope.launch {
                val owner = currentUser.id()
                preferred = profiles.preferredUnit(owner)
                sorting.read(owner)
                publish()
                reads.machines(owner, withCards = true).collect {
                    read = it
                    publish()
                }
            }
    }

    fun chooseSort(sort: MachineSort) {
        sorting.choose(sort)
        publish()
    }

    fun onQueryChange(text: String) {
        query = text
        publish()
    }

    fun toggleTag(tag: String) {
        chosenTags = toggledTag(chosenTags, tagKey(tag))
        publish()
    }

    fun toggleSuggested() {
        onlySuggested = !onlySuggested
        publish()
    }

    private fun publish() {
        val sort = sorting.current
        val read = read
        if (read == null) {
            mutableState.value = MachineListUiState(sort = sort, query = query)
            return
        }
        val now = clock.now()
        val offset = utcOffset.at(now)
        val listing =
            read.listing(
                MachineQuery(query, chosenTags, onlySuggested = onlySuggested),
                sort,
                preferred,
                CalendarDay.of(now, offset),
                offset,
            )
        chosenTags = listing.query.tags
        onlySuggested = listing.query.onlySuggested
        mutableState.value =
            MachineListUiState(
                own = listing.own,
                friendSections = listing.friendSections,
                sort = sort,
                query = query,
                tags = listing.tags,
                nothingFound = listing.nothingFound,
                hasSuggestions = listing.hasSuggestions,
                onlySuggested = onlySuggested,
            )
    }
}
