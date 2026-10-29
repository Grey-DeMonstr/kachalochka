package monster.greyde.kachalochka.core.data.gym

import monster.greyde.kachalochka.core.data.catching
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.dayAt
import monster.greyde.kachalochka.core.domain.gym.withPriorBests
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** Applies [withPriorBests] to an owner's machines through the repositories (tech spec §4.5). */
class PriorBestsKeeper(
    private val visits: VisitRepository,
    private val sets: WorkoutSetRepository,
    private val machines: MachineRepository,
    private val clock: Clock,
    private val utcOffset: (Instant) -> Duration,
    private val failed: (Throwable) -> Unit,
) {
    /** True when it wrote anything; a failure is reported to [failed] and answers false. */
    suspend fun refresh(
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

    /** [refresh] over every machine the owner has a live set on. */
    suspend fun refreshAll(owner: UserId?): Boolean {
        val machineIds =
            catching { sets.all(owner).map { it.machineId }.toSet() }.getOrElse {
                failed(it)
                return false
            }
        return refresh(owner, machineIds)
    }
}
