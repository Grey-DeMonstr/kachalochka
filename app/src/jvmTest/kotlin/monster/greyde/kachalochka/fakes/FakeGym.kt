package monster.greyde.kachalochka.fakes

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.identity.GoogleSignIn
import monster.greyde.kachalochka.core.data.identity.InMemoryAccountStorage
import monster.greyde.kachalochka.core.data.identity.OwnerlessRows
import monster.greyde.kachalochka.core.data.identity.PersistedAccountStore
import monster.greyde.kachalochka.core.data.identity.SessionActivation
import monster.greyde.kachalochka.core.data.supabase.SupabaseCredentials
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

    override suspend fun all(owner: UserId?): List<Machine> =
        rows.values
            .filterNot { it.deleted }
            .filter { it.userId == owner }
            .sortedBy { it.name.lowercase() }

    override suspend fun named(
        owner: UserId?,
        name: String,
    ): Machine? = all(owner).firstOrNull { it.name == name }
}

class InMemoryVisitRepository : VisitRepository {
    val rows = linkedMapOf<VisitId, Visit>()

    override suspend fun upsert(visit: Visit) {
        rows[visit.id] = visit
    }

    override suspend fun byId(id: VisitId): Visit? = rows[id]

    override suspend fun active(owner: UserId?): Visit? =
        rows.values
            .filter { it.endedAt == null && !it.deleted && it.userId == owner }
            .maxByOrNull { it.recordedAt }
}

class InMemoryWorkoutSetRepository : WorkoutSetRepository {
    val rows = linkedMapOf<WorkoutSetId, WorkoutSet>()

    /** While set, writes wait for it, which keeps a write in flight for as long as a test needs. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun upsert(set: WorkoutSet) {
        gate?.await()
        rows[set.id] = set
    }

    private fun live() = rows.values.filterNot { it.deleted }.sortedBy { it.recordedAt }

    override suspend fun forVisit(visitId: VisitId) = live().filter { it.visitId == visitId }

    override suspend fun forMachine(machineId: MachineId) =
        live().filter { it.machineId == machineId }

    override suspend fun latestPerMachine(owner: UserId?) =
        live()
            .filter { it.userId == owner }
            .groupBy { it.machineId }
            .values
            .map { it.last() }
}

private class QueuedGoogleSignIn : GoogleSignIn {
    val queue = ArrayDeque<AccountSession>()

    override suspend fun signIn(): AccountSession = queue.removeFirst()
}

private class NoOpSessionActivation : SessionActivation {
    override suspend fun activate(session: AccountSession) = Unit

    override suspend fun clear() = Unit
}

private class NoOpOwnerlessRows : OwnerlessRows {
    override suspend fun claim(owner: UserId) = Unit
}

class FakeGym(
    now: Instant = Instant.fromEpochSeconds(1_700_000_000),
    val credentials: SupabaseCredentials =
        SupabaseCredentials("https://example.test", "anon-key", "google-client-id"),
) {
    val clock = MutableClock(now)
    val ticker = ManualTicker()
    val machines = InMemoryMachineRepository()
    val visits = InMemoryVisitRepository()
    val sets = InMemoryWorkoutSetRepository()
    private val signIn = QueuedGoogleSignIn()
    val accounts =
        Accounts(
            PersistedAccountStore(InMemoryAccountStorage()),
            signIn,
            NoOpSessionActivation(),
            NoOpOwnerlessRows(),
        )
    val currentUser =
        object : CurrentUser {
            override suspend fun id(): UserId? = accounts.activeId.value
        }
    val utcOffset = UtcOffset { Duration.ZERO }

    /** Signs [sessions] in through [accounts] in order, then makes [active] the live one. */
    fun withAccounts(
        vararg sessions: AccountSession,
        active: AccountSession,
    ): FakeGym =
        runBlocking {
            sessions.forEach {
                signIn.queue.addLast(it)
                accounts.addAccount()
            }
            accounts.switchTo(active.account.userId)
            this@FakeGym
        }
}
