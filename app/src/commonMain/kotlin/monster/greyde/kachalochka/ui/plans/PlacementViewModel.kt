package monster.greyde.kachalochka.ui.plans

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
import monster.greyde.kachalochka.ui.machine.MachineCatalogue
import monster.greyde.kachalochka.ui.machine.PlacedMachine
import monster.greyde.kachalochka.ui.machine.StandIn
import monster.greyde.kachalochka.ui.strings.AppStrings

/** [key] names what the screen showed, so the notice shows under that only. */
data class PlacementNoticeUi(
    val key: String,
    val text: String,
)

data class PlacementUi(
    /** The plans offered while "Add to plan…" asks which; null while it does not ask. */
    val choices: List<PlanChoiceUi>? = null,
    val notice: PlacementNoticeUi? = null,
)

/**
 * Puts an own visit, or shown machines, into a plan or into today's visit. Shown machines go in
 * as the account's machines that [MachineCatalogue.standIns] gives for them. Every write is for
 * the account the screen offered the action to (spec §4.3).
 */
class PlacementViewModel(
    private val placement: Placement,
    private val accounts: Accounts,
    private val catalogue: MachineCatalogue,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PlacementUi())
    val state: StateFlow<PlacementUi> = mutableState
    private val writes = WriteGuard(viewModelScope)

    /**
     * What an open choice is for, and the account it was offered to: the own visit on [day], else
     * [machines].
     */
    private class Choosing(
        val key: String,
        val owner: UserId?,
        val day: CalendarDay?,
        val machines: List<PlacedMachine>,
    )

    private var choosing: Choosing? = null

    init {
        viewModelScope.launch {
            accounts.activeId.collect {
                choosing = null
                mutableState.value = PlacementUi()
            }
        }
    }

    /** Asks which plan takes the machines of the own visit on [day]. */
    fun choosePlan(day: CalendarDay) = choose(Choosing(day.iso, owner(), day, emptyList()))

    /** Asks which plan takes [machines], shown as [key]. */
    fun choosePlan(
        key: String,
        machines: List<PlacedMachine>,
    ) = choose(Choosing(key, owner(), null, machines))

    private fun owner() = accounts.activeId.value

    private fun choose(next: Choosing) {
        viewModelScope.launch {
            val choices = placement.choices(next.owner) ?: return@launch
            choosing = next
            mutableState.value = PlacementUi(choices = choices)
        }
    }

    fun cancelChoice() {
        choosing = null
        mutableState.value = mutableState.value.copy(choices = null)
    }

    /**
     * Appends the machines the plan lacks, in the shown order. [onStoodIn] follows once another
     * account's machine has the account's own standing in.
     */
    fun addTo(
        plan: PlanId,
        onStoodIn: () -> Unit = {},
    ) {
        val title =
            mutableState.value.choices
                ?.firstOrNull { it.id == plan }
                ?.title ?: return
        val choice = choosing ?: return
        cancelChoice()
        writes.launch {
            val owner = choice.owner
            val done =
                choice.day?.let { placement.addVisitToPlan(owner, plan, it) }
                    ?: standIns(choice, onStoodIn)?.let { placement.addToPlan(owner, plan, it) }
            if (done != true) return@launch
            notice(choice.key, AppStrings.current.addedTo(title))
        }
    }

    /** [onOpen] gets the day of an own visit; [onOpenMachines] the stand-ins of shown machines. */
    fun newPlan(
        onOpen: (CalendarDay) -> Unit = {},
        onOpenMachines: (List<MachineId>) -> Unit = {},
        onStoodIn: () -> Unit = {},
    ) {
        val choice = choosing ?: return
        cancelChoice()
        val day = choice.day
        if (day != null) {
            onOpen(day)
        } else {
            writes.launch { standIns(choice, onStoodIn)?.let(onOpenMachines) }
        }
    }

    /** Plans [machines], shown as [key], in today's visit, then hands on today. */
    fun planToday(
        key: String,
        machines: List<PlacedMachine>,
        onStoodIn: () -> Unit = {},
        onOpen: (CalendarDay) -> Unit,
    ) {
        val choice = Choosing(key, owner(), null, machines)
        writes.launch {
            val own = standIns(choice, onStoodIn) ?: return@launch
            placement.planToday(choice.owner, own)?.let(onOpen)
        }
    }

    fun dismissNotice() {
        mutableState.value = mutableState.value.copy(notice = null)
    }

    /** The account's machines for the shown ones; null, with a notice when one is due. */
    private suspend fun standIns(
        choice: Choosing,
        onStoodIn: () -> Unit,
    ): List<MachineId>? =
        when (val found = catalogue.standIns(choice.owner, choice.machines)) {
            is StandIn.Found -> {
                found.machines
                    .map { it.id }
                    .also { if (it != choice.machines.map { shown -> shown.id }) onStoodIn() }
            }

            StandIn.Offline -> null.also { notice(choice.key, AppStrings.current.offline) }
            StandIn.Gone -> null.also { notice(choice.key, AppStrings.current.machineGone) }
            StandIn.Switched -> null
        }

    private fun notice(
        key: String,
        text: String,
    ) {
        mutableState.value = mutableState.value.copy(notice = PlacementNoticeUi(key, text))
    }
}
