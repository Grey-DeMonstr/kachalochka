package monster.greyde.kachalochka.fakes

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.data.gym.PhotoImages
import monster.greyde.kachalochka.core.data.identity.AccountDeletion
import monster.greyde.kachalochka.core.data.identity.AccountServer
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.identity.GoogleSignIn
import monster.greyde.kachalochka.core.data.identity.InMemoryAccountStorage
import monster.greyde.kachalochka.core.data.identity.OwnerlessRows
import monster.greyde.kachalochka.core.data.identity.PersistedAccountStore
import monster.greyde.kachalochka.core.data.identity.SessionActivation
import monster.greyde.kachalochka.core.data.supabase.SupabaseCredentials
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkId
import monster.greyde.kachalochka.core.domain.gym.MachineLinkRepository
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.PhotoId
import monster.greyde.kachalochka.core.domain.gym.PhotoRepository
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.photoOrder
import monster.greyde.kachalochka.core.domain.gym.visitOrder
import monster.greyde.kachalochka.core.domain.gym.visitRecency
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.measures.Measure
import monster.greyde.kachalochka.core.domain.measures.MeasureId
import monster.greyde.kachalochka.core.domain.measures.MeasureRepository
import monster.greyde.kachalochka.core.domain.measures.Measurement
import monster.greyde.kachalochka.core.domain.measures.MeasurementId
import monster.greyde.kachalochka.core.domain.measures.MeasurementRepository
import monster.greyde.kachalochka.core.domain.measures.measureOrder
import monster.greyde.kachalochka.core.domain.measures.newestPerDay
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.ProfileId
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.friends.InMemoryJoinCodeStore
import monster.greyde.kachalochka.ui.friends.Invite
import monster.greyde.kachalochka.ui.friends.InviteSharing
import monster.greyde.kachalochka.ui.machine.MachineCatalogue
import monster.greyde.kachalochka.ui.photos.PhotoCapture
import monster.greyde.kachalochka.ui.photos.PhotoLaunchers
import monster.greyde.kachalochka.ui.share.TextSharing
import monster.greyde.kachalochka.ui.timer.Ticker
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

class MutableClock(
    var current: Instant,
) : Clock {
    override fun now(): Instant = current
}

/** Ticks only when a test calls [tick], so screens never animate on their own. */
class ManualTicker : Ticker {
    private val ticks = Channel<Unit>(Channel.UNLIMITED)

    fun tick() {
        ticks.trySend(Unit)
    }

    override suspend fun awaitTick() = ticks.receive()
}

class InMemoryMachineRepository : MachineRepository {
    val rows = linkedMapOf<MachineId, Machine>()

    override suspend fun upsert(machine: Machine) {
        rows[machine.id] = machine
    }

    override suspend fun byId(id: MachineId): Machine? = rows[id]

    override suspend fun all(owner: UserId?): List<Machine> =
        rows.values
            .filterNot { it.deleted }
            .filter { it.userId == owner }
            .sortedBy { it.name.lowercase() }

    override suspend fun named(
        owner: UserId?,
        name: String,
    ): Machine? = all(owner).firstOrNull { it.name == name }
}

class InMemoryVisitRepository : VisitRepository {
    val rows = linkedMapOf<VisitId, Visit>()

    /** While set, [all] waits for it, which keeps a reload in flight as long as a test needs. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun upsert(visit: Visit) {
        rows[visit.id] = visit
    }

    override suspend fun byId(id: VisitId): Visit? = rows[id]

    override suspend fun onDay(
        owner: UserId?,
        day: CalendarDay,
    ): Visit? =
        rows.values
            .filter { !it.deleted && it.userId == owner && it.day == day }
            .maxWithOrNull(visitRecency)

    override suspend fun all(owner: UserId?): List<Visit> {
        gate?.await()
        return rows.values
            .filter { !it.deleted && it.userId == owner }
            .sortedByDescending { it.recordedAt }
    }

    override suspend fun undated(owner: UserId?): List<Visit> =
        rows.values.filter { !it.deleted && it.userId == owner && it.day == null }
}

class InMemoryWorkoutSetRepository : WorkoutSetRepository {
    val rows = linkedMapOf<WorkoutSetId, WorkoutSet>()

    /** While set, writes wait for it, keeping a write in flight for as long as a test needs. */
    var gate: CompletableDeferred<Unit>? = null

    /** While set, [forVisit] waits for it, keeping a read in flight as long as a test needs. */
    var readGate: CompletableDeferred<Unit>? = null

    override suspend fun upsert(set: WorkoutSet) {
        gate?.await()
        rows[set.id] = set
    }

    private fun live() = rows.values.filterNot { it.deleted }.sortedBy { it.recordedAt }

    override suspend fun forVisit(visitId: VisitId): List<WorkoutSet> {
        readGate?.await()
        return live().filter { it.visitId == visitId }.sortedWith(visitOrder)
    }

    override suspend fun forMachine(machineId: MachineId) =
        live().filter { it.machineId == machineId }

    override suspend fun latestPerMachine(owner: UserId?) =
        live()
            .filter { it.userId == owner }
            .groupBy { it.machineId }
            .values
            .map { it.last() }
}

class InMemoryProfileRepository : ProfileRepository {
    val rows = linkedMapOf<ProfileId, Profile>()

    override suspend fun upsert(profile: Profile) {
        rows[profile.id] = profile
    }

    override suspend fun byId(id: ProfileId): Profile? = rows[id]

    /** While set, [forOwner] answers with what it read before waiting, as a slow network read. */
    var readGate: CompletableDeferred<Unit>? = null

    override suspend fun forOwner(owner: UserId?): Profile? {
        val found =
            rows.values
                .filter { !it.deleted && it.userId == owner }
                .maxWithOrNull(compareBy<Profile> { it.updatedAt }.thenBy { it.id.value })
        readGate?.await()
        return found
    }
}

class InMemoryMachineLinkRepository : MachineLinkRepository {
    val rows = linkedMapOf<MachineLinkId, MachineLink>()

    override suspend fun upsert(link: MachineLink) {
        rows[link.id] = link
    }

    override suspend fun all(owner: UserId?): List<MachineLink> =
        rows.values.filter { !it.deleted && it.userId == owner }
}

class InMemoryMeasureRepository : MeasureRepository {
    val rows = linkedMapOf<MeasureId, Measure>()

    override suspend fun upsert(measure: Measure) {
        rows[measure.id] = measure
    }

    override suspend fun all(owner: UserId?): List<Measure> =
        rows.values.filter { !it.deleted && it.userId == owner }.sortedWith(measureOrder)

    override suspend fun predefined(owner: UserId?): List<Measure> =
        rows.values.filter { it.userId == owner && it.kind != null }
}

class InMemoryMeasurementRepository : MeasurementRepository {
    val rows = linkedMapOf<MeasurementId, Measurement>()

    override suspend fun upsert(measurement: Measurement) {
        rows[measurement.id] = measurement
    }

    override suspend fun all(owner: UserId?): List<Measurement> =
        newestPerDay(rows.values.filter { it.userId == owner })
}

class InMemoryPhotoRepository : PhotoRepository {
    val rows = linkedMapOf<PhotoId, Photo>()
    val bytes = mutableMapOf<PhotoId, ByteArray>()

    override suspend fun add(
        photo: Photo,
        jpeg: ByteArray,
    ) {
        bytes[photo.id] = jpeg
        rows[photo.id] = photo
    }

    override suspend fun upsert(photo: Photo) {
        rows[photo.id] = photo
    }

    override suspend fun forMachine(machineId: MachineId): List<Photo> =
        rows.values.filter { !it.deleted && it.machineId == machineId }.sortedWith(photoOrder)

    override suspend fun all(owner: UserId?): List<Photo> =
        rows.values.filter { !it.deleted && it.userId == owner }
}

/** Hands [jpeg] over the moment either launcher is tapped, as a camera would once it is done. */
class InstantPhotoCapture(
    val jpeg: ByteArray,
) : PhotoCapture {
    @Composable
    override fun rememberLaunchers(onPhoto: (ByteArray) -> Unit) =
        PhotoLaunchers(takePhoto = { onPhoto(jpeg) }, pickPhoto = { onPhoto(jpeg) })
}

/** The server's side of deleting an account; fails while [offline], as a lost connection would. */
class RecordingAccountServer : AccountServer {
    var offline = false
    val deleted = mutableListOf<UserId>()

    override suspend fun deleteEverything(owner: UserId) {
        if (offline) error("no connection")
        deleted += owner
    }
}

private class QueuedGoogleSignIn : GoogleSignIn {
    val queue = ArrayDeque<AccountSession>()

    override suspend fun signIn(): AccountSession = queue.removeFirst()
}

private class NoOpSessionActivation : SessionActivation {
    override suspend fun activate(session: AccountSession) = Unit

    override suspend fun clear() = Unit
}

private class NoOpOwnerlessRows : OwnerlessRows {
    override suspend fun claim(owner: UserId) = Unit
}

class RecordingSyncTrigger : SyncTrigger {
    var requests = 0

    override val completed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun completePass() {
        completed.tryEmit(Unit)
    }

    override fun request() {
        requests++
    }
}

class RecordingInviteSharing : InviteSharing {
    override var pageAddress: String? = "https://example.test/kachalochka/"
    var notice: String? = "Ссылка скопирована"
    val shared = mutableListOf<Invite>()

    override suspend fun share(invite: Invite): String? {
        shared += invite
        return notice
    }
}

class RecordingTextSharing : TextSharing {
    var notice: String? = "Скопировано"
    val shared = mutableListOf<String>()

    override suspend fun share(text: String): String? {
        shared += text
        return notice
    }
}

class FakeGym(
    now: Instant = Instant.fromEpochSeconds(1_700_000_000),
    val credentials: SupabaseCredentials =
        SupabaseCredentials("https://example.test", "anon-key", "google-client-id"),
) {
    val clock = MutableClock(now)
    val ticker = ManualTicker()
    val machines = InMemoryMachineRepository()
    val visits = InMemoryVisitRepository()
    val sets = InMemoryWorkoutSetRepository()
    val profiles = InMemoryProfileRepository()
    val machineLinks = InMemoryMachineLinkRepository()
    val measures = InMemoryMeasureRepository()
    val measurements = InMemoryMeasurementRepository()
    val photos = InMemoryPhotoRepository()
    val photoImages = PhotoImages { photos.bytes[it.id] }
    val photoCapture = InstantPhotoCapture(byteArrayOf(1, 2, 3))
    private val signIn = QueuedGoogleSignIn()
    val accounts =
        Accounts(
            PersistedAccountStore(InMemoryAccountStorage()),
            signIn,
            NoOpSessionActivation(),
            NoOpOwnerlessRows(),
        )
    val currentUser =
        object : CurrentUser {
            override suspend fun id(): UserId? = accounts.activeId.value
        }
    val friends =
        FakeFriends {
            accounts.accounts.value.firstOrNull { it.userId == accounts.activeId.value }
        }
    val utcOffset = UtcOffset { Duration.ZERO }
    val sync = RecordingSyncTrigger()
    val catalogue = MachineCatalogue(machines, photos, machineLinks, friends, clock, sync)
    val invites = RecordingInviteSharing()
    val texts = RecordingTextSharing()
    val joinCodes = InMemoryJoinCodeStore()
    val accountServer = RecordingAccountServer()
    val deletion = AccountDeletion(accountServer, {}, accounts)
    val today: CalendarDay get() = CalendarDay.of(clock.current, utcOffset.at(clock.current))

    /** Signs [sessions] in through [accounts] in order, then makes [active] the live one. */
    fun withAccounts(
        vararg sessions: AccountSession,
        active: AccountSession,
    ): FakeGym =
        runBlocking {
            sessions.forEach {
                signIn.queue.addLast(it)
                accounts.addAccount()
            }
            accounts.switchTo(active.account.userId)
            this@FakeGym
        }
}
