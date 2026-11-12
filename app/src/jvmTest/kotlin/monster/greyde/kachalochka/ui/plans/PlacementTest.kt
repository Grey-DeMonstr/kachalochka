package monster.greyde.kachalochka.ui.plans

import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.Plan
import monster.greyde.kachalochka.core.domain.gym.PlanId
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.family.SASHA
import monster.greyde.kachalochka.ui.family.traineeAccount
import monster.greyde.kachalochka.ui.friends.IVAN_SESSION
import monster.greyde.kachalochka.ui.friends.signedInGym
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class PlacementTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val yesterday = gym.today.plusDays(-1)
    private val press = Machine.new("Leg press", null, t0)
    private val row = Machine.new("Row", null, t0)
    private val curl = Machine.new("Biceps curl", null, t0)
    private val legs = Plan(PlanId.random(), null, "Legs", listOf(row.id), t0, t0, false)
    private val visit =
        Visit(VisitId.random(), null, yesterday, t0 - 1.days, t0, false, listOf(curl.id))

    private val placement = gym.placement

    private suspend fun seed() {
        listOf(press, row, curl).forEach { gym.machines.upsert(it) }
        gym.plans.upsert(legs)
        gym.visits.upsert(visit)
        gym.sets.upsert(set(press, 0))
        gym.sets.upsert(set(row, 1))
    }

    private fun set(
        machine: Machine,
        minutes: Int,
    ) = WorkoutSet(
        WorkoutSetId.random(),
        null,
        visit.id,
        machine.id,
        60.0,
        10,
        0,
        visit.recordedAt + minutes.minutes,
        t0,
        false,
    )

    private fun legsMachines() =
        gym.plans.rows
            .getValue(legs.id)
            .machineIds

    private suspend fun plannedToday() = gym.visits.onDay(null, gym.today)?.planned

    private fun activateIvan() {
        gym.withAccounts(IVAN_SESSION, active = IVAN_SESSION)
    }

    @Test
    fun the_choices_are_the_account_s_plans_by_their_titles() =
        runTest {
            seed()

            assertEquals(listOf(PlanChoiceUi(legs.id, "Legs")), placement.choices(null))
        }

    @Test
    fun no_choices_are_offered_once_another_account_is_active() =
        runTest {
            seed()
            activateIvan()

            assertNull(placement.choices(null))
        }

    @Test
    fun a_plan_takes_the_machines_it_lacks_at_its_end() =
        runTest {
            seed()

            assertTrue(placement.addToPlan(null, legs.id, listOf(press.id, row.id)))

            assertEquals(listOf(row.id, press.id), legsMachines())
        }

    @Test
    fun a_plan_holding_every_machine_is_not_written_again() =
        runTest {
            seed()

            assertTrue(placement.addToPlan(null, legs.id, listOf(row.id)))

            assertEquals(legs, gym.plans.rows.getValue(legs.id))
        }

    @Test
    fun a_deleted_plan_takes_nothing() =
        runTest {
            seed()
            gym.plans.upsert(legs.copy(deleted = true))

            assertFalse(placement.addToPlan(null, legs.id, listOf(press.id)))

            assertEquals(listOf(row.id), legsMachines())
        }

    @Test
    fun a_plan_takes_nothing_once_another_account_is_active() =
        runTest {
            seed()
            activateIvan()

            assertFalse(placement.addToPlan(null, legs.id, listOf(press.id)))

            assertEquals(legs, gym.plans.rows.getValue(legs.id))
        }

    @Test
    fun a_plan_takes_a_visit_s_machines_in_the_visit_s_order() =
        runTest {
            seed()

            assertTrue(placement.addVisitToPlan(null, legs.id, yesterday))

            assertEquals(listOf(row.id, press.id, curl.id), legsMachines())
        }

    @Test
    fun a_day_without_a_visit_adds_nothing_to_a_plan() =
        runTest {
            seed()

            assertFalse(placement.addVisitToPlan(null, legs.id, yesterday.plusDays(-1)))

            assertEquals(legs, gym.plans.rows.getValue(legs.id))
        }

    @Test
    fun a_visit_s_machines_are_its_sets_machines_then_its_planned_ones() =
        runTest {
            seed()

            assertEquals(
                listOf(press.id, row.id, curl.id),
                placement.machinesOn(null, yesterday),
            )
        }

    @Test
    fun today_plans_the_machines_in_today_s_visit() =
        runTest {
            seed()

            assertEquals(gym.today, placement.planToday(null, listOf(press.id)))

            assertEquals(listOf(press.id), plannedToday())
        }

    @Test
    fun today_plans_nothing_once_another_account_is_active() =
        runTest {
            seed()
            activateIvan()

            assertNull(placement.planToday(null, listOf(press.id)))

            assertNull(plannedToday())
        }

    @Test
    fun a_repeated_day_plans_its_machines_in_today_s_visit() =
        runTest {
            seed()

            assertEquals(gym.today, placement.repeatToday(null, yesterday))

            assertEquals(listOf(press.id, row.id, curl.id), plannedToday())
        }

    @Test
    fun a_day_repeats_nothing_once_another_account_is_active() =
        runTest {
            seed()
            activateIvan()

            assertNull(placement.repeatToday(null, yesterday))

            assertNull(plannedToday())
        }

    @Test
    fun a_started_plan_goes_into_today_s_visit_and_is_deleted() =
        runTest {
            seed()

            assertEquals(gym.today, placement.startPlan(null, legs))

            assertEquals(listOf(row.id), plannedToday())
            assertTrue(
                gym.plans.rows
                    .getValue(legs.id)
                    .deleted,
            )
        }

    @Test
    fun a_started_pinned_plan_is_kept() =
        runTest {
            val pinned = legs.copy(pinned = true)
            seed()
            gym.plans.upsert(pinned)

            assertEquals(gym.today, placement.startPlan(null, pinned))

            assertEquals(pinned, gym.plans.rows.getValue(legs.id))
        }

    @Test
    fun a_plan_starts_nothing_once_another_account_is_active() =
        runTest {
            seed()
            activateIvan()

            assertNull(placement.startPlan(null, legs))

            assertNull(plannedToday())
            assertEquals(legs, gym.plans.rows.getValue(legs.id))
        }

    @Test
    fun while_a_trainee_is_active_nothing_goes_into_today_s_visit() =
        runTest {
            val coached = signedInGym().withChild(traineeAccount(SASHA, IVAN_SESSION))
            coached.accounts.switchTo(SASHA.userId)
            val sashas = legs.copy(userId = SASHA.userId, pinned = false)
            coached.plans.upsert(sashas)

            assertNull(coached.placement.planToday(SASHA.userId, listOf(press.id)))
            assertNull(coached.placement.repeatToday(SASHA.userId, yesterday))
            assertNull(coached.placement.startPlan(SASHA.userId, sashas))

            assertEquals(emptyMap(), coached.visits.rows)
            assertEquals(sashas, coached.plans.rows.getValue(sashas.id))
        }
}
