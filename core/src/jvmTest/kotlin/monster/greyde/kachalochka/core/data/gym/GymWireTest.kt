package monster.greyde.kachalochka.core.data.gym

import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun units_use_the_names_the_postgres_check_accepts() {
        assertEquals(listOf("kg", "lb"), WeightUnit.entries.map { it.wireName() })
        WeightUnit.entries.forEach { assertEquals(it, weightUnitOf(it.wireName())) }
    }
}
