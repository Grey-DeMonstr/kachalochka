package monster.greyde.kachalochka.core.data.gym

import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WeightUnit

const val MACHINE_TABLE: String = "machine"
const val VISIT_TABLE: String = "visit"
const val WORKOUT_SET_TABLE: String = "workout_set"

fun WeightMode.wireName(): String =
    when (this) {
        WeightMode.Total -> "total"
        WeightMode.PerSide -> "per_side"
        WeightMode.Counterweight -> "counterweight"
    }

fun weightModeOf(wire: String): WeightMode = WeightMode.entries.first { it.wireName() == wire }

fun WeightUnit.wireName(): String =
    when (this) {
        WeightUnit.Kg -> "kg"
        WeightUnit.Lb -> "lb"
    }

fun weightUnitOf(wire: String): WeightUnit = WeightUnit.entries.first { it.wireName() == wire }
