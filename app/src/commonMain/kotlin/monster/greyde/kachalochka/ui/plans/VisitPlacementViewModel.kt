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

/** [visit] names the visit the screen showed, so the notice shows under that visit only. */
data class VisitPlacementNoticeUi(
    val visit: String,
    val text: String,
)

data class VisitPlacementUi(
    /** The plans offered while "Add to plan…" asks which; null while it does not ask. */
    val choices: List<PlanChoiceUi>? = null,
    val notice: VisitPlacementNoticeUi? = null,
)

/**
 * Puts the machines of a visit into a plan or into today's visit, as a machine screen puts its one
 * machine. A friend's visit goes in as the account's stand-ins for the friend's machines.
 */
class VisitPlacementViewModel(
    private val placement: Placement,
    private val accounts: Accounts,
    private val catalogue: MachineCatalogue,
) : ViewModel() {
    private val mutableState = MutableStateFlow(VisitPlacementUi())
    val state: StateFlow<VisitPlacementUi> = mutableState
    private val writes = WriteGuard(viewModelScope)

    /**
     * The visit an open choice is for, and the account it was offered to. An own visit is on
     * [day]; a friend's visit has [friendMachines] instead.
     */
    private class Choosing(
        val key: String,
        val owner: UserId?,
        val day: CalendarDay?,
        val friendMachines: List<MachineId>,
    )

    private var choosing: Choosing? = null

    init {
        viewModelScope.launch {
            accounts.activeId.collect {
                choosing = null
                mutableState.value = VisitPlacementUi()
            }
        }
    }

    /** Asks which plan takes the machines of the own visit on [day]. */
    fun choosePlan(day: CalendarDay) = choose(Choosing(day.iso, owner(), day, emptyList()))

    /** Asks which plan takes the friend's visit shown as [key], with [friendMachines]. */
    fun choosePlan(
        key: String,
        friendMachines: List<MachineId>,
    ) = choose(Choosing(key, owner(), null, friendMachines))

    private fun owner() = accounts.activeId.value

    private fun choose(next: Choosing) {
        viewModelScope.launch {
            val choices = placement.choices(next.owner) ?: return@launch
            choosing = next
            mutableState.value = VisitPlacementUi(choices = choices)
        }
    }

    fun cancelChoice() {
        choosing = null
        mutableState.value = mutableState.value.copy(choices = null)
    }

    /** Appends the visit's machines the plan lacks, in the visit's order. */
    fun addTo(plan: PlanId) {
        val title =
            mutableState.value.choices
                ?.firstOrNull { it.id == plan }
                ?.title ?: return
        val choice = choosing ?: return
        cancelChoice()
        writes.launch {
            val done =
                choice.day?.let { placement.addVisitToPlan(choice.owner, plan, it) }
                    ?: standIns(choice)?.let { placement.addToPlan(choice.owner, plan, it) }
            if (done != true) return@launch
            notice(choice.key, AppStrings.current.addedTo(title))
        }
    }

    /** [onOpen] gets the day of an own visit; [onOpenMachines] the stand-ins of a friend's. */
    fun newPlan(
        onOpenMachines: (List<MachineId>) -> Unit = {},
        onOpen: (CalendarDay) -> Unit,
    ) {
        val choice = choosing ?: return
        cancelChoice()
        val day = choice.day
        if (day != null) {
            onOpen(day)
        } else {
            writes.launch { standIns(choice)?.let(onOpenMachines) }
        }
    }

    /** Plans a friend's visit shown as [key] in today's visit, then hands on today. */
    fun repeatToday(
        key: String,
        friendMachines: List<MachineId>,
        onOpen: (CalendarDay) -> Unit,
    ) {
        val choice = Choosing(key, owner(), null, friendMachines)
        writes.launch {
            val own = standIns(choice) ?: return@launch
            placement.planToday(choice.owner, own)?.let(onOpen)
        }
    }

    fun dismissNotice() {
        mutableState.value = mutableState.value.copy(notice = null)
    }

    /** The account's machines for the friend's visit; null, with a notice when one is due. */
    private suspend fun standIns(choice: Choosing): List<MachineId>? {
        val shown = choice.friendMachines.map { PlacedMachine(it, ofFriend = true) }
        return when (val found = catalogue.standIns(choice.owner, shown)) {
            is StandIn.Found -> found.machines.map { it.id }

            StandIn.Offline -> null.also { notice(choice.key, AppStrings.current.offline) }
            StandIn.Gone -> null.also { notice(choice.key, AppStrings.current.machineGone) }
            StandIn.Switched -> null
        }
    }

    private fun notice(
        key: String,
        text: String,
    ) {
        mutableState.value = mutableState.value.copy(notice = VisitPlacementNoticeUi(key, text))
    }
}
