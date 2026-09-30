package monster.greyde.kachalochka.core.data.sync

import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.gym.MACHINE_LINK_TABLE
import monster.greyde.kachalochka.core.data.gym.PLAN_TABLE
import monster.greyde.kachalochka.core.data.gym.VISIT_TABLE
import monster.greyde.kachalochka.core.data.measures.MEASUREMENT_TABLE
import monster.greyde.kachalochka.core.data.measures.MEASURE_TABLE
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.T0
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.measures.MeasureKind
import monster.greyde.kachalochka.core.domain.measures.missingDefaults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class SyncPassPullTest {
    private val h = SyncHarness()

    @Test
    fun a_pulled_visit_keeps_the_machines_planned_for_it() =
        runTest {
            val theirs = ownedVisit(IVAN).copy(planned = listOf(MachineId.random()))
            h.gateway.visitsToPull = listOf(theirs)

            h.pass.run(listOf(IVAN))

            assertEquals(theirs, h.visits.byId(theirs.id))
        }

    @Test
    fun a_pulled_plan_is_written_locally_and_counts_for_the_watermark() =
        runTest {
            val plan = ownedPlan(IVAN, updatedAt = T0 + 5.hours)
            h.gateway.plansToPull = listOf(plan)

            h.pass.run(listOf(IVAN))

            assertEquals(plan, h.plans.byId(plan.id))
            assertEquals(T0 + 5.hours, h.watermarks.lastPullAt(IVAN))
        }

    @Test
    fun a_plan_waiting_in_the_outbox_survives_the_pull_that_would_overwrite_it() =
        runTest {
            val mine = ownedPlan(IVAN)
            h.plans.upsert(mine)
            h.gateway.failing = PLAN_TABLE
            h.gateway.plansToPull = listOf(mine.copy(name = "Спина"))

            h.pass.run(listOf(IVAN))

            assertEquals(mine, h.plans.byId(mine.id))
        }

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
    fun a_pulled_machine_keeps_its_own_unit() =
        runTest {
            val gravitron = ownedPress(IVAN).copy(unit = WeightUnit.Custom, unitLabel = "плитка")
            h.gateway.machinesToPull = listOf(gravitron)

            h.pass.run(listOf(IVAN))

            assertEquals(gravitron, h.machines.byId(gravitron.id))
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
    fun a_pulled_link_is_written_locally() =
        runTest {
            val link = ownedLink(IVAN)
            h.gateway.linksToPull = listOf(link)

            h.pass.run(listOf(IVAN))

            assertEquals(listOf(link), h.links.all(IVAN))
        }

    @Test
    fun a_link_waiting_in_the_outbox_survives_the_pull_that_would_overwrite_it() =
        runTest {
            val mine = ownedLink(IVAN)
            h.links.upsert(mine)
            h.gateway.failing = MACHINE_LINK_TABLE
            h.gateway.linksToPull = listOf(mine.copy(deleted = true))

            h.pass.run(listOf(IVAN))

            assertEquals(listOf(mine), h.links.all(IVAN))
        }

    @Test
    fun the_watermark_counts_pulled_links() =
        runTest {
            h.gateway.visitsToPull = listOf(ownedVisit(IVAN, updatedAt = T0 + 1.hours))
            h.gateway.linksToPull = listOf(ownedLink(IVAN, updatedAt = T0 + 2.hours))

            h.pass.run(listOf(IVAN))

            assertEquals(T0 + 2.hours, h.watermarks.lastPullAt(IVAN))
        }

    @Test
    fun a_pulled_measure_and_its_value_are_written_locally() =
        runTest {
            val neck = ownedMeasure(IVAN)
            val monday = ownedMeasurement(IVAN, neck)
            h.gateway.measuresToPull = listOf(neck)
            h.gateway.measurementsToPull = listOf(monday)

            h.pass.run(listOf(IVAN))

            assertEquals(listOf(neck), h.measures.all(IVAN))
            assertEquals(listOf(monday), h.measurements.all(IVAN))
        }

    @Test
    fun a_measure_waiting_in_the_outbox_survives_the_pull_that_would_overwrite_it() =
        runTest {
            val neck = ownedMeasure(IVAN)
            h.measures.upsert(neck)
            h.gateway.failing = MEASURE_TABLE
            h.gateway.measuresToPull = listOf(neck.copy(name = "Шея сзади"))

            h.pass.run(listOf(IVAN))

            assertEquals(listOf(neck), h.measures.all(IVAN))
        }

    @Test
    fun a_rename_pulled_from_another_device_replaces_a_seed_still_waiting_to_be_pushed() =
        runTest {
            val seed =
                missingDefaults(IVAN, MeasureKind.entries.toSet() - MeasureKind.Neck).single()
            val renamed = seed.copy(name = "Шея сзади", updatedAt = T0)
            h.measures.upsert(seed)
            h.gateway.failing = MEASURE_TABLE
            h.gateway.measuresToPull = listOf(renamed)

            h.pass.run(listOf(IVAN))

            assertEquals(listOf(renamed), h.measures.all(IVAN))
            assertTrue(h.outbox.pending().none { it.tableName == MEASURE_TABLE })
        }

    @Test
    fun a_value_waiting_in_the_outbox_survives_the_pull_that_would_overwrite_it() =
        runTest {
            val monday = ownedMeasurement(IVAN, ownedMeasure(IVAN))
            h.measurements.upsert(monday)
            h.gateway.failing = MEASUREMENT_TABLE
            h.gateway.measurementsToPull = listOf(monday.copy(value = 40.0))

            h.pass.run(listOf(IVAN))

            assertEquals(listOf(monday), h.measurements.all(IVAN))
        }

    @Test
    fun the_watermark_counts_pulled_measures_and_values() =
        runTest {
            val neck = ownedMeasure(IVAN, updatedAt = T0 + 1.hours)
            h.gateway.measuresToPull = listOf(neck)
            h.gateway.measurementsToPull =
                listOf(ownedMeasurement(IVAN, neck, updatedAt = T0 + 2.hours))

            h.pass.run(listOf(IVAN))

            assertEquals(T0 + 2.hours, h.watermarks.lastPullAt(IVAN))
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
