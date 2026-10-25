package monster.greyde.kachalochka.ui.machine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.PlanId
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.plans.Placement
import monster.greyde.kachalochka.ui.plans.PlanChoiceUi
import monster.greyde.kachalochka.ui.strings.AppStrings

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
 * Puts the machine of a machine screen into a plan or into today's visit, as the account's own
 * machine that [MachineCatalogue.standIn] gives for it. Every write is for the account the screen
 * offered the action to (spec §4.3).
 */
class MachinePlacementViewModel(
    private val placement: Placement,
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
        writes.launch { standIn(owner, machine, onStoodIn)?.let(onOpen) }
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

    /** [owner]'s machine for [machine]; null, with a notice when there is something to say. */
    private suspend fun standIn(
        owner: UserId?,
        machine: PlacedMachine,
        onStoodIn: () -> Unit,
    ): MachineId? =
        when (val found = catalogue.standIn(owner, machine)) {
            is StandIn.Found -> found.machine.id.also { if (it != machine.id) onStoodIn() }
            StandIn.Offline -> null.also { notice(machine, AppStrings.current.offline) }
            StandIn.Gone -> null.also { notice(machine, AppStrings.current.machineGone) }
            StandIn.Switched -> null
        }

    private fun notice(
        machine: PlacedMachine,
        text: String,
    ) {
        mutableState.value = mutableState.value.copy(notice = PlacementNoticeUi(machine.id, text))
    }
}
