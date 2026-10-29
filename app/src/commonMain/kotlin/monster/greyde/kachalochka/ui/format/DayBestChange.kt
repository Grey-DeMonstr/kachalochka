package monster.greyde.kachalochka.ui.format

import monster.greyde.kachalochka.core.domain.gym.DayBest
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.weightGain
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.ui.strings.AppStrings

/**
 * How [today] beats [prior] in the viewer's unit: weight first, at the same shown weight the reps,
 * at the same reps the sets; null when it does not.
 */
fun dayBestGain(
    machine: Machine,
    prior: DayBest,
    today: DayBest,
    preferred: PreferredWeightUnit,
): String? {
    val gain =
        weightGain(
            shownWeight(prior.weight, machine, preferred),
            shownWeight(today.weight, machine, preferred),
            machine.weightMode,
        )
    val strings = AppStrings.current
    return when {
        gain > 0 -> "+${formatNumber(gain)} ${shownLabel(machine, preferred)}"
        gain < 0 -> null
        today.reps > prior.reps -> strings.moreReps(today.reps - prior.reps)
        today.reps < prior.reps -> null
        today.sets > prior.sets -> strings.moreSets(today.sets - prior.sets)
        else -> null
    }
}
