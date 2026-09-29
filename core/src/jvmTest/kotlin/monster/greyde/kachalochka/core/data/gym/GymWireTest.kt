package monster.greyde.kachalochka.core.data.gym

import kotlinx.serialization.json.Json
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class GymWireTest {
    @Test
    fun weight_modes_use_the_names_the_postgres_check_accepts() {
        assertEquals(
            listOf("total", "per_side", "counterweight"),
            WeightMode.entries.map { it.wireName() },
        )
        WeightMode.entries.forEach { assertEquals(it, weightModeOf(it.wireName())) }
    }

    @Test
    fun an_unknown_mode_reads_as_total() {
        assertEquals(WeightMode.Total, weightModeOf("hydraulic"))
    }

    @Test
    fun units_use_the_names_the_postgres_check_accepts() {
        assertEquals(listOf("kg", "lb", "custom"), WeightUnit.entries.map { it.wireName() })
        WeightUnit.entries.forEach { assertEquals(it, weightUnitOf(it.wireName())) }
    }

    @Test
    fun an_unknown_unit_reads_as_kilograms() {
        assertEquals(WeightUnit.Kg, weightUnitOf("stone"))
    }

    private val set =
        WorkoutSet(
            WorkoutSetId.random(),
            null,
            VisitId.random(),
            MachineId.random(),
            80.0,
            8,
            1,
            Instant.fromEpochSeconds(1_700_000_000),
            Instant.fromEpochSeconds(1_700_000_000),
            false,
            comment = "Тяжело",
        )

    @Test
    fun a_set_s_comment_travels_both_ways() {
        assertEquals(set, WorkoutSetRow.of(set).toWorkoutSet())
    }

    @Test
    fun an_emptied_comment_is_still_sent() {
        val json = Json.encodeToString(WorkoutSetRow.of(set.copy(comment = "")))

        assertTrue("\"comment\":\"\"" in json, json)
    }
}
