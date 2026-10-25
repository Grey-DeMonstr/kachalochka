package monster.greyde.kachalochka.ui.visit

import monster.greyde.kachalochka.core.domain.gym.DEFAULT_REPS
import monster.greyde.kachalochka.core.domain.gym.SetValues
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.roundWeight
import monster.greyde.kachalochka.core.domain.gym.stepReps
import monster.greyde.kachalochka.core.domain.gym.stepWeight
import monster.greyde.kachalochka.ui.format.formatNumber
import monster.greyde.kachalochka.ui.format.parseDecimal

const val COMMENT_LENGTH = 200

/** More reps than anyone does in one set: a typo, not a record. */
private const val MAX_REPS = 999

/** The set being added, or [editing] when one is corrected, with its fields as typed. */
class SetForm {
    var isOpen = false
        private set
    var editing: WorkoutSet? = null
        private set
    var values = SetValues(0.0, DEFAULT_REPS)
        private set
    var comment = ""
        private set

    /** The text as typed; null while the weight shows the stepped, formatted value. */
    private var weightText: String? = null
    private val typedWeight: Double? get() = weightText?.let(::parseDecimal)?.takeIf { it >= 0 }

    /** The reps as typed; null while they show the stepped value. */
    private var repsText: String? = null
    private val typedReps: Int?
        get() = repsText?.trim()?.toIntOrNull()?.takeIf { it in 1..MAX_REPS }

    val weightShown: String get() = weightText ?: formatNumber(values.weight)
    val repsShown: String get() = repsText ?: values.reps.toString()
    val valid: Boolean
        get() =
            (weightText == null || typedWeight != null) &&
                (repsText == null || typedReps != null)

    fun openNew() {
        isOpen = true
        editing = null
        comment = ""
    }

    fun openOn(set: WorkoutSet) {
        isOpen = true
        editing = set
        values = SetValues(set.weight, set.reps)
        weightText = null
        repsText = null
        comment = set.comment
    }

    fun close() {
        isOpen = false
        editing = null
        weightText = null
        repsText = null
        comment = ""
    }

    /** Shows [next] in place of what was chosen or typed. */
    fun suggest(next: SetValues) {
        values = next
        weightText = null
        repsText = null
    }

    fun changeWeight(
        step: Double,
        direction: Int,
    ) {
        weightText = null
        values = values.copy(weight = stepWeight(values.weight, step, direction))
    }

    fun typeWeight(text: String) {
        weightText = text
        typedWeight?.let { values = values.copy(weight = roundWeight(it)) }
    }

    fun changeReps(direction: Int) {
        repsText = null
        values = values.copy(reps = stepReps(values.reps, direction))
    }

    fun typeReps(text: String) {
        repsText = text
        typedReps?.let { values = values.copy(reps = it) }
    }

    fun typeComment(text: String) {
        comment = text.take(COMMENT_LENGTH)
    }

    fun clearComment() {
        comment = ""
    }
}
