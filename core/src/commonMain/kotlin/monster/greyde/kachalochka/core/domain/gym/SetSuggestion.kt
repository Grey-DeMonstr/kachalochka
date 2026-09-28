package monster.greyde.kachalochka.core.domain.gym

import kotlin.time.Instant

const val DEFAULT_REPS: Int = 10

fun previousVisitSets(
    machineSets: List<WorkoutSet>,
    currentVisit: VisitId?,
    before: Instant? = null,
): List<WorkoutSet> {
    val earlier =
        machineSets.filter {
            it.visitId != currentVisit && !it.deleted && (before == null || it.recordedAt < before)
        }
    val latest = earlier.maxByOrNull { it.recordedAt }?.visitId ?: return emptyList()
    return earlier.filter { it.visitId == latest }.sortedBy { it.recordedAt }
}

/**
 * [thisVisit] holds the sets already recorded on [machine] in this visit, in order. Once a set is
 * recorded its weight carries on, since the user just chose it; the reps still follow the
 * previous visit's pattern.
 */
fun suggestNextSet(
    machine: Machine,
    previousVisit: List<WorkoutSet>,
    thisVisit: List<WorkoutSet>,
): SetValues {
    val pattern =
        previousVisit.getOrNull(thisVisit.size) ?: thisVisit.lastOrNull()
            ?: previousVisit.lastOrNull()
    return pattern?.let { SetValues(thisVisit.lastOrNull()?.weight ?: it.weight, it.reps) }
        ?: SetValues(if (machine.platformIncluded) machine.platformWeight else 0.0, DEFAULT_REPS)
}
