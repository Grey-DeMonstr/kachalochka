package monster.greyde.kachalochka.core.data.sync

import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.gym.MACHINE_TABLE
import monster.greyde.kachalochka.core.data.gym.VISIT_TABLE
import monster.greyde.kachalochka.core.data.gym.WORKOUT_SET_TABLE
import monster.greyde.kachalochka.core.data.profile.PROFILE_TABLE
import monster.greyde.kachalochka.core.domain.gym.T0
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.sync.OutboxEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class SyncPassPushTest {
    private val h = SyncHarness()

    @Test
    fun sets_are_pushed_after_their_visit_and_machine_even_when_enqueued_first() =
        runTest {
            val visit = ownedVisit(IVAN, updatedAt = T0 + 2.hours)
            val press = ownedPress(IVAN, updatedAt = T0 + 2.hours)
            h.sets.upsert(ownedSet(IVAN, visit, press, updatedAt = T0))
            h.visits.upsert(visit)
            h.machines.upsert(press)

            h.pass.run(listOf(IVAN))

            assertEquals(
                WORKOUT_SET_TABLE,
                h.gateway.pushed
                    .last()
                    .substringBefore(':'),
            )
            assertEquals(3, h.gateway.pushed.size)
        }

    @Test
    fun a_profile_is_pushed_like_any_other_row() =
        runTest {
            val profile = ownedProfile(IVAN)
            h.profiles.upsert(profile)

            h.pass.run(listOf(IVAN))

            assertEquals(listOf("$PROFILE_TABLE:${profile.id.value}"), h.gateway.pushed)
        }

    @Test
    fun a_pushed_row_leaves_the_outbox() =
        runTest {
            h.visits.upsert(ownedVisit(IVAN))

            h.pass.run(listOf(IVAN))

            assertTrue(h.outbox.pending().isEmpty())
        }

    @Test
    fun a_failed_push_stays_enqueued_and_the_rest_still_go() =
        runTest {
            h.visits.upsert(ownedVisit(IVAN))
            h.machines.upsert(ownedPress(IVAN))
            h.gateway.failing = VISIT_TABLE

            val clean = h.pass.run(listOf(IVAN))

            assertFalse(clean)
            assertEquals(listOf(VISIT_TABLE), h.outbox.pending().map { it.tableName })
            assertEquals(
                MACHINE_TABLE,
                h.gateway.pushed
                    .single()
                    .substringBefore(':'),
            )
        }

    @Test
    fun another_account_s_rows_wait_for_that_account() =
        runTest {
            val mishas = ownedVisit(MISHA)
            h.visits.upsert(ownedVisit(IVAN))
            h.visits.upsert(mishas)

            val clean = h.pass.run(listOf(IVAN))

            assertTrue(clean)
            assertEquals(listOf(mishas.id.value), h.outbox.pending().map { it.rowId })
        }

    @Test
    fun each_row_is_pushed_while_the_pass_is_on_its_owner() =
        runTest {
            h.visits.upsert(ownedVisit(IVAN))
            h.visits.upsert(ownedVisit(MISHA))

            h.pass.run(listOf(IVAN, MISHA))

            assertEquals(listOf<UserId?>(IVAN, MISHA), h.gateway.pushedAs)
            assertNull(h.session.owner)
        }

    @Test
    fun an_entry_whose_row_is_missing_leaves_the_outbox_unpushed() =
        runTest {
            h.outbox.enqueue(OutboxEntry(VISIT_TABLE, VisitId.random().value, T0))

            h.pass.run(listOf(IVAN))

            assertTrue(h.outbox.pending().isEmpty())
            assertTrue(h.gateway.pushed.isEmpty())
        }
}
