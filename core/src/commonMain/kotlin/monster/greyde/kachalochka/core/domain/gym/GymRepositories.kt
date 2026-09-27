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

/**
 * The owner's visit shown on [day], carrying it: of the visits on it and those not yet given a
 * day that were recorded on it, the greatest by [visitRecency].
 */
suspend fun VisitRepository.shownOn(
    owner: UserId?,
    day: CalendarDay,
    utcOffset: (Instant) -> Duration,
): Visit? {
    val recordedOnDay = undated(owner).filter { it.dayAt(utcOffset) == day }
    return (listOfNotNull(onDay(owner, day)) + recordedOnDay)
        .maxWithOrNull(visitRecency)
        ?.copy(day = day)
}

/** Every live visit of the owner's that [shownOn] weighs for [day]. */
suspend fun VisitRepository.allOn(
    owner: UserId?,
    day: CalendarDay,
    utcOffset: (Instant) -> Duration,
): List<Visit> = all(owner).filter { it.dayAt(utcOffset) == day }

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
}
