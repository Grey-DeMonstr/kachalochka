package monster.greyde.kachalochka.ui.machine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.calendarDaysBetween
import monster.greyde.kachalochka.core.domain.gym.rankMachines
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.daysAgoLabel
import monster.greyde.kachalochka.ui.format.setCount
import monster.greyde.kachalochka.ui.format.setValue
import monster.greyde.kachalochka.ui.ownVisit
import kotlin.time.Clock
import kotlin.time.Instant

data class PickerUiState(
    val query: String = "",
    val createLabel: String? = null,
    val sectionLabel: String = "Недавние",
    val rows: List<PickerRowUi> = emptyList(),
)

data class PickerRowUi(
    val id: MachineId,
    val name: String,
    val detail: String?,
)

class MachinePickerViewModel(
    private val visitId: VisitId,
    private val machines: MachineRepository,
    private val sets: WorkoutSetRepository,
    private val visits: VisitRepository,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val clock: Clock,
    private val utcOffset: UtcOffset,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PickerUiState())
    val state: StateFlow<PickerUiState> = mutableState

    private var all: List<Machine> = emptyList()
    private var latest: Map<MachineId, WorkoutSet> = emptyMap()
    private var today: Map<MachineId, Int> = emptyMap()

    /** The screen follows whoever is active, wherever the switch came from. */
    init {
        viewModelScope.launch { accounts.activeId.collect { load() } }
    }

    fun load() {
        viewModelScope.launch {
            val owner = currentUser.id()
            all = machines.all(owner)
            latest = sets.latestPerMachine(owner).associateBy { it.machineId }
            today =
                visits
                    .ownVisit(visitId, owner)
                    ?.let { sets.forVisit(it.id) }
                    .orEmpty()
                    .groupingBy { it.machineId }
                    .eachCount()
            publish(mutableState.value.query)
        }
    }

    fun onQueryChange(query: String) = publish(query)

    private fun publish(query: String) {
        val ranking = rankMachines(query, all, latest.mapValues { it.value.recordedAt })
        val now = clock.now()
        mutableState.value =
            PickerUiState(
                query = query,
                createLabel = if (ranking.offerCreate) "Создать «${query.trim()}»" else null,
                sectionLabel = if (query.isBlank()) "Недавние" else "Похожие",
                rows = ranking.machines.map { PickerRowUi(it.id, it.name, detail(it, now)) },
            )
    }

    private fun detail(
        machine: Machine,
        now: Instant,
    ): String? {
        today[machine.id]?.let { return "${setCount(it)} сегодня" }
        val last = latest[machine.id] ?: return null
        val days = calendarDaysBetween(last.recordedAt, now, utcOffset.at(now))
        return "Было ${setValue(last.weight, last.reps, machine.unit)} · ${daysAgoLabel(days)}"
    }
}
