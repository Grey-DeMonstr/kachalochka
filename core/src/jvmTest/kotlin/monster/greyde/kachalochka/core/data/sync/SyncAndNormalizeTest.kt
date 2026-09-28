package monster.greyde.kachalochka.core.data.sync

import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.gym.VISIT_TABLE
import monster.greyde.kachalochka.core.data.gym.VisitNormalizer
import monster.greyde.kachalochka.core.data.gym.WORKOUT_SET_TABLE
import monster.greyde.kachalochka.core.domain.gym.T0
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
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
            h.gateway.setsToPull = listOf(oldSet, ownedSet(IVAN, current, press))

            assertTrue(h.pass.runAndNormalize(listOf(IVAN), h.normalizer, h.failures::add))

            assertEquals(
                listOf("$VISIT_TABLE:${old.id.value}", "$WORKOUT_SET_TABLE:${oldSet.id.value}"),
                h.gateway.pushed,
            )
            assertEquals(16, h.gateway.pulledSince.size)
            assertEquals(emptyList(), h.failures)
        }

    @Test
    fun an_account_that_could_not_pull_keeps_its_local_visits_as_they_are() =
        runTest {
            val rows = LocalSyncRows(h.database)
            val older = ownedVisit(IVAN).copy(recordedAt = T0 - 1.hours)
            rows.writeVisit(older)
            rows.writeVisit(ownedVisit(IVAN))
            h.gateway.pullFails = true

            assertFalse(h.pass.runAndNormalize(listOf(IVAN), h.normalizer, h.failures::add))

            assertEquals(older, h.visits.byId(older.id))
            assertEquals(emptyList(), h.outbox.pending())
        }

    @Test
    fun a_pass_with_nothing_to_normalize_runs_once() =
        runTest {
            h.gateway.visitsToPull = listOf(ownedVisit(IVAN))

            assertTrue(h.pass.runAndNormalize(listOf(IVAN), h.normalizer, h.failures::add))

            assertEquals(emptyList(), h.gateway.pushed)
            assertEquals(8, h.gateway.pulledSince.size)
        }

    @Test
    fun an_account_whose_normalization_fails_is_reported_and_the_others_still_go_out() =
        runTest {
            val mishas = ownedVisit(MISHA).copy(recordedAt = T0 - 1.hours)
            h.gateway.visitsToPull =
                listOf(
                    ownedVisit(IVAN).copy(recordedAt = T0 - 1.hours),
                    ownedVisit(IVAN),
                    mishas,
                    ownedVisit(MISHA),
                )
            val brokenForIvan =
                object : VisitRepository by h.visits {
                    override suspend fun all(owner: UserId?): List<Visit> =
                        if (owner == IVAN) error("unreadable row") else h.visits.all(owner)
                }
            val normalizer = VisitNormalizer(brokenForIvan, h.sets, h.clock) { Duration.ZERO }

            assertTrue(h.pass.runAndNormalize(listOf(IVAN, MISHA), normalizer, h.failures::add))

            assertEquals(listOf("unreadable row"), h.failures.map { it.message })
            assertEquals(listOf("$VISIT_TABLE:${mishas.id.value}"), h.gateway.pushed)
        }
}
