package monster.greyde.kachalochka.core.data.sync

import monster.greyde.kachalochka.core.data.gym.MACHINE_LINK_TABLE
import monster.greyde.kachalochka.core.data.gym.MACHINE_TABLE
import monster.greyde.kachalochka.core.data.gym.PHOTO_TABLE
import monster.greyde.kachalochka.core.data.gym.VISIT_TABLE
import monster.greyde.kachalochka.core.data.gym.WORKOUT_SET_TABLE
import monster.greyde.kachalochka.core.data.measures.MEASUREMENT_TABLE
import monster.greyde.kachalochka.core.data.measures.MEASURE_TABLE
import monster.greyde.kachalochka.core.data.profile.PROFILE_TABLE
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.PhotoId
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.measures.Measure
import monster.greyde.kachalochka.core.domain.measures.Measurement
import monster.greyde.kachalochka.core.domain.profile.Profile
import kotlin.time.Instant

class FakeSyncGateway(
    private val session: SyncSession,
) : SyncGateway {
    /** `"table:id"` in push order. */
    val pushed = mutableListOf<String>()

    /** The account the pass was on at each push, parallel to [pushed]. */
    val pushedAs = mutableListOf<UserId?>()

    val pulledSince = mutableListOf<Pair<UserId, Instant?>>()
    var machinesToPull: List<Machine> = emptyList()
    var visitsToPull: List<Visit> = emptyList()
    var setsToPull: List<WorkoutSet> = emptyList()
    var profilesToPull: List<Profile> = emptyList()
    var linksToPull: List<MachineLink> = emptyList()
    var measuresToPull: List<Measure> = emptyList()
    var measurementsToPull: List<Measurement> = emptyList()
    var photosToPull: List<Photo> = emptyList()

    /** The bytes each live photo push carried. */
    val uploaded = mutableMapOf<PhotoId, ByteArray>()

    /** Deleted photos whose bytes were removed from storage, in order. */
    val removedFromStorage = mutableListOf<PhotoId>()

    /** Makes every push to that table fail, as a lost connection would. */
    var failing: String? = null

    var pullFails = false

    override suspend fun pushMachine(machine: Machine) = record(MACHINE_TABLE, machine.id.value)

    override suspend fun pushVisit(visit: Visit) = record(VISIT_TABLE, visit.id.value)

    override suspend fun pushSet(set: WorkoutSet) = record(WORKOUT_SET_TABLE, set.id.value)

    override suspend fun pushProfile(profile: Profile) = record(PROFILE_TABLE, profile.id.value)

    override suspend fun pushMachineLink(link: MachineLink) =
        record(MACHINE_LINK_TABLE, link.id.value)

    override suspend fun pushMeasure(measure: Measure) = record(MEASURE_TABLE, measure.id.value)

    override suspend fun pushMeasurement(measurement: Measurement) =
        record(MEASUREMENT_TABLE, measurement.id.value)

    override suspend fun pushPhoto(
        photo: Photo,
        jpeg: ByteArray?,
    ) {
        if (PHOTO_TABLE == failing) error("no connection")
        if (photo.deleted) removedFromStorage += photo.id
        jpeg?.let { uploaded[photo.id] = it }
        record(PHOTO_TABLE, photo.id.value)
    }

    override suspend fun pullMachines(
        owner: UserId,
        since: Instant?,
    ) = pull(owner, since) { machinesToPull.filter { it.userId == owner } }

    override suspend fun pullVisits(
        owner: UserId,
        since: Instant?,
    ) = pull(owner, since) { visitsToPull.filter { it.userId == owner } }

    override suspend fun pullSets(
        owner: UserId,
        since: Instant?,
    ) = pull(owner, since) { setsToPull.filter { it.userId == owner } }

    override suspend fun pullProfiles(
        owner: UserId,
        since: Instant?,
    ) = pull(owner, since) { profilesToPull.filter { it.userId == owner } }

    override suspend fun pullMachineLinks(
        owner: UserId,
        since: Instant?,
    ) = pull(owner, since) { linksToPull.filter { it.userId == owner } }

    override suspend fun pullMeasures(
        owner: UserId,
        since: Instant?,
    ) = pull(owner, since) { measuresToPull.filter { it.userId == owner } }

    override suspend fun pullMeasurements(
        owner: UserId,
        since: Instant?,
    ) = pull(owner, since) { measurementsToPull.filter { it.userId == owner } }

    override suspend fun pullPhotos(
        owner: UserId,
        since: Instant?,
    ) = pull(owner, since) { photosToPull.filter { it.userId == owner } }

    private fun <T> pull(
        owner: UserId,
        since: Instant?,
        rows: () -> List<T>,
    ): List<T> {
        if (pullFails) error("no connection")
        pulledSince += owner to since
        return rows()
    }

    private fun record(
        table: String,
        id: String,
    ) {
        if (table == failing) error("no connection")
        pushed += "$table:$id"
        pushedAs += session.owner
    }
}
