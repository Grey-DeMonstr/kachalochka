package monster.greyde.kachalochka.ui.machine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.PlanId
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.friends.reading
import monster.greyde.kachalochka.ui.plans.Placement
import monster.greyde.kachalochka.ui.plans.PlanChoiceUi
import monster.greyde.kachalochka.ui.strings.AppStrings

/**
 * A machine a screen shows: the account's own, or a group mate's when [ofFriend]. [group] is the
 * group read the screen holds, used instead of a new one when it was made for the active account.
 */
data class PlacedMachine(
    val id: MachineId,
    val ofFriend: Boolean = false,
    val group: GroupMachines? = null,
)

/** [machine] is the one the screen showed, so another machine's screen shows nothing. */
data class PlacementNoticeUi(
    val machine: MachineId,
    val text: String,
)

data class PlacementUi(
    /** The plans offered while "В план…" asks which; null while it does not ask. */
    val choices: List<PlanChoiceUi>? = null,
    val notice: PlacementNoticeUi? = null,
)

/**
 * Puts the machine of a machine screen into a plan or into today's visit. A friend's machine, or
 * another account's on the device, goes in as the account's machine of its cluster, else as a new
 * linked copy. Every write checks first that the account it is for is still the active one (spec
 * §4.3).
 */
class MachinePlacementViewModel(
    private val placement: Placement,
    private val machines: MachineRepository,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val catalogue: MachineCatalogue,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PlacementUi())
    val state: StateFlow<PlacementUi> = mutableState
    private val writes = WriteGuard(viewModelScope)

    /** The machine the open choice is for, and the account it was offered to. */
    private var choosing: Pair<PlacedMachine, UserId?>? = null

    init {
        viewModelScope.launch {
            accounts.activeId.collect {
                choosing = null
                mutableState.value = PlacementUi()
            }
        }
    }

    fun choosePlan(machine: PlacedMachine) {
        val owner = accounts.activeId.value
        viewModelScope.launch {
            val choices = placement.choices(owner) ?: return@launch
            choosing = machine to owner
            mutableState.value = PlacementUi(choices = choices)
        }
    }

    fun cancelChoice() {
        choosing = null
        mutableState.value = mutableState.value.copy(choices = null)
    }

    /** [onStoodIn] follows once another account's machine has the account's own standing in. */
    fun addTo(
        plan: PlanId,
        onStoodIn: () -> Unit = {},
    ) {
        val title =
            mutableState.value.choices
                ?.firstOrNull { it.id == plan }
                ?.title ?: return
        val (machine, owner) = choosing ?: return
        cancelChoice()
        writes.launch {
            val own = standIn(owner, machine, onStoodIn) ?: return@launch
            if (!placement.addToPlan(owner, plan, listOf(own))) return@launch
            notice(machine, AppStrings.current.addedTo(title))
        }
    }

    fun newPlan(
        onStoodIn: () -> Unit = {},
        onOpen: (MachineId) -> Unit,
    ) {
        val (machine, owner) = choosing ?: return
        cancelChoice()
        writes.launch {
            val own = standIn(owner, machine, onStoodIn) ?: return@launch
            if (stillActive(owner)) onOpen(own)
        }
    }

    /** Plans the machine in today's visit, then hands on today. */
    fun planToday(
        machine: PlacedMachine,
        onStoodIn: () -> Unit = {},
        onOpen: (CalendarDay) -> Unit,
    ) {
        val owner = accounts.activeId.value
        writes.launch {
            val own = standIn(owner, machine, onStoodIn) ?: return@launch
            placement.planToday(owner, listOf(own))?.let(onOpen)
        }
    }

    fun dismissNotice() {
        mutableState.value = mutableState.value.copy(notice = null)
    }

    /**
     * [owner]'s machine for [machine], or null when there is none to use, the read failed or
     * another account became active meanwhile.
     */
    private suspend fun standIn(
        owner: UserId?,
        machine: PlacedMachine,
        onStoodIn: () -> Unit,
    ): MachineId? {
        if (!stillActive(owner)) return null
        if (!machine.ofFriend) return ownMachine(owner, machine, onStoodIn)
        val viewer = owner ?: return null
        val group =
            machine.group?.takeIf { it.owner == viewer }
                ?: reading { catalogue.group(viewer) }.getOrElse {
                    if (stillActive(viewer)) notice(machine, AppStrings.current.offline)
                    return null
                }
        if (!stillActive(viewer)) return null
        val own =
            reading { catalogue.friendStandIn(viewer, machine.id, group) }.getOrElse {
                notice(machine, AppStrings.current.offline)
                return null
            }
        if (own == null) {
            notice(machine, AppStrings.current.machineGone)
            return null
        }
        onStoodIn()
        return own.id
    }

    /** Another account's stored machine stands in as in [MachineCatalogue.ownCopy]. */
    private suspend fun ownMachine(
        owner: UserId?,
        machine: PlacedMachine,
        onStoodIn: () -> Unit,
    ): MachineId? {
        val shown = machines.byId(machine.id)?.takeIf { !it.deleted } ?: return null
        if (shown.userId == owner) return shown.id
        val found = catalogue.deviceStandIn(owner, shown)
        if (!stillActive(owner)) return null
        val own = found ?: catalogue.ownCopy(owner, shown)
        onStoodIn()
        return own.id
    }

    private suspend fun stillActive(owner: UserId?) = currentUser.id() == owner

    private fun notice(
        machine: PlacedMachine,
        text: String,
    ) {
        mutableState.value = mutableState.value.copy(notice = PlacementNoticeUi(machine.id, text))
    }
}
