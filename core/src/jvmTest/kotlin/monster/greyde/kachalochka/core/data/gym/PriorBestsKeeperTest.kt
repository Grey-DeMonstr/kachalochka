package monster.greyde.kachalochka.core.data.gym

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.db.inMemoryDatabase
import monster.greyde.kachalochka.core.data.sync.LocalSyncRows
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.DayBest
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class PriorBestsKeeperTest {
    private val database = inMemoryDatabase()
    private val outbox = OutboxDao(database)
    private val visits = LocalVisitRepository(database, outbox, Dispatchers.Unconfined)
    private val sets = LocalWorkoutSetRepository(database, outbox, Dispatchers.Unconfined)
    private val machines = LocalMachineRepository(database, outbox, Dispatchers.Unconfined)
    private val pulled = LocalSyncRows(database)
    private val ivan = UserId("11111111-1111-4111-8111-111111111111")
    private val misha = UserId("22222222-2222-4222-8222-222222222222")
    private val t0 = Instant.fromEpochSeconds(1_700_000_000)
    private val later = t0 + 1.hours
    private val clock =
        object : Clock {
            override fun now(): Instant = later
        }
    private val failures = mutableListOf<Throwable>()
    private val keeper =
        PriorBestsKeeper(visits, sets, machines, clock, { Duration.ZERO }, failures::add)
    private val press = Machine.new("Жим ногами", ivan, t0)
    private val row = Machine.new("Тяга", ivan, t0)
    private val first = visit(CalendarDay(2023, 11, 1))
    private val second = visit(CalendarDay(2023, 11, 2))

    private fun visit(
        day: CalendarDay,
        owner: UserId = ivan,
    ) = Visit(VisitId.random(), owner, day, day.at(0L, Duration.ZERO), t0, false)

    private fun set(
        visit: Visit,
        machine: Machine,
        weight: Double,
    ) = WorkoutSet(
        WorkoutSetId.random(),
        visit.userId,
        visit.id,
        machine.id,
        weight,
        10,
        0,
        visit.recordedAt,
        t0,
        false,
    )

    private fun pulledPressHistory() {
        pulled.writeMachine(press)
        pulled.writeVisit(first)
        pulled.writeVisit(second)
        pulled.writeSet(set(first, press, 70.0))
        pulled.writeSet(set(second, press, 75.0))
    }

    @Test
    fun a_machine_s_sets_get_their_prior_bests_and_the_owned_rows_are_enqueued() =
        runTest {
            pulledPressHistory()

            assertTrue(keeper.refresh(ivan, setOf(press.id)))

            assertEquals(DayBest.NONE, sets.forVisit(first.id).single().priorBest)
            assertEquals(DayBest(70.0, 10, 1), sets.forVisit(second.id).single().priorBest)
            assertEquals(later, sets.forVisit(second.id).single().updatedAt)
            assertEquals(2, outbox.pending().size)
        }

    @Test
    fun a_second_refresh_writes_nothing() =
        runTest {
            pulledPressHistory()
            keeper.refresh(ivan, setOf(press.id))

            assertFalse(keeper.refresh(ivan, setOf(press.id)))

            assertEquals(2, outbox.pending().size)
        }

    @Test
    fun refresh_all_covers_every_machine_with_a_live_set() =
        runTest {
            pulledPressHistory()
            pulled.writeMachine(row)
            pulled.writeSet(set(second, row, 40.0))

            assertTrue(keeper.refreshAll(ivan))

            assertEquals(
                setOf(DayBest(70.0, 10, 1), DayBest.NONE),
                sets.forVisit(second.id).map { it.priorBest }.toSet(),
            )
        }

    @Test
    fun a_machine_the_owner_no_longer_has_is_skipped() =
        runTest {
            pulled.writeVisit(first)
            pulled.writeSet(set(first, press, 70.0))

            assertFalse(keeper.refresh(ivan, setOf(press.id)))

            assertNull(sets.forVisit(first.id).single().priorBest)
            assertEquals(emptyList(), outbox.pending())
        }

    @Test
    fun a_failing_read_is_reported_and_answers_false() =
        runTest {
            pulledPressHistory()
            val broken =
                object : VisitRepository by visits {
                    override suspend fun all(owner: UserId?): List<Visit> = error("unreadable row")
                }
            val failing =
                PriorBestsKeeper(broken, sets, machines, clock, { Duration.ZERO }, failures::add)

            assertFalse(failing.refresh(ivan, setOf(press.id)))

            assertEquals(listOf("unreadable row"), failures.map { it.message })
            assertNull(sets.forVisit(first.id).single().priorBest)
        }

    @Test
    fun another_owner_s_sets_on_the_same_machine_id_are_left_alone() =
        runTest {
            pulledPressHistory()
            val hers = visit(CalendarDay(2023, 11, 3), owner = misha)
            pulled.writeVisit(hers)
            pulled.writeSet(set(hers, press, 90.0))

            keeper.refresh(ivan, setOf(press.id))

            assertNull(sets.forVisit(hers.id).single().priorBest)
            assertEquals(DayBest(70.0, 10, 1), sets.forVisit(second.id).single().priorBest)
        }
}
