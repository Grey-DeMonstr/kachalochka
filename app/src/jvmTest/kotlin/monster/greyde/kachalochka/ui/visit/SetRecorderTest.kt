package monster.greyde.kachalochka.ui.visit

import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.SetValues
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.timer.RestTimer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SetRecorderTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val today = gym.today
    private val seventh = CalendarDay(2023, 11, 7)
    private val timer = RestTimer(gym.clock)
    private val press = Machine.new("Жим ногами", null, t0)
    private val row = Machine.new("Тяга", null, t0)
    private val ivan = UserId("11111111-1111-4111-8111-111111111111")
    private val misha = UserId("22222222-2222-4222-8222-222222222222")
    private val lastWeek = Visit(VisitId.random(), null, seventh, t0 - 7.days, t0, false)
    private val lastWeekSet =
        WorkoutSet(
            WorkoutSetId.random(),
            null,
            lastWeek.id,
            press.id,
            60.0,
            10,
            1,
            t0 - 7.days + 10.minutes,
            t0,
            false,
        )

    private fun recorder(day: CalendarDay = today) =
        SetRecorder(
            day,
            gym.visits,
            gym.machines,
            gym.sets,
            gym.clock,
            gym.utcOffset,
            timer,
            gym.sync,
        )

    @Test
    fun the_day_s_first_set_creates_its_visit_and_starts_the_rest_timer() =
        runTest {
            gym.machines.upsert(press)

            recorder().record(null, press, SetValues(60.0, 10), "")

            val visit =
                gym.visits.rows.values
                    .single()
            assertEquals(today, visit.day)
            assertNull(visit.userId)
            val set =
                gym.sets.rows.values
                    .single()
            assertEquals(visit.id, set.visitId)
            assertEquals(press.id, set.machineId)
            assertEquals(1, set.position)
            assertEquals(t0, set.recordedAt)
            assertEquals(t0, timer.startedAt.value)
            assertEquals(0, gym.sync.requests)
        }

    @Test
    fun a_set_on_a_past_day_follows_the_visit_s_last_set_and_is_pushed_at_once() =
        runTest {
            gym.machines.upsert(press)
            gym.visits.upsert(lastWeek)
            gym.sets.upsert(lastWeekSet)

            recorder(seventh).record(null, press, SetValues(70.0, 8), "тяжело")

            val added =
                gym.sets.rows.values
                    .single { it.id != lastWeekSet.id }
            assertEquals(lastWeek.id, added.visitId)
            assertEquals(lastWeekSet.recordedAt + 1.seconds, added.recordedAt)
            assertEquals(2, added.position)
            assertEquals("тяжело", added.comment)
            assertNull(timer.startedAt.value)
            assertEquals(1, gym.sync.requests)
        }

    @Test
    fun a_set_on_another_account_s_machine_lands_on_the_owner_s_copy_of_it() =
        runTest {
            val mishasPress = press.copy(userId = misha)
            gym.machines.upsert(mishasPress)

            recorder().record(ivan, mishasPress, SetValues(60.0, 10), "")
            recorder().record(ivan, mishasPress, SetValues(65.0, 10), "")

            val copy =
                gym.machines.rows.values
                    .single { it.userId == ivan }
            assertEquals(mishasPress.name, copy.name)
            assertEquals(2, gym.machines.rows.size)
            assertEquals(
                setOf(copy.id),
                gym.sets.rows.values
                    .map { it.machineId }
                    .toSet(),
            )
            assertTrue(
                gym.sets.rows.values
                    .all { it.userId == ivan },
            )
        }

    @Test
    fun amending_a_set_changes_its_values_and_keeps_its_place() =
        runTest {
            gym.machines.upsert(press)
            gym.visits.upsert(lastWeek)
            gym.sets.upsert(lastWeekSet)

            recorder(seventh).amend(lastWeekSet, SetValues(65.0, 12), "легче")

            val amended = gym.sets.rows.getValue(lastWeekSet.id)
            assertEquals(65.0, amended.weight)
            assertEquals(12, amended.reps)
            assertEquals("легче", amended.comment)
            assertEquals(lastWeekSet.recordedAt, amended.recordedAt)
            assertEquals(lastWeekSet.position, amended.position)
            assertEquals(t0, amended.updatedAt)
            assertEquals(1, gym.sync.requests)
        }

    @Test
    fun removing_a_set_soft_deletes_it() =
        runTest {
            gym.machines.upsert(press)
            gym.visits.upsert(lastWeek)
            gym.sets.upsert(lastWeekSet)

            recorder(seventh).remove(lastWeekSet)

            assertTrue(
                gym.sets.rows
                    .getValue(lastWeekSet.id)
                    .deleted,
            )
            assertEquals(1, gym.sync.requests)
        }

    @Test
    fun a_reorder_writes_the_rows_handed_to_it_and_today_s_waits_for_the_sync_worker() =
        runTest {
            gym.machines.upsert(press)
            val todayVisit = Visit(VisitId.random(), null, today, t0, t0, false)
            gym.visits.upsert(todayVisit)
            val set = lastWeekSet.copy(visitId = todayVisit.id, recordedAt = t0)
            gym.sets.upsert(set)

            recorder().reorder(listOf(set.copy(position = 3)))

            assertEquals(
                3,
                gym.sets.rows
                    .getValue(set.id)
                    .position,
            )
            assertEquals(0, gym.sync.requests)
        }

    @Test
    fun a_plan_started_on_a_day_without_a_visit_creates_it_with_the_plan_s_machines() =
        runTest {
            gym.machines.upsert(press)
            gym.machines.upsert(row)

            recorder().plan(null, listOf(row.id, press.id))

            val visit =
                gym.visits.rows.values
                    .single()
            assertEquals(today, visit.day)
            assertEquals(listOf(row.id, press.id), visit.planned)
            assertEquals(0, gym.sync.requests)
        }

    @Test
    fun a_plan_started_on_a_visit_adds_only_what_it_lacks() =
        runTest {
            gym.machines.upsert(press)
            gym.machines.upsert(row)
            val gone = Machine.new("Гакк", null, t0).copy(deleted = true)
            gym.machines.upsert(gone)
            recorder().record(null, press, SetValues(60.0, 10), "")

            recorder().plan(null, listOf(press.id, row.id, gone.id))
            recorder().plan(null, listOf(row.id))

            val visit =
                gym.visits.rows.values
                    .single()
            assertEquals(listOf(row.id), visit.planned)
        }

    @Test
    fun a_set_recorded_into_a_planned_visit_keeps_the_plan() =
        runTest {
            gym.machines.upsert(press)
            gym.machines.upsert(row)
            recorder().plan(null, listOf(row.id, press.id))

            recorder().record(null, press, SetValues(60.0, 10), "")

            val visit =
                gym.visits.rows.values
                    .single()
            assertEquals(listOf(row.id, press.id), visit.planned)
            assertEquals(
                visit.id,
                gym.sets.rows.values
                    .single()
                    .visitId,
            )
        }

    @Test
    fun a_planned_machine_taken_out_of_a_past_visit_is_pushed_at_once() =
        runTest {
            gym.machines.upsert(press)
            gym.visits.upsert(lastWeek.copy(planned = listOf(press.id)))

            recorder(seventh).unplan(lastWeek.id, press.id)

            assertEquals(
                emptyList(),
                gym.visits.rows
                    .getValue(lastWeek.id)
                    .planned,
            )
            assertEquals(1, gym.sync.requests)
        }
}
