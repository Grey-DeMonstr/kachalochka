package monster.greyde.kachalochka.core.data.sync

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import monster.greyde.kachalochka.core.data.gym.MACHINE_LINK_TABLE
import monster.greyde.kachalochka.core.data.gym.MACHINE_TABLE
import monster.greyde.kachalochka.core.data.gym.MachineLinkRow
import monster.greyde.kachalochka.core.data.gym.MachineRow
import monster.greyde.kachalochka.core.data.gym.PHOTO_BUCKET
import monster.greyde.kachalochka.core.data.gym.PHOTO_TABLE
import monster.greyde.kachalochka.core.data.gym.PLAN_TABLE
import monster.greyde.kachalochka.core.data.gym.PhotoRow
import monster.greyde.kachalochka.core.data.gym.PlanRow
import monster.greyde.kachalochka.core.data.gym.VISIT_TABLE
import monster.greyde.kachalochka.core.data.gym.VisitRow
import monster.greyde.kachalochka.core.data.gym.WORKOUT_SET_TABLE
import monster.greyde.kachalochka.core.data.gym.WorkoutSetRow
import monster.greyde.kachalochka.core.data.gym.owned
import monster.greyde.kachalochka.core.data.gym.storagePath
import monster.greyde.kachalochka.core.data.measures.MEASUREMENT_TABLE
import monster.greyde.kachalochka.core.data.measures.MEASURE_TABLE
import monster.greyde.kachalochka.core.data.measures.MeasureRow
import monster.greyde.kachalochka.core.data.measures.MeasurementRow
import monster.greyde.kachalochka.core.data.profile.PROFILE_TABLE
import monster.greyde.kachalochka.core.data.profile.ProfileRow
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.Plan
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.measures.Measure
import monster.greyde.kachalochka.core.domain.measures.Measurement
import monster.greyde.kachalochka.core.domain.profile.Profile
import kotlin.time.Instant

private const val PAGE_SIZE = 1000L

/**
 * Talks to PostgREST through the same wire rows the web repositories use, so both platforms write
 * these tables in one format.
 */
class SupabaseSyncGateway(
    private val client: Lazy<SupabaseClient>,
    internal val pageSize: Long = PAGE_SIZE,
) : SyncGateway {
    override suspend fun pushMachine(machine: Machine) {
        client.value.postgrest
            .from(MACHINE_TABLE)
            .upsert(MachineRow.of(machine))
    }

    override suspend fun pushVisit(visit: Visit) {
        client.value.postgrest
            .from(VISIT_TABLE)
            .upsert(VisitRow.of(visit))
    }

    override suspend fun pushSet(set: WorkoutSet) {
        client.value.postgrest
            .from(WORKOUT_SET_TABLE)
            .upsert(WorkoutSetRow.of(set))
    }

    override suspend fun pushProfile(profile: Profile) {
        client.value.postgrest
            .from(PROFILE_TABLE)
            .upsert(ProfileRow.of(profile))
    }

    override suspend fun pushMachineLink(link: MachineLink) {
        client.value.postgrest
            .from(MACHINE_LINK_TABLE)
            .upsert(MachineLinkRow.of(link))
    }

    override suspend fun pushPlan(plan: Plan) {
        client.value.postgrest
            .from(PLAN_TABLE)
            .upsert(PlanRow.of(plan))
    }

    override suspend fun pushMeasure(measure: Measure) {
        client.value.postgrest
            .from(MEASURE_TABLE)
            .upsert(MeasureRow.of(measure))
    }

    override suspend fun pushMeasurement(measurement: Measurement) {
        client.value.postgrest
            .from(MEASUREMENT_TABLE)
            .upsert(MeasurementRow.of(measurement))
    }

    override suspend fun pushPhoto(
        photo: Photo,
        jpeg: ByteArray?,
    ) {
        val path = checkNotNull(photo.storagePath()) { "An unowned photo is never pushed" }
        val bucket = client.value.storage.from(PHOTO_BUCKET)
        if (photo.deleted) {
            bucket.delete(path)
        } else if (jpeg != null) {
            bucket.upload(path, jpeg) {
                upsert = true
                contentType = ContentType.Image.JPEG
            }
        }
        client.value.postgrest
            .from(PHOTO_TABLE)
            .upsert(PhotoRow.of(photo))
    }

    override suspend fun pullMachines(
        owner: UserId,
        since: Instant?,
    ): List<Machine> =
        pullAll<MachineRow>(MACHINE_TABLE, owner, since) { it.updatedAt to it.id }
            .map { it.toMachine() }

    override suspend fun pullVisits(
        owner: UserId,
        since: Instant?,
    ): List<Visit> =
        pullAll<VisitRow>(VISIT_TABLE, owner, since) { it.updatedAt to it.id }
            .map { it.toVisit() }

    override suspend fun pullSets(
        owner: UserId,
        since: Instant?,
    ): List<WorkoutSet> =
        pullAll<WorkoutSetRow>(WORKOUT_SET_TABLE, owner, since) { it.updatedAt to it.id }
            .map { it.toWorkoutSet() }

    override suspend fun pullProfiles(
        owner: UserId,
        since: Instant?,
    ): List<Profile> =
        pullAll<ProfileRow>(PROFILE_TABLE, owner, since) { it.updatedAt to it.id }
            .map { it.toProfile() }

    override suspend fun pullMachineLinks(
        owner: UserId,
        since: Instant?,
    ): List<MachineLink> =
        pullAll<MachineLinkRow>(MACHINE_LINK_TABLE, owner, since) { it.updatedAt to it.id }
            .map { it.toMachineLink() }

    override suspend fun pullMeasures(
        owner: UserId,
        since: Instant?,
    ): List<Measure> =
        pullAll<MeasureRow>(MEASURE_TABLE, owner, since) { it.updatedAt to it.id }
            .map { it.toMeasure() }

    override suspend fun pullMeasurements(
        owner: UserId,
        since: Instant?,
    ): List<Measurement> =
        pullAll<MeasurementRow>(MEASUREMENT_TABLE, owner, since) { it.updatedAt to it.id }
            .map { it.toMeasurement() }

    override suspend fun pullPhotos(
        owner: UserId,
        since: Instant?,
    ): List<Photo> =
        pullAll<PhotoRow>(PHOTO_TABLE, owner, since) { it.updatedAt to it.id }
            .map { it.toPhoto() }

    override suspend fun pullPlans(
        owner: UserId,
        since: Instant?,
    ): List<Plan> =
        pullAll<PlanRow>(PLAN_TABLE, owner, since) { it.updatedAt to it.id }
            .map { it.toPlan() }

    // Each page starts after the previous page's last (updated_at, id), so a row another device
    // edits between two fetches only moves later in the order and never makes the pull skip one.
    // The cursor is taken from the server's response so no reformatting can lose precision the
    // server wouldn't accept back.
    private suspend inline fun <reified R : Any> pullAll(
        table: String,
        owner: UserId,
        since: Instant?,
        key: (R) -> Pair<String, String>,
    ): List<R> {
        val rows = mutableListOf<R>()
        var after: Pair<String, String>? = null
        do {
            val page =
                client.value.postgrest
                    .from(table)
                    .select {
                        filter {
                            owned(owner)
                            if (since != null) gt("updated_at", since.toString())
                            after?.let { (updatedAt, id) ->
                                or {
                                    gt("updated_at", updatedAt)
                                    and {
                                        eq("updated_at", updatedAt)
                                        gt("id", id)
                                    }
                                }
                            }
                        }
                        order("updated_at", Order.ASCENDING)
                        order("id", Order.ASCENDING)
                        limit(pageSize)
                    }.decodeList<R>()
            rows += page
            after = page.lastOrNull()?.let(key)
        } while (page.isNotEmpty())
        return rows
    }
}
