package monster.greyde.kachalochka.ui.format

import monster.greyde.kachalochka.core.domain.gym.KG_PER_LB
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import kotlin.math.round

/** The unit a machine's weights in [unit] are shown in; an own unit never converts. */
fun shownUnit(
    unit: WeightUnit,
    preferred: PreferredWeightUnit,
): WeightUnit =
    when {
        unit == WeightUnit.Custom -> unit
        preferred == PreferredWeightUnit.Kg -> WeightUnit.Kg
        preferred == PreferredWeightUnit.Lb -> WeightUnit.Lb
        else -> unit
    }

/** A converted weight rounds to the nearest half unit. */
fun shownWeight(
    weight: Double,
    from: WeightUnit,
    to: WeightUnit,
): Double = converted(weight, from, to, perUnit = 2.0)

/** A step keeps one decimal: half units would turn a 5 lb step into 2,5 kg. */
fun shownStep(
    step: Double,
    from: WeightUnit,
    to: WeightUnit,
): Double = converted(step, from, to, perUnit = 10.0)

private fun converted(
    value: Double,
    from: WeightUnit,
    to: WeightUnit,
    perUnit: Double,
): Double {
    val factor =
        when {
            from == WeightUnit.Lb && to == WeightUnit.Kg -> KG_PER_LB
            from == WeightUnit.Kg && to == WeightUnit.Lb -> 1 / KG_PER_LB
            else -> return value
        }
    return round(value * factor * perUnit) / perUnit
}

internal fun shownUnit(
    machine: Machine,
    preferred: PreferredWeightUnit,
): WeightUnit = shownUnit(machine.unit, preferred)

internal fun shownLabel(
    machine: Machine,
    preferred: PreferredWeightUnit,
): String = unitLabel(shownUnit(machine, preferred), machine.unitLabel)

internal fun shownWeight(
    weight: Double,
    machine: Machine,
    preferred: PreferredWeightUnit,
): Double = shownWeight(weight, machine.unit, shownUnit(machine, preferred))
