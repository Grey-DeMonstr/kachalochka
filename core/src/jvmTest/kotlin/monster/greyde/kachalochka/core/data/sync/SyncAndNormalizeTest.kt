package monster.greyde.kachalochka.core.data.sync

import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.gym.VISIT_TABLE
import monster.greyde.kachalochka.core.data.gym.WORKOUT_SET_TABLE
import monster.greyde.kachalochka.core.domain.gym.T0
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class SyncAndNormalizeTest {
    private val h = SyncHarness()

    @Test
    fun visits_pulled_onto_one_day_are_normalized_and_the_result_pushed_in_a_second_pass() =
        runTest {
            val old = ownedVisit(IVAN).copy(day = null, recordedAt = T0 - 1.hours)
            val current = ownedVisit(IVAN)
            val press = ownedPress(IVAN)
            val oldSet = ownedSet(IVAN, old, press)
            h.gateway.visitsToPull = listOf(old, current)
            h.gateway.machinesToPull = listOf(press)
            h.gateway.setsToPull = listOf(oldSet)

            assertTrue(h.pass.runAndNormalize(listOf(IVAN), h.normalizer))

            assertEquals(
                listOf("$VISIT_TABLE:${old.id.value}", "$WORKOUT_SET_TABLE:${oldSet.id.value}"),
                h.gateway.pushed,
            )
            assertEquals(8, h.gateway.pulledSince.size)
        }

    @Test
    fun an_account_that_could_not_pull_keeps_its_local_visits_as_they_are() =
        runTest {
            val rows = LocalSyncRows(h.database)
            val older = ownedVisit(IVAN).copy(recordedAt = T0 - 1.hours)
            rows.writeVisit(older)
            rows.writeVisit(ownedVisit(IVAN))
            h.gateway.pullFails = true

            assertFalse(h.pass.runAndNormalize(listOf(IVAN), h.normalizer))

            assertEquals(older, h.visits.byId(older.id))
            assertEquals(emptyList(), h.outbox.pending())
        }

    @Test
    fun a_pass_with_nothing_to_normalize_runs_once() =
        runTest {
            h.gateway.visitsToPull = listOf(ownedVisit(IVAN))

            assertTrue(h.pass.runAndNormalize(listOf(IVAN), h.normalizer))

            assertEquals(emptyList(), h.gateway.pushed)
            assertEquals(4, h.gateway.pulledSince.size)
        }
}
