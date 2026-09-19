package monster.greyde.kachalochka.core.domain.gym

import kotlin.time.Instant

internal val T0: Instant = Instant.fromEpochSeconds(1_700_000_000)
internal val VISIT_A = VisitId("0a000000-0000-4000-8000-00000000000a")
internal val VISIT_B = VisitId("0b000000-0000-4000-8000-00000000000b")
internal val VISIT_C = VisitId("0c000000-0000-4000-8000-00000000000c")
internal val PRESS = MachineId("0d000000-0000-4000-8000-00000000000d")
internal val ROW = MachineId("0e000000-0000-4000-8000-00000000000e")

internal fun machine(
    id: MachineId = PRESS,
    name: String = "Жим ногами",
    platformWeight: Double = 0.0,
    platformIncluded: Boolean = false,
) = Machine.new(name, null, T0).copy(
    id = id,
    platformWeight = platformWeight,
    platformIncluded = platformIncluded,
)

internal fun set(
    visit: VisitId,
    weight: Double,
    reps: Int = 10,
    atSeconds: Long,
    machine: MachineId = PRESS,
    deleted: Boolean = false,
) = WorkoutSet(
    id = WorkoutSetId.random(),
    userId = null,
    visitId = visit,
    machineId = machine,
    weight = weight,
    reps = reps,
    recordedAt = Instant.fromEpochSeconds(T0.epochSeconds + atSeconds),
    updatedAt = T0,
    deleted = deleted,
)
