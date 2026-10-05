package monster.greyde.kachalochka.core.data.sync

import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.gym.MACHINE_LINK_TABLE
import monster.greyde.kachalochka.core.data.gym.MACHINE_TABLE
import monster.greyde.kachalochka.core.data.gym.PHOTO_TABLE
import monster.greyde.kachalochka.core.data.gym.PLAN_TABLE
import monster.greyde.kachalochka.core.data.gym.VISIT_TABLE
import monster.greyde.kachalochka.core.data.gym.WORKOUT_SET_TABLE
import monster.greyde.kachalochka.core.data.measures.MEASUREMENT_TABLE
import monster.greyde.kachalochka.core.data.measures.MEASURE_TABLE
import monster.greyde.kachalochka.core.data.profile.PROFILE_TABLE
import monster.greyde.kachalochka.core.domain.gym.T0
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/** MISHA plays a child IVAN guards. */
class SyncPassManagedTest {
    private val h = SyncHarness()

    @Test
    fun a_managed_owner_s_gym_rows_are_pushed_while_the_pass_is_on_it() =
        runTest {
            val visit = ownedVisit(MISHA)
            h.visits.upsert(visit)

            assertTrue(h.pass.run(listOf(IVAN, MISHA), managed = setOf(MISHA)))

            assertEquals(listOf("$VISIT_TABLE:${visit.id.value}"), h.gateway.pushed)
            assertEquals(listOf<UserId?>(MISHA), h.gateway.pushedAs)
        }

    @Test
    fun a_managed_owner_s_private_rows_stay_on_the_device() =
        runTest {
            val profile = ownedProfile(MISHA)
            val measure = ownedMeasure(MISHA)
            h.profiles.upsert(profile)
            h.measures.upsert(measure)
            h.measurements.upsert(ownedMeasurement(MISHA, measure))

            assertTrue(h.pass.run(listOf(MISHA), managed = setOf(MISHA)))

            assertTrue(h.gateway.pushed.isEmpty())
            assertEquals(
                setOf(PROFILE_TABLE, MEASURE_TABLE, MEASUREMENT_TABLE),
                h.outbox
                    .pending()
                    .map { it.tableName }
                    .toSet(),
            )
            assertEquals(profile, h.profiles.forOwner(MISHA))
        }

    @Test
    fun a_managed_owner_pulls_only_the_gym_tables_from_its_own_watermark() =
        runTest {
            h.watermarks.advance(MISHA, T0)

            h.pass.run(listOf(IVAN, MISHA), managed = setOf(MISHA))

            assertEquals(
                setOf(
                    MACHINE_TABLE,
                    VISIT_TABLE,
                    WORKOUT_SET_TABLE,
                    MACHINE_LINK_TABLE,
                    PHOTO_TABLE,
                    PLAN_TABLE,
                ),
                h.gateway.pulledTables
                    .filter { it.first == MISHA }
                    .map { it.second }
                    .toSet(),
            )
            assertEquals(9, h.gateway.pulledTables.count { it.first == IVAN })
            assertTrue((MISHA to T0) in h.gateway.pulledSince)
        }

    @Test
    fun a_row_pulled_for_a_managed_owner_moves_only_its_watermark() =
        runTest {
            h.gateway.visitsToPull = listOf(ownedVisit(MISHA, updatedAt = T0 + 1.hours))

            h.pass.run(listOf(IVAN, MISHA), managed = setOf(MISHA))

            assertEquals(T0 + 1.hours, h.watermarks.lastPullAt(MISHA))
            assertEquals(null, h.watermarks.lastPullAt(IVAN))
        }

    @Test
    fun a_pass_mixing_signed_in_and_managed_owners_pushes_only_what_each_may_write() =
        runTest {
            val ivan = ownedProfile(IVAN)
            val misha = ownedProfile(MISHA)
            h.profiles.upsert(ivan)
            h.profiles.upsert(misha)

            assertTrue(h.pass.run(listOf(IVAN, MISHA), managed = setOf(MISHA)))

            assertEquals(listOf("$PROFILE_TABLE:${ivan.id.value}"), h.gateway.pushed)
            assertEquals(listOf<UserId?>(IVAN), h.gateway.pushedAs)
            assertEquals(listOf(misha.id.value), h.outbox.pending().map { it.rowId })
        }

    @Test
    fun running_and_normalizing_keeps_a_managed_owner_s_private_rows_on_the_device() =
        runTest {
            val measure = ownedMeasure(MISHA)
            h.profiles.upsert(ownedProfile(MISHA))
            h.measures.upsert(measure)

            assertTrue(
                h.pass.runAndNormalize(
                    listOf(MISHA),
                    h.normalizer,
                    h.failures::add,
                    managed = setOf(MISHA),
                ),
            )

            assertTrue(h.gateway.pushed.isEmpty())
            assertEquals(
                setOf(PROFILE_TABLE, MEASURE_TABLE),
                h.outbox
                    .pending()
                    .map { it.tableName }
                    .toSet(),
            )
        }
}
