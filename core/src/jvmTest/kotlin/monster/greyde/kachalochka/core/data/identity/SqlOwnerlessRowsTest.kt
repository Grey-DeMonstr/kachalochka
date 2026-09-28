package monster.greyde.kachalochka.core.data.identity

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.db.inMemoryDatabase
import monster.greyde.kachalochka.core.data.gym.InMemoryPhotoFiles
import monster.greyde.kachalochka.core.data.gym.LocalMachineLinkRepository
import monster.greyde.kachalochka.core.data.gym.LocalMachineRepository
import monster.greyde.kachalochka.core.data.gym.LocalPhotoRepository
import monster.greyde.kachalochka.core.data.gym.LocalVisitRepository
import monster.greyde.kachalochka.core.data.gym.LocalWorkoutSetRepository
import monster.greyde.kachalochka.core.data.gym.MACHINE_LINK_TABLE
import monster.greyde.kachalochka.core.data.gym.PHOTO_TABLE
import monster.greyde.kachalochka.core.data.measures.LocalMeasureRepository
import monster.greyde.kachalochka.core.data.measures.LocalMeasurementRepository
import monster.greyde.kachalochka.core.data.measures.MEASUREMENT_TABLE
import monster.greyde.kachalochka.core.data.measures.MEASURE_TABLE
import monster.greyde.kachalochka.core.data.profile.LocalProfileRepository
import monster.greyde.kachalochka.core.data.profile.PROFILE_TABLE
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkId
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.measures.Measure
import monster.greyde.kachalochka.core.domain.measures.MeasureId
import monster.greyde.kachalochka.core.domain.measures.Measurement
import monster.greyde.kachalochka.core.domain.measures.MeasurementId
import monster.greyde.kachalochka.core.domain.profile.Profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class SqlOwnerlessRowsTest {
    private val database = inMemoryDatabase()
    private val outbox = OutboxDao(database)
    private val visits = LocalVisitRepository(database, outbox, Dispatchers.Unconfined)
    private val machines = LocalMachineRepository(database, outbox, Dispatchers.Unconfined)
    private val sets = LocalWorkoutSetRepository(database, outbox, Dispatchers.Unconfined)
    private val profiles = LocalProfileRepository(database, outbox, Dispatchers.Unconfined)
    private val links = LocalMachineLinkRepository(database, outbox, Dispatchers.Unconfined)
    private val measures = LocalMeasureRepository(database, outbox, Dispatchers.Unconfined)
    private val values = LocalMeasurementRepository(database, outbox, Dispatchers.Unconfined)
    private val photos =
        LocalPhotoRepository(database, outbox, InMemoryPhotoFiles(), Dispatchers.Unconfined)

    private val owner = UserId("11111111-1111-4111-8111-111111111111")
    private val stranger = UserId("22222222-2222-4222-8222-222222222222")
    private val t0 = Instant.fromEpochSeconds(1_700_000_000)

    // kotlin.time.Clock is a plain interface, so a fixed one is an object, not a lambda.
    private val clock =
        object : Clock {
            override fun now(): Instant = t0 + 1.hours
        }

    private val rows = SqlOwnerlessRows(database, outbox, clock, Dispatchers.Unconfined)

    private fun visit(userId: UserId?) =
        Visit(VisitId.random(), userId, CalendarDay.of(t0, Duration.ZERO), t0, t0, false)

    @Test
    fun claiming_stamps_unowned_rows_and_leaves_other_owners_alone() =
        runTest {
            val unowned = visit(null)
            val theirs = visit(stranger)
            visits.upsert(unowned)
            visits.upsert(theirs)

            rows.claim(owner)

            assertEquals(owner, visits.byId(unowned.id)?.userId)
            assertEquals(stranger, visits.byId(theirs.id)?.userId)
        }

    @Test
    fun a_claimed_row_is_enqueued_for_the_next_sync_pass() =
        runTest {
            val unowned = visit(null)
            visits.upsert(unowned)

            rows.claim(owner)

            assertTrue(outbox.pending().any { it.rowId == unowned.id.value })
        }

    @Test
    fun claiming_covers_machines_and_sets_as_well_as_visits() =
        runTest {
            val unowned = visit(null)
            val press = Machine.new("Жим ногами", null, t0)
            val recorded =
                WorkoutSet(
                    WorkoutSetId.random(),
                    null,
                    unowned.id,
                    press.id,
                    70.0,
                    10,
                    0,
                    t0,
                    t0,
                    false,
                )
            visits.upsert(unowned)
            machines.upsert(press)
            sets.upsert(recorded)

            rows.claim(owner)

            assertEquals(owner, machines.byId(press.id)?.userId)
            assertEquals(owner, sets.forVisit(unowned.id).single().userId)
        }

    @Test
    fun claiming_covers_the_anonymous_profile_and_enqueues_it() =
        runTest {
            val anonymous = Profile.new(null, t0)
            profiles.upsert(anonymous)

            rows.claim(owner)

            assertEquals(owner, profiles.byId(anonymous.id)?.userId)
            assertTrue(
                outbox.pending().any {
                    it.tableName == PROFILE_TABLE && it.rowId == anonymous.id.value
                },
            )
        }

    @Test
    fun an_older_anonymous_profile_stays_older_than_the_account_s_pulled_one() =
        runTest {
            val anonymous = Profile.new(null, t0).copy(displayName = "Аноним")
            val pulled = Profile.new(owner, t0 + 0.5.hours).copy(displayName = "Ванёк")
            profiles.upsert(anonymous)
            profiles.upsert(pulled)

            rows.claim(owner)

            assertEquals(t0, profiles.byId(anonymous.id)?.updatedAt)
            assertEquals(pulled, profiles.forOwner(owner))
            assertTrue(
                outbox.pending().any {
                    it.tableName == PROFILE_TABLE && it.rowId == anonymous.id.value
                },
            )
        }

    @Test
    fun claiming_stamps_an_anonymous_link_and_enqueues_it() =
        runTest {
            val anonymous =
                MachineLink(
                    MachineLinkId.random(),
                    null,
                    MachineId.random(),
                    MachineId.random(),
                    t0,
                    false,
                )
            links.upsert(anonymous)

            rows.claim(owner)

            assertEquals(
                listOf(anonymous.copy(userId = owner, updatedAt = clock.now())),
                links.all(owner),
            )
            assertTrue(
                outbox.pending().any {
                    it.tableName == MACHINE_LINK_TABLE && it.rowId == anonymous.id.value
                },
            )
        }

    @Test
    fun claiming_stamps_anonymous_measures_and_their_values_and_enqueues_them() =
        runTest {
            val neck = Measure(MeasureId.random(), null, "Шея", "см", null, 0, t0, false)
            val monday =
                Measurement(
                    MeasurementId.random(),
                    null,
                    neck.id,
                    CalendarDay(2026, 9, 21),
                    38.5,
                    t0,
                    false,
                )
            measures.upsert(neck)
            values.upsert(monday)

            rows.claim(owner)

            assertEquals(
                listOf(neck.copy(userId = owner, updatedAt = clock.now())),
                measures.all(owner),
            )
            assertEquals(
                listOf(monday.copy(userId = owner, updatedAt = clock.now())),
                values.all(owner),
            )
            assertEquals(
                setOf(MEASURE_TABLE to neck.id.value, MEASUREMENT_TABLE to monday.id.value),
                outbox
                    .pending()
                    .map { it.tableName to it.rowId }
                    .filter { it.first == MEASURE_TABLE || it.first == MEASUREMENT_TABLE }
                    .toSet(),
            )
        }

    @Test
    fun claiming_stamps_an_anonymous_photo_and_enqueues_it() =
        runTest {
            val anonymous = Photo.new(MachineId.random(), null, t0)
            photos.add(anonymous, byteArrayOf(1))

            rows.claim(owner)

            assertEquals(
                listOf(anonymous.copy(userId = owner, updatedAt = clock.now())),
                photos.forMachine(anonymous.machineId),
            )
            assertTrue(
                outbox.pending().any {
                    it.tableName == PHOTO_TABLE && it.rowId == anonymous.id.value
                },
            )
        }
}
