package monster.greyde.kachalochka.core.data.sync

import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.measures.Measure
import monster.greyde.kachalochka.core.domain.measures.Measurement
import monster.greyde.kachalochka.core.domain.profile.Profile
import kotlin.time.Instant

/** The whole of what a pass needs from the server, so the algorithm is testable without one. */
interface SyncGateway {
    suspend fun pushMachine(machine: Machine)

    suspend fun pushVisit(visit: Visit)

    suspend fun pushSet(set: WorkoutSet)

    suspend fun pushProfile(profile: Profile)

    suspend fun pushMachineLink(link: MachineLink)

    suspend fun pushMeasure(measure: Measure)

    suspend fun pushMeasurement(measurement: Measurement)

    /** A live photo uploads [jpeg] first, when given; a deleted one removes its bytes first. */
    suspend fun pushPhoto(
        photo: Photo,
        jpeg: ByteArray?,
    )

    suspend fun pullMachines(
        owner: UserId,
        since: Instant?,
    ): List<Machine>

    suspend fun pullVisits(
        owner: UserId,
        since: Instant?,
    ): List<Visit>

    suspend fun pullSets(
        owner: UserId,
        since: Instant?,
    ): List<WorkoutSet>

    suspend fun pullProfiles(
        owner: UserId,
        since: Instant?,
    ): List<Profile>

    suspend fun pullMachineLinks(
        owner: UserId,
        since: Instant?,
    ): List<MachineLink>

    suspend fun pullMeasures(
        owner: UserId,
        since: Instant?,
    ): List<Measure>

    suspend fun pullMeasurements(
        owner: UserId,
        since: Instant?,
    ): List<Measurement>

    suspend fun pullPhotos(
        owner: UserId,
        since: Instant?,
    ): List<Photo>
}
