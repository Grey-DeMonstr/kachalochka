package monster.greyde.kachalochka.core.domain.gym

import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Duration
import kotlin.time.Instant

interface MachineRepository {
    suspend fun upsert(machine: Machine)

    suspend fun byId(id: MachineId): Machine?

    /** The owner's machines that are not deleted, by name. */
    suspend fun all(owner: UserId?): List<Machine>

    /** The owner's machine of that name, which is what mirroring looks for. */
    suspend fun named(
        owner: UserId?,
        name: String,
    ): Machine?
}

interface VisitRepository {
    suspend fun upsert(visit: Visit)

    suspend fun byId(id: VisitId): Visit?

    /** The owner's live visit on [day]; of several, the greatest by [visitRecency]. */
    suspend fun onDay(
        owner: UserId?,
        day: CalendarDay,
    ): Visit?

    /**
     * The owner's visits that are not deleted, newest first, including those normalization has
     * not given a day yet.
     */
    suspend fun all(owner: UserId?): List<Visit>

    /** The owner's live visits normalization has not given a day yet. */
    suspend fun undated(owner: UserId?): List<Visit>
}

/** Every live visit of the owner's that [shownOn] weighs for [day]. */
suspend fun VisitRepository.allOn(
    owner: UserId?,
    day: CalendarDay,
    utcOffset: (Instant) -> Duration,
): List<Visit> = all(owner).filter { it.dayAt(utcOffset) == day }

/** The owner's visit shown on [day], the [shownVisit] of [allOn]'s, with its sets. */
suspend fun VisitRepository.shownOn(
    owner: UserId?,
    day: CalendarDay,
    sets: WorkoutSetRepository,
    utcOffset: (Instant) -> Duration,
): ShownVisit? =
    shownVisit(allOn(owner, day, utcOffset)) { sets.forVisit(it.id) }
        ?.let { it.copy(visit = it.visit.copy(day = day)) }

/**
 * Every list leaves deleted sets out; a visit's sets run in [visitOrder], the others in recording
 * order.
 */
interface WorkoutSetRepository {
    suspend fun upsert(set: WorkoutSet)

    suspend fun forVisit(visitId: VisitId): List<WorkoutSet>

    suspend fun forMachine(machineId: MachineId): List<WorkoutSet>

    /** The most recently recorded set of each of the owner's machines. */
    suspend fun latestPerMachine(owner: UserId?): List<WorkoutSet>

    /** How far the live sets on each of the owner's machines reach. */
    suspend fun peaks(owner: UserId?): List<MachinePeaks>
}

interface PlanRepository {
    suspend fun upsert(plan: Plan)

    suspend fun byId(id: PlanId): Plan?

    /** The owner's live plans in [planOrder]. */
    suspend fun all(owner: UserId?): List<Plan>
}
