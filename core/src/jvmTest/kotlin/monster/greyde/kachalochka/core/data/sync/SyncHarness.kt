package monster.greyde.kachalochka.core.data.sync

import kotlinx.coroutines.Dispatchers
import monster.greyde.kachalochka.core.data.db.inMemoryDatabase
import monster.greyde.kachalochka.core.data.gym.LocalMachineLinkRepository
import monster.greyde.kachalochka.core.data.gym.LocalMachineRepository
import monster.greyde.kachalochka.core.data.gym.LocalVisitRepository
import monster.greyde.kachalochka.core.data.gym.LocalWorkoutSetRepository
import monster.greyde.kachalochka.core.data.gym.VisitNormalizer
import monster.greyde.kachalochka.core.data.measures.LocalMeasureRepository
import monster.greyde.kachalochka.core.data.measures.LocalMeasurementRepository
import monster.greyde.kachalochka.core.data.profile.LocalProfileRepository
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkId
import monster.greyde.kachalochka.core.domain.gym.T0
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.identity.newUuidV4
import monster.greyde.kachalochka.core.domain.measures.Measure
import monster.greyde.kachalochka.core.domain.measures.MeasureId
import monster.greyde.kachalochka.core.domain.measures.Measurement
import monster.greyde.kachalochka.core.domain.measures.MeasurementId
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.ProfileId
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

internal val IVAN = UserId("11111111-1111-4111-8111-111111111111")
internal val MISHA = UserId("22222222-2222-4222-8222-222222222222")

internal fun ownedVisit(
    owner: UserId,
    updatedAt: Instant = T0,
) = Visit(VisitId.random(), owner, CalendarDay.of(T0, Duration.ZERO), T0, updatedAt, false)

internal fun ownedPress(
    owner: UserId,
    updatedAt: Instant = T0,
) = Machine.new("Жим ногами", owner, updatedAt)

internal fun ownedSet(
    owner: UserId,
    visit: Visit,
    machine: Machine,
    updatedAt: Instant = T0,
) = WorkoutSet(
    WorkoutSetId.random(),
    owner,
    visit.id,
    machine.id,
    70.0,
    10,
    0,
    T0,
    updatedAt,
    false,
)

internal fun ownedProfile(
    owner: UserId,
    updatedAt: Instant = T0,
) = Profile(ProfileId(newUuidV4()), owner, "Иван", updatedAt, false)

internal fun ownedLink(
    owner: UserId,
    updatedAt: Instant = T0,
) = MachineLink(
    MachineLinkId.random(),
    owner,
    MachineId.random(),
    MachineId.random(),
    updatedAt,
    false,
)

internal fun ownedMeasure(
    owner: UserId,
    updatedAt: Instant = T0,
) = Measure(MeasureId.random(), owner, "Шея", "см", null, 0, updatedAt, false)

internal fun ownedMeasurement(
    owner: UserId,
    measure: Measure,
    updatedAt: Instant = T0,
) = Measurement(
    MeasurementId.random(),
    owner,
    measure.id,
    CalendarDay.of(T0, Duration.ZERO),
    38.5,
    updatedAt,
    false,
)

internal class SyncHarness {
    val database = inMemoryDatabase()
    val outbox = OutboxDao(database)
    val visits = LocalVisitRepository(database, outbox, Dispatchers.Unconfined)
    val machines = LocalMachineRepository(database, outbox, Dispatchers.Unconfined)
    val sets = LocalWorkoutSetRepository(database, outbox, Dispatchers.Unconfined)
    val profiles = LocalProfileRepository(database, outbox, Dispatchers.Unconfined)
    val links = LocalMachineLinkRepository(database, outbox, Dispatchers.Unconfined)
    val measures = LocalMeasureRepository(database, outbox, Dispatchers.Unconfined)
    val measurements = LocalMeasurementRepository(database, outbox, Dispatchers.Unconfined)
    val watermarks = SyncWatermarks(database)
    val session = SyncSession()
    val gateway = FakeSyncGateway(session)
    val pass =
        SyncPass(
            outbox,
            LocalSyncRows(database),
            watermarks,
            gateway,
            session,
            Dispatchers.Unconfined,
        )
    val clock =
        object : Clock {
            override fun now(): Instant = T0 + 1.hours
        }
    val normalizer = VisitNormalizer(visits, sets, clock) { Duration.ZERO }
    val failures = mutableListOf<Throwable>()
}
