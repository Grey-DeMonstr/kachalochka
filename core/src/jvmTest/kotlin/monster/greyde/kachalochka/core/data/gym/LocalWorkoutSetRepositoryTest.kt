package monster.greyde.kachalochka.core.data.gym

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.db.inMemoryDatabase
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class LocalWorkoutSetRepositoryTest {
    private val database = inMemoryDatabase()
    private val repository =
        LocalWorkoutSetRepository(database, OutboxDao(database), Dispatchers.Unconfined)
    private val t0 = Instant.fromEpochMilliseconds(1_700_000_000_123)
    private val visitA = VisitId.random()
    private val visitB = VisitId.random()
    private val press = MachineId.random()
    private val row = MachineId.random()

    private fun set(
        visit: VisitId,
        machine: MachineId,
        minute: Int,
        weight: Double = 70.0,
        deleted: Boolean = false,
    ) = WorkoutSet(
        WorkoutSetId.random(),
        null,
        visit,
        machine,
        weight,
        10,
        t0 + minute.minutes,
        t0,
        deleted,
    )

    @Test
    fun a_set_reads_back_through_its_visit() =
        runTest {
            val set = set(visitA, press, 0, weight = 72.5)

            repository.upsert(set)

            assertEquals(listOf(set), repository.forVisit(visitA))
        }

    @Test
    fun lists_run_in_recording_order_and_leave_out_deleted_sets() =
        runTest {
            val late = set(visitA, press, 5)
            val early = set(visitA, press, 1)
            val deleted = set(visitA, press, 3, deleted = true)
            val elsewhere = set(visitB, press, 9)
            listOf(late, early, deleted, elsewhere).forEach { repository.upsert(it) }

            assertEquals(listOf(early, late), repository.forVisit(visitA))
            assertEquals(listOf(early, late, elsewhere), repository.forMachine(press))
        }

    @Test
    fun latest_per_machine_takes_the_newest_live_set_of_each() =
        runTest {
            val pressOld = set(visitA, press, 1)
            val pressNew = set(visitB, press, 50)
            val pressDeleted = set(visitB, press, 60, deleted = true)
            val rowOnly = set(visitA, row, 2)
            listOf(pressOld, pressNew, pressDeleted, rowOnly).forEach { repository.upsert(it) }

            assertEquals(
                setOf(pressNew, rowOnly),
                repository.latestPerMachine().toSet(),
            )
        }
}
