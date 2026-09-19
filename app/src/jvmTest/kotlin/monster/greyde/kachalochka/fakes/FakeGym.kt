package monster.greyde.kachalochka.fakes

import kotlinx.coroutines.channels.Channel
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.timer.Ticker
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

class MutableClock(
    var current: Instant,
) : Clock {
    override fun now(): Instant = current
}

/** Ticks only when a test calls [tick], so screens never animate on their own. */
class ManualTicker : Ticker {
    private val ticks = Channel<Unit>(Channel.UNLIMITED)

    fun tick() {
        ticks.trySend(Unit)
    }

    override suspend fun awaitTick() = ticks.receive()
}

class InMemoryMachineRepository : MachineRepository {
    val rows = linkedMapOf<MachineId, Machine>()

    override suspend fun upsert(machine: Machine) {
        rows[machine.id] = machine
    }

    override suspend fun byId(id: MachineId): Machine? = rows[id]

    override suspend fun all(): List<Machine> =
        rows.values.filterNot { it.deleted }.sortedBy { it.name.lowercase() }
}

class InMemoryVisitRepository : VisitRepository {
    val rows = linkedMapOf<VisitId, Visit>()

    override suspend fun upsert(visit: Visit) {
        rows[visit.id] = visit
    }

    override suspend fun byId(id: VisitId): Visit? = rows[id]

    override suspend fun active(): Visit? =
        rows.values.filter { it.endedAt == null && !it.deleted }.maxByOrNull { it.startedAt }
}

class InMemoryWorkoutSetRepository : WorkoutSetRepository {
    val rows = linkedMapOf<WorkoutSetId, WorkoutSet>()

    override suspend fun upsert(set: WorkoutSet) {
        rows[set.id] = set
    }

    private fun live() = rows.values.filterNot { it.deleted }.sortedBy { it.recordedAt }

    override suspend fun forVisit(visitId: VisitId) = live().filter { it.visitId == visitId }

    override suspend fun forMachine(machineId: MachineId) =
        live().filter { it.machineId == machineId }

    override suspend fun latestPerMachine() =
        live().groupBy { it.machineId }.values.map { it.last() }
}

class FakeGym(
    now: Instant = Instant.fromEpochSeconds(1_700_000_000),
) {
    val clock = MutableClock(now)
    val ticker = ManualTicker()
    val machines = InMemoryMachineRepository()
    val visits = InMemoryVisitRepository()
    val sets = InMemoryWorkoutSetRepository()
    val currentUser =
        object : CurrentUser {
            override suspend fun id(): UserId? = null
        }
    val utcOffset = UtcOffset { Duration.ZERO }
}
