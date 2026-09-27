package monster.greyde.kachalochka.core.data.gym

import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class GymWireTest {
    @Test
    fun weight_modes_use_the_names_the_postgres_check_accepts() {
        assertEquals(listOf("total", "per_side"), WeightMode.entries.map { it.wireName() })
        WeightMode.entries.forEach { assertEquals(it, weightModeOf(it.wireName())) }
    }

    @Test
    fun a_counterweight_or_unknown_mode_reads_as_total() {
        assertEquals(WeightMode.Total, weightModeOf("counterweight"))
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
}
