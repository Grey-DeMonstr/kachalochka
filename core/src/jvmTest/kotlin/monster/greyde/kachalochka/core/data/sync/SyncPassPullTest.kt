package monster.greyde.kachalochka.core.data.sync

import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.gym.VISIT_TABLE
import monster.greyde.kachalochka.core.domain.gym.T0
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class SyncPassPullTest {
    private val h = SyncHarness()

    @Test
    fun a_pulled_row_is_written_locally() =
        runTest {
            val theirs = ownedVisit(IVAN)
            h.gateway.visitsToPull = listOf(theirs)

            h.pass.run(listOf(IVAN))

            assertEquals(theirs, h.visits.byId(theirs.id))
        }

    @Test
    fun a_pass_where_every_push_and_pull_succeeded_reports_it() =
        runTest {
            h.visits.upsert(ownedVisit(IVAN))
            h.gateway.visitsToPull = listOf(ownedVisit(IVAN))

            assertTrue(h.pass.run(listOf(IVAN, MISHA)))
        }

    @Test
    fun a_pulled_profile_is_written_locally() =
        runTest {
            val profile = ownedProfile(IVAN)
            h.gateway.profilesToPull = listOf(profile)

            h.pass.run(listOf(IVAN))

            assertEquals(profile, h.profiles.byId(profile.id))
        }

    @Test
    fun a_pulled_row_is_not_pushed_straight_back() =
        runTest {
            h.gateway.visitsToPull = listOf(ownedVisit(IVAN))

            h.pass.run(listOf(IVAN))
            h.pass.run(listOf(IVAN))

            assertTrue(h.outbox.pending().isEmpty())
            assertTrue(h.gateway.pushed.isEmpty())
        }

    @Test
    fun a_row_waiting_in_the_outbox_survives_the_pull_that_would_overwrite_it() =
        runTest {
            val mine = ownedVisit(IVAN)
            h.visits.upsert(mine)
            h.gateway.failing = VISIT_TABLE
            h.gateway.visitsToPull = listOf(mine.copy(deleted = true))

            h.pass.run(listOf(IVAN))

            assertEquals(false, h.visits.byId(mine.id)?.deleted)
        }

    @Test
    fun the_watermark_advances_to_the_newest_row_of_any_table() =
        runTest {
            h.gateway.visitsToPull = listOf(ownedVisit(IVAN, updatedAt = T0 + 1.hours))
            h.gateway.profilesToPull = listOf(ownedProfile(IVAN, updatedAt = T0 + 3.hours))

            h.pass.run(listOf(IVAN))

            assertEquals(T0 + 3.hours, h.watermarks.lastPullAt(IVAN))
        }

    @Test
    fun each_account_pulls_from_its_own_watermark() =
        runTest {
            h.watermarks.advance(IVAN, T0)

            h.pass.run(listOf(IVAN, MISHA))

            assertEquals(listOf(IVAN to T0, MISHA to null), h.gateway.pulledSince.distinct())
        }

    @Test
    fun a_failed_pull_keeps_the_watermark_and_writes_nothing() =
        runTest {
            h.watermarks.advance(IVAN, T0)
            val theirs = ownedVisit(IVAN, updatedAt = T0 + 1.hours)
            h.gateway.visitsToPull = listOf(theirs)
            h.gateway.pullFails = true

            val clean = h.pass.run(listOf(IVAN))

            assertFalse(clean)
            assertEquals(T0, h.watermarks.lastPullAt(IVAN))
            assertNull(h.visits.byId(theirs.id))
        }
}
