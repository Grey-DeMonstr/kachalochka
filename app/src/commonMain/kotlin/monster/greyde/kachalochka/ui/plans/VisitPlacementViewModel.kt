package monster.greyde.kachalochka.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.PlanId
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.strings.AppStrings

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

/** Puts the machines of a day's visit into a plan, as a machine screen puts its one machine. */
class VisitPlacementViewModel(
    private val placement: Placement,
    private val accounts: Accounts,
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
        val owner = accounts.activeId.value
        viewModelScope.launch {
            val choices = placement.choices(owner) ?: return@launch
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
            if (!placement.addVisitToPlan(owner, plan, day)) return@launch
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
}
