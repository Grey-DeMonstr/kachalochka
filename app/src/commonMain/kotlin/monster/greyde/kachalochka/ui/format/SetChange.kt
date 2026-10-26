package monster.greyde.kachalochka.ui.format

import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.weightGain
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.ui.strings.AppStrings

data class SetChange(
    val text: String,
    val gain: Boolean,
)

/** From [from] to [to] in the viewer's unit: weight first; at the same shown weight, the reps. */
fun setChange(
    machine: Machine,
    from: WorkoutSet,
    to: WorkoutSet,
    preferred: PreferredWeightUnit,
): SetChange {
    val gain =
        weightGain(
            shownWeight(from.weight, machine, preferred),
            shownWeight(to.weight, machine, preferred),
            machine.weightMode,
        )
    val reps = to.reps - from.reps
    val strings = AppStrings.current
    val unit = shownLabel(machine, preferred)
    return when {
        gain > 0 -> SetChange("+${formatNumber(gain)} $unit", gain = true)
        gain < 0 -> SetChange("−${formatNumber(-gain)} $unit", gain = false)
        reps > 0 -> SetChange(strings.moreReps(reps), gain = true)
        reps < 0 -> SetChange(strings.fewerReps(-reps), gain = false)
        else -> SetChange(strings.noChange, gain = false)
    }
}
