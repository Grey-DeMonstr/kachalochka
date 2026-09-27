package monster.greyde.kachalochka.core.domain.gym

import monster.greyde.kachalochka.core.domain.identity.UserId

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

    /** The owner's live visit on [day]; of several, the one normalization keeps. */
    suspend fun onDay(
        owner: UserId?,
        day: CalendarDay,
    ): Visit?

    /**
     * The owner's visits that are not deleted, newest first, including those normalization has
     * not given a day yet.
     */
    suspend fun all(owner: UserId?): List<Visit>
}

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
