package monster.greyde.kachalochka.core.data.gym

import monster.greyde.kachalochka.core.data.catching
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.VisitRows
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.dayAt
import monster.greyde.kachalochka.core.domain.gym.withPriorBests
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The one writer of workout sets. Each write then applies [withPriorBests] to the machines it
 * touched (tech spec §4.5). A failed recompute goes to [failed], and the write stands.
 */
class GymWrites(
    private val visits: VisitRepository,
    private val sets: WorkoutSetRepository,
    private val machines: MachineRepository,
    private val clock: Clock,
    private val utcOffset: (Instant) -> Duration,
    private val failed: (Throwable) -> Unit,
) {
    suspend fun write(rows: List<WorkoutSet>) {
        rows.forEach { sets.upsert(it) }
        refresh(rows)
    }

    /** The visit's day orders its sets among the machine's days. */
    suspend fun write(rows: VisitRows) {
        rows.sets.forEach { sets.upsert(it) }
        visits.upsert(rows.visit)
        refresh(rows.sets)
    }

    /** [rewritten] are [machine]'s sets; its weight mode orders its results. */
    suspend fun write(
        machine: Machine,
        rewritten: List<WorkoutSet>,
    ) {
        machines.upsert(machine)
        rewritten.forEach { sets.upsert(it) }
        refresh(machine.userId, setOf(machine.id))
    }

    /**
     * Recomputes every machine [owner] has a live set on, for rows a pull wrote. True when it
     * wrote anything; a failure answers false.
     */
    suspend fun refreshAll(owner: UserId?): Boolean {
        val machineIds =
            catching { sets.all(owner).map { it.machineId }.toSet() }.getOrElse {
                failed(it)
                return false
            }
        return refresh(owner, machineIds)
    }

    private suspend fun refresh(rows: List<WorkoutSet>) =
        rows
            .groupBy({ it.userId }, { it.machineId })
            .forEach { (owner, machineIds) -> refresh(owner, machineIds.toSet()) }

    private suspend fun refresh(
        owner: UserId?,
        machineIds: Set<MachineId>,
    ): Boolean =
        catching {
            val days = visits.all(owner).associate { it.id to it.dayAt(utcOffset) }
            val now = clock.now()
            var wrote = false
            machineIds.forEach { id ->
                val machine = machines.byId(id) ?: return@forEach
                val owned = sets.forMachine(id).filter { it.userId == owner }
                withPriorBests(owned, days::get, machine.weightMode, now).forEach {
                    sets.storePriorBest(it.id, checkNotNull(it.priorBest), now)
                    wrote = true
                }
            }
            wrote
        }.getOrElse {
            failed(it)
            false
        }
}
