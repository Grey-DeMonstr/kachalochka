package monster.greyde.kachalochka.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.PlanId
import monster.greyde.kachalochka.core.domain.gym.PlanRepository
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.shownOn
import monster.greyde.kachalochka.core.domain.gym.visitMachines
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.strings.AppStrings
import kotlin.time.Clock

/** [day] is the visit's the screen showed, so another day's visit shows nothing. */
data class VisitPlacementNoticeUi(
    val day: CalendarDay,
    val text: String,
)

data class VisitPlacementUi(
    /** The plans offered while "В план…" asks which; null while it does not ask. */
    val choices: List<PlanChoiceUi>? = null,
    val notice: VisitPlacementNoticeUi? = null,
)

/**
 * Puts the machines of a day's visit into a plan, as a machine screen puts its one machine. Every
 * write checks first that the account it is for is still the active one (spec §4.3).
 */
class VisitPlacementViewModel(
    private val plans: PlanRepository,
    private val visits: VisitRepository,
    private val machines: MachineRepository,
    private val sets: WorkoutSetRepository,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val clock: Clock,
    private val utcOffset: UtcOffset,
) : ViewModel() {
    private val mutableState = MutableStateFlow(VisitPlacementUi())
    val state: StateFlow<VisitPlacementUi> = mutableState
    private val writes = WriteGuard(viewModelScope)

    /** The day of the visit the open choice is for, and the account it was offered to. */
    private var choosing: Pair<CalendarDay, UserId?>? = null

    init {
        viewModelScope.launch {
            accounts.activeId.collect {
                choosing = null
                mutableState.value = VisitPlacementUi()
            }
        }
    }

    fun choosePlan(day: CalendarDay) {
        viewModelScope.launch {
            val owner = currentUser.id()
            val choices = planChoices(plans, machines, owner)
            if (!stillActive(owner)) return@launch
            choosing = day to owner
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
        val (day, owner) = choosing ?: return
        cancelChoice()
        writes.launch {
            val shown = visits.shownOn(owner, day, sets, utcOffset::at) ?: return@launch
            val stored = plans.byId(plan)?.takeIf { it.userId == owner && !it.deleted }
            if (stored == null || !stillActive(owner)) return@launch
            val added = visitMachines(shown.sets, shown.visit.planned) - stored.machineIds.toSet()
            if (added.isNotEmpty()) {
                plans.upsert(
                    stored.copy(machineIds = stored.machineIds + added, updatedAt = clock.now()),
                )
            }
            mutableState.value =
                mutableState.value.copy(
                    notice = VisitPlacementNoticeUi(day, AppStrings.current.addedTo(title)),
                )
        }
    }

    fun newPlan(onOpen: (CalendarDay) -> Unit) {
        val (day, _) = choosing ?: return
        cancelChoice()
        onOpen(day)
    }

    fun dismissNotice() {
        mutableState.value = mutableState.value.copy(notice = null)
    }

    private suspend fun stillActive(owner: UserId?) = currentUser.id() == owner
}
