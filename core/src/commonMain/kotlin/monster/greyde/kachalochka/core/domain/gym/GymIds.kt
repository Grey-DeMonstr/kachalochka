package monster.greyde.kachalochka.core.domain.gym

import monster.greyde.kachalochka.core.domain.identity.newUuidV4
import monster.greyde.kachalochka.core.domain.identity.requireUuidV4
import kotlin.jvm.JvmInline

@JvmInline
value class MachineId(
    val value: String,
) {
    init {
        requireUuidV4(value, "MachineId")
    }

    companion object {
        fun random(): MachineId = MachineId(newUuidV4())
    }
}

@JvmInline
value class VisitId(
    val value: String,
) {
    init {
        requireUuidV4(value, "VisitId")
    }

    companion object {
        fun random(): VisitId = VisitId(newUuidV4())
    }
}

@JvmInline
value class WorkoutSetId(
    val value: String,
) {
    init {
        requireUuidV4(value, "WorkoutSetId")
    }

    companion object {
        fun random(): WorkoutSetId = WorkoutSetId(newUuidV4())
    }
}
