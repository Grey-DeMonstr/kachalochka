package monster.greyde.kachalochka.core.data.identity

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.db.inMemoryDatabase
import monster.greyde.kachalochka.core.data.gym.LocalMachineRepository
import monster.greyde.kachalochka.core.data.gym.LocalVisitRepository
import monster.greyde.kachalochka.core.data.gym.LocalWorkoutSetRepository
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class SqlOwnerlessRowsTest {
    private val database = inMemoryDatabase()
    private val outbox = OutboxDao(database)
    private val visits = LocalVisitRepository(database, outbox, Dispatchers.Unconfined)
    private val machines = LocalMachineRepository(database, outbox, Dispatchers.Unconfined)
    private val sets = LocalWorkoutSetRepository(database, outbox, Dispatchers.Unconfined)

    private val owner = UserId("11111111-1111-4111-8111-111111111111")
    private val stranger = UserId("22222222-2222-4222-8222-222222222222")
    private val t0 = Instant.fromEpochSeconds(1_700_000_000)

    // kotlin.time.Clock is a plain interface, so a fixed one is an object, not a lambda.
    private val clock =
        object : Clock {
            override fun now(): Instant = t0 + 1.hours
        }

    private val rows = SqlOwnerlessRows(database, outbox, clock, Dispatchers.Unconfined)

    private fun visit(userId: UserId?) = Visit(VisitId.random(), userId, t0, null, t0, false)

    @Test
    fun claiming_stamps_unowned_rows_and_leaves_other_owners_alone() =
        runTest {
            val unowned = visit(null)
            val theirs = visit(stranger)
            visits.upsert(unowned)
            visits.upsert(theirs)

            rows.claim(owner)

            assertEquals(owner, visits.byId(unowned.id)?.userId)
            assertEquals(stranger, visits.byId(theirs.id)?.userId)
        }

    @Test
    fun a_claimed_row_is_enqueued_for_the_next_sync_pass() =
        runTest {
            val unowned = visit(null)
            visits.upsert(unowned)

            rows.claim(owner)

            assertTrue(outbox.pending().any { it.rowId == unowned.id.value })
        }

    @Test
    fun claiming_covers_machines_and_sets_as_well_as_visits() =
        runTest {
            val unowned = visit(null)
            val press = Machine.new("Жим ногами", null, t0)
            val recorded =
                WorkoutSet(
                    WorkoutSetId.random(),
                    null,
                    unowned.id,
                    press.id,
                    70.0,
                    10,
                    t0,
                    t0,
                    false,
                )
            visits.upsert(unowned)
            machines.upsert(press)
            sets.upsert(recorded)

            rows.claim(owner)

            assertEquals(owner, machines.byId(press.id)?.userId)
            assertEquals(owner, sets.forVisit(unowned.id).single().userId)
        }
}
