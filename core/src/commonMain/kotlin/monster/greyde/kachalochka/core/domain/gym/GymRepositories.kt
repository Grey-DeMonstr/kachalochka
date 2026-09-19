package monster.greyde.kachalochka.core.domain.gym

interface MachineRepository {
    suspend fun upsert(machine: Machine)

    suspend fun byId(id: MachineId): Machine?

    /** Machines that are not deleted, by name. */
    suspend fun all(): List<Machine>
}

interface VisitRepository {
    suspend fun upsert(visit: Visit)

    suspend fun byId(id: VisitId): Visit?

    /** The newest visit that has not ended and is not deleted. */
    suspend fun active(): Visit?
}

/** Every list leaves deleted sets out and runs in recording order. */
interface WorkoutSetRepository {
    suspend fun upsert(set: WorkoutSet)

    suspend fun forVisit(visitId: VisitId): List<WorkoutSet>

    suspend fun forMachine(machineId: MachineId): List<WorkoutSet>

    /** The most recently recorded set of each machine. */
    suspend fun latestPerMachine(): List<WorkoutSet>
}
