package monster.greyde.kachalochka.core.data.sync

import monster.greyde.kachalochka.core.data.db.KachalochkaDatabase
import monster.greyde.kachalochka.core.data.gym.machineIdsText
import monster.greyde.kachalochka.core.data.gym.machineLinkOf
import monster.greyde.kachalochka.core.data.gym.machineOf
import monster.greyde.kachalochka.core.data.gym.photoOf
import monster.greyde.kachalochka.core.data.gym.tagsText
import monster.greyde.kachalochka.core.data.gym.visitOf
import monster.greyde.kachalochka.core.data.gym.wireName
import monster.greyde.kachalochka.core.data.gym.workoutSetOf
import monster.greyde.kachalochka.core.data.gym.write
import monster.greyde.kachalochka.core.data.measures.measureOf
import monster.greyde.kachalochka.core.data.measures.measurementOf
import monster.greyde.kachalochka.core.data.measures.write
import monster.greyde.kachalochka.core.data.profile.profileOf
import monster.greyde.kachalochka.core.data.profile.write
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.measures.Measure
import monster.greyde.kachalochka.core.domain.measures.Measurement
import monster.greyde.kachalochka.core.domain.profile.Profile

/** Rows as the sync pass sees them: read and written without ever touching the outbox. */
class LocalSyncRows(
    private val database: KachalochkaDatabase,
) {
    fun machine(id: String): Machine? =
        database.machineQueries.byId(id, ::machineOf).executeAsOneOrNull()

    fun visit(id: String): Visit? = database.visitQueries.byId(id, ::visitOf).executeAsOneOrNull()

    fun set(id: String): WorkoutSet? =
        database.workoutSetQueries.byId(id, ::workoutSetOf).executeAsOneOrNull()

    fun profile(id: String): Profile? =
        database.profileQueries.byId(id, ::profileOf).executeAsOneOrNull()

    fun machineLink(id: String): MachineLink? =
        database.machineLinkQueries.byId(id, ::machineLinkOf).executeAsOneOrNull()

    fun measure(id: String): Measure? =
        database.measureQueries.byId(id, ::measureOf).executeAsOneOrNull()

    fun measurement(id: String): Measurement? =
        database.measurementQueries.byId(id, ::measurementOf).executeAsOneOrNull()

    fun photo(id: String): Photo? = database.photoQueries.byId(id, ::photoOf).executeAsOneOrNull()

    fun writeMachine(machine: Machine) =
        database.machineQueries.upsert(
            machine.id.value,
            machine.userId?.value,
            machine.name,
            machine.setupNote,
            machine.weightMode.wireName(),
            machine.platformWeight,
            machine.platformIncluded,
            machine.unit.wireName(),
            machine.weightStep,
            machine.updatedAt,
            machine.deleted,
            machine.unitLabel,
            tagsText(machine.tags),
        )

    fun writeVisit(visit: Visit) =
        database.visitQueries.upsert(
            visit.id.value,
            visit.userId?.value,
            visit.day,
            visit.recordedAt,
            visit.updatedAt,
            visit.deleted,
            machineIdsText(visit.planned),
        )

    fun writeSet(set: WorkoutSet) =
        database.workoutSetQueries.upsert(
            set.id.value,
            set.userId?.value,
            set.visitId.value,
            set.machineId.value,
            set.weight,
            set.reps.toLong(),
            set.recordedAt,
            set.updatedAt,
            set.deleted,
            set.position.toLong(),
            set.comment,
        )

    fun writeProfile(profile: Profile) = database.profileQueries.write(profile)

    fun writeMachineLink(link: MachineLink) = database.machineLinkQueries.write(link)

    fun writeMeasure(measure: Measure) = database.measureQueries.write(measure)

    fun writeMeasurement(measurement: Measurement) = database.measurementQueries.write(measurement)

    fun writePhoto(photo: Photo) = database.photoQueries.write(photo)

    fun transaction(body: () -> Unit) = database.transaction { body() }
}
