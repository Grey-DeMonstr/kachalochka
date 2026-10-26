package monster.greyde.kachalochka.core.data.sync

import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.gym.MACHINE_TABLE
import monster.greyde.kachalochka.core.data.gym.MACHINE_TAG_TABLE
import monster.greyde.kachalochka.core.data.gym.PHOTO_TABLE
import monster.greyde.kachalochka.core.data.gym.PLAN_TABLE
import monster.greyde.kachalochka.core.data.gym.TAG_TABLE
import monster.greyde.kachalochka.core.data.gym.VISIT_TABLE
import monster.greyde.kachalochka.core.data.gym.WORKOUT_SET_TABLE
import monster.greyde.kachalochka.core.domain.gym.T0
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** MISHA plays a trainee IVAN coaches. */
class SyncPassTraineeTest {
    private val h = SyncHarness()

    @Test
    fun a_trainee_s_new_machines_and_plans_are_pushed_by_the_trainer() =
        runTest {
            val press = ownedPress(MISHA)
            val plan = ownedPlan(MISHA)
            h.machines.upsert(press)
            h.plans.upsert(plan)

            assertTrue(h.pass.run(listOf(MISHA), managed = setOf(MISHA), trainees = setOf(MISHA)))

            assertEquals(
                listOf("$MACHINE_TABLE:${press.id.value}", "$PLAN_TABLE:${plan.id.value}"),
                h.gateway.pushed,
            )
            assertEquals(listOf<UserId?>(MISHA, MISHA), h.gateway.pushedAs)
        }

    @Test
    fun a_trainee_s_visits_and_sets_stay_on_the_trainer_s_device() =
        runTest {
            val press = ownedPress(MISHA)
            val visit = ownedVisit(MISHA)
            h.machines.upsert(press)
            h.visits.upsert(visit)
            h.sets.upsert(ownedSet(MISHA, visit, press))
            h.outbox.removePushed(h.outbox.pending().single { it.tableName == MACHINE_TABLE })

            assertTrue(h.pass.run(listOf(MISHA), managed = setOf(MISHA), trainees = setOf(MISHA)))

            assertTrue(h.gateway.pushed.isEmpty())
            assertEquals(
                setOf(VISIT_TABLE, WORKOUT_SET_TABLE),
                h.outbox
                    .pending()
                    .map { it.tableName }
                    .toSet(),
            )
        }

    @Test
    fun a_trainee_pulls_the_gym_tables_but_no_links_to_friends() =
        runTest {
            h.watermarks.advance(MISHA, T0)

            h.pass.run(listOf(MISHA), managed = setOf(MISHA), trainees = setOf(MISHA))

            assertEquals(
                setOf(
                    MACHINE_TABLE,
                    VISIT_TABLE,
                    WORKOUT_SET_TABLE,
                    PHOTO_TABLE,
                    PLAN_TABLE,
                    TAG_TABLE,
                    MACHINE_TAG_TABLE,
                ),
                h.gateway.pulledTables
                    .map { it.second }
                    .toSet(),
            )
        }

    @Test
    fun a_trainee_s_visits_are_not_normalized_on_the_trainer_s_device() =
        runTest {
            h.visits.upsert(ownedVisit(MISHA))
            h.visits.upsert(ownedVisit(MISHA))

            h.pass.runAndNormalize(
                listOf(MISHA),
                h.normalizer,
                h.failures::add,
                managed = setOf(MISHA),
                trainees = setOf(MISHA),
            )

            assertEquals(2, h.visits.all(MISHA).size)
            assertTrue(h.gateway.pushed.isEmpty())
        }
}
