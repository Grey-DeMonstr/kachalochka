package monster.greyde.kachalochka.ui.calendar

import monster.greyde.kachalochka.core.domain.friends.Friend
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.bestSet
import monster.greyde.kachalochka.core.domain.gym.groupByMachine
import monster.greyde.kachalochka.core.domain.gym.shownVisit
import monster.greyde.kachalochka.core.domain.gym.sortedTags
import monster.greyde.kachalochka.core.domain.gym.visitMachines
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.ui.format.setChange
import monster.greyde.kachalochka.ui.format.setValue

/** A machine on a visit card: the day's best set, and how it beats every earlier day's best. */
data class ExerciseUi(
    val name: String,
    val best: String,
    val growth: String?,
)

/** The own visit a card's menu acts on. */
data class OwnVisitUi(
    val id: VisitId,
    val canRepeatToday: Boolean,
)

/** One person's visit on one day, on the calendar and in the feed. */
data class VisitCardUi(
    val tag: String,
    val person: PersonUi,
    /** A friend's calendar colour; null on the own card. */
    val color: Int?,
    val day: CalendarDay,
    val tags: List<String>,
    /** Null while a friend's sets are read. */
    val exercises: List<ExerciseUi>?,
    val own: OwnVisitUi? = null,
)

internal fun cardTags(machines: List<Machine>): List<String> =
    sortedTags(machines.flatMap { it.tags })

/** The own visit [visitSets] belong to, with gains over the days before [day]. */
internal fun ownCard(
    visit: Visit,
    visitSets: List<WorkoutSet>,
    day: CalendarDay,
    today: CalendarDay,
    person: PersonUi,
    machines: Map<MachineId, Machine>,
    history: OwnHistory,
    preferred: PreferredWeightUnit,
): VisitCardUi {
    val shown = groupByMachine(visitSets).mapNotNull { machines[it.machineId] }
    return VisitCardUi(
        tag = "calendar-visit-${visit.id.value}",
        person = person,
        color = null,
        day = day,
        tags = cardTags(shown),
        exercises = exerciseRows(visitSets, machines, preferred) { history.before(it, day) },
        own =
            OwnVisitUi(
                visit.id,
                canRepeatToday =
                    day != today && visitMachines(visitSets, visit.planned).isNotEmpty(),
            ),
    )
}

/** A friend's visit on one day, once its sets are read. */
internal data class FriendDayDetails(
    val tags: List<String>,
    val exercises: List<ExerciseUi>,
)

/** [friend]'s visit of [sameDay] that [shownVisit] picks, on [theirs] machines. */
internal suspend fun FriendsRepository.dayDetails(
    sameDay: List<Visit>,
    theirs: Map<MachineId, Machine>,
    preferred: PreferredWeightUnit,
): FriendDayDetails {
    val daySets = shownVisit(sameDay, ::sets)?.sets.orEmpty()
    val shown = groupByMachine(daySets).mapNotNull { theirs[it.machineId] }
    return FriendDayDetails(cardTags(shown), exerciseRows(daySets, theirs, preferred))
}

internal fun friendCard(
    tag: String,
    friend: Friend,
    color: Int,
    day: CalendarDay,
    details: FriendDayDetails?,
) = VisitCardUi(
    tag = tag,
    person = PersonUi(friend.userId, friend.displayName, friend.avatar),
    color = color,
    day = day,
    tags = details?.tags.orEmpty(),
    exercises = details?.exercises,
)

/** [visitSets]' machines in visit order; [earlier] gives a machine's sets of the earlier days. */
internal fun exerciseRows(
    visitSets: List<WorkoutSet>,
    machines: Map<MachineId, Machine>,
    preferred: PreferredWeightUnit,
    earlier: (MachineId) -> List<WorkoutSet> = { emptyList() },
): List<ExerciseUi> =
    groupByMachine(visitSets).mapNotNull { (id, sets) ->
        val machine = machines[id] ?: return@mapNotNull null
        val best = bestSet(sets, machine.weightMode) ?: return@mapNotNull null
        val before = bestSet(earlier(id), machine.weightMode)
        ExerciseUi(
            machine.name,
            setValue(best.weight, best.reps, machine, preferred),
            before?.let { setChange(machine, it, best, preferred) }?.takeIf { it.gain }?.text,
        )
    }

/** The own sets of each machine, placed on their visits' days. */
internal class OwnHistory(
    sets: List<WorkoutSet>,
    private val visitDays: Map<VisitId, CalendarDay>,
) {
    private val byMachine = sets.groupBy { it.machineId }

    fun before(
        machine: MachineId,
        day: CalendarDay,
    ): List<WorkoutSet> =
        byMachine[machine].orEmpty().filter { set ->
            visitDays[set.visitId]?.let { it < day } == true
        }
}
