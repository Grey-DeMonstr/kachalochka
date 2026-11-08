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
import monster.greyde.kachalochka.core.domain.gym.VisitRows
import monster.greyde.kachalochka.core.domain.gym.WeightMode
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

class GymWritesTest {
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
    private val writes = GymWrites(visits, sets, machines, clock, { Duration.ZERO }, failures::add)
    private val press = Machine.new("Жим ногами", ivan, t0)
    private val row = Machine.new("Тяга", ivan, t0)
    private val first = visit(CalendarDay(2023, 11, 1))
    private val second = visit(CalendarDay(2023, 11, 2))
    private val third = visit(CalendarDay(2023, 11, 3))

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
    fun a_written_set_and_the_other_sets_of_its_machine_get_their_prior_bests() =
        runTest {
            pulled.writeMachine(press)
            pulled.writeVisit(first)
            pulled.writeVisit(second)
            pulled.writeSet(set(first, press, 70.0))

            writes.write(listOf(set(second, press, 75.0)))

            assertEquals(DayBest.NONE, sets.forVisit(first.id).single().priorBest)
            assertEquals(DayBest(70.0, 10, 1), sets.forVisit(second.id).single().priorBest)
            assertEquals(later, sets.forVisit(first.id).single().updatedAt)
        }

    @Test
    fun a_visit_moved_before_another_day_changes_the_prior_bests_of_both() =
        runTest {
            pulledPressHistory()
            writes.refreshAll(ivan)
            val earlier = CalendarDay(2023, 10, 31)
            val moved =
                second.copy(day = earlier, recordedAt = earlier.at(0L, Duration.ZERO))

            writes.write(VisitRows(moved, sets.forVisit(second.id)))

            assertEquals(earlier, visits.byId(second.id)?.day)
            assertEquals(DayBest(75.0, 10, 1), sets.forVisit(first.id).single().priorBest)
            assertEquals(DayBest.NONE, sets.forVisit(second.id).single().priorBest)
        }

    @Test
    fun a_new_weight_mode_of_a_machine_orders_its_results_again() =
        runTest {
            pulledPressHistory()
            pulled.writeVisit(third)
            pulled.writeSet(set(third, press, 60.0))
            writes.refreshAll(ivan)

            writes.write(press.copy(weightMode = WeightMode.Counterweight), emptyList())

            assertEquals(WeightMode.Counterweight, machines.byId(press.id)?.weightMode)
            assertEquals(DayBest(70.0, 10, 1), sets.forVisit(third.id).single().priorBest)
        }

    @Test
    fun the_rewritten_sets_of_a_machine_get_their_prior_bests() =
        runTest {
            pulledPressHistory()
            writes.refreshAll(ivan)
            val halved = sets.forMachine(press.id).map { it.copy(weight = it.weight / 2) }

            writes.write(press.copy(weightMode = WeightMode.PerSide), halved)

            assertEquals(37.5, sets.forVisit(second.id).single().weight)
            assertEquals(DayBest(35.0, 10, 1), sets.forVisit(second.id).single().priorBest)
        }

    @Test
    fun refresh_all_gives_pulled_rows_their_prior_bests_and_enqueues_them() =
        runTest {
            pulledPressHistory()
            pulled.writeMachine(row)
            pulled.writeSet(set(second, row, 40.0))

            assertTrue(writes.refreshAll(ivan))

            assertEquals(DayBest.NONE, sets.forVisit(first.id).single().priorBest)
            assertEquals(
                setOf(DayBest(70.0, 10, 1), DayBest.NONE),
                sets.forVisit(second.id).map { it.priorBest }.toSet(),
            )
            assertEquals(3, outbox.pending().size)
        }

    @Test
    fun a_second_refresh_all_writes_nothing() =
        runTest {
            pulledPressHistory()
            writes.refreshAll(ivan)

            assertFalse(writes.refreshAll(ivan))

            assertEquals(2, outbox.pending().size)
        }

    @Test
    fun a_machine_the_owner_no_longer_has_is_skipped() =
        runTest {
            pulled.writeVisit(first)
            pulled.writeSet(set(first, press, 70.0))

            assertFalse(writes.refreshAll(ivan))

            assertNull(sets.forVisit(first.id).single().priorBest)
            assertEquals(emptyList(), outbox.pending())
        }

    @Test
    fun a_failing_recompute_is_reported_and_the_write_stands() =
        runTest {
            pulledPressHistory()
            val broken =
                object : VisitRepository by visits {
                    override suspend fun all(owner: UserId?): List<Visit> = error("unreadable row")
                }
            val failing =
                GymWrites(broken, sets, machines, clock, { Duration.ZERO }, failures::add)
            val added = set(third, press, 80.0)

            failing.write(listOf(added))

            assertEquals(listOf("unreadable row"), failures.map { it.message })
            assertEquals(added, sets.forVisit(third.id).single())
            assertFalse(failing.refreshAll(ivan))
        }

    @Test
    fun another_owner_s_sets_on_the_same_machine_id_are_left_alone() =
        runTest {
            pulledPressHistory()
            val hers = visit(CalendarDay(2023, 11, 3), owner = misha)
            pulled.writeVisit(hers)
            pulled.writeSet(set(hers, press, 90.0))

            writes.refreshAll(ivan)

            assertNull(sets.forVisit(hers.id).single().priorBest)
            assertEquals(DayBest(70.0, 10, 1), sets.forVisit(second.id).single().priorBest)
        }
}
