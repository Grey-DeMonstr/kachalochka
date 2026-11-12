package monster.greyde.kachalochka.core.data.friends

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.Json
import monster.greyde.kachalochka.core.data.gym.MachineLinkRow
import monster.greyde.kachalochka.core.data.gym.MachinePeaksRow
import monster.greyde.kachalochka.core.data.gym.MachineRow
import monster.greyde.kachalochka.core.data.gym.PhotoRow
import monster.greyde.kachalochka.core.data.gym.VisitRow
import monster.greyde.kachalochka.core.data.gym.WorkoutSetRow
import monster.greyde.kachalochka.core.data.gym.photoIdOf
import monster.greyde.kachalochka.core.domain.friends.Friend
import monster.greyde.kachalochka.core.domain.friends.FriendMachine
import monster.greyde.kachalochka.core.domain.friends.FriendVisit
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.friends.GroupGym
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachinePeaks
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.identity.Avatar
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** How long a kept answer stays when nobody reads it again. */
val KEPT_FOR: Duration = 31.days

/** Friends' answers kept on the device, as JSON text, for each account and request. */
interface FriendAnswers {
    suspend fun answer(
        account: UserId,
        request: String,
    ): String?

    /** Also forgets every answer kept more than [KEPT_FOR] before [at]. */
    suspend fun keep(
        account: UserId,
        request: String,
        answer: String,
        at: Instant,
    )

    suspend fun keepOnly(listed: List<UserId>)
}

/** The web keeps nothing on the device. */
object NoFriendAnswers : FriendAnswers {
    override suspend fun answer(
        account: UserId,
        request: String,
    ): String? = null

    override suspend fun keep(
        account: UserId,
        request: String,
        answer: String,
        at: Instant,
    ) = Unit

    override suspend fun keepOnly(listed: List<UserId>) = Unit
}

/** Inside it, a [KeptFriends] read gives the kept answer and fails when there is none. */
object KeptOnly : CoroutineContext.Element, CoroutineContext.Key<KeptOnly> {
    override val key: CoroutineContext.Key<*> get() = this
}

suspend fun <T> keptOnly(read: suspend () -> T): T = withContext(KeptOnly) { read() }

/**
 * Keeps the answers the screens show first, for the [active] account. A visit window replaces the
 * kept visits it covers, so a new day finds the visits the earlier windows read.
 */
class KeptFriends(
    private val inner: FriendsRepository,
    private val answers: FriendAnswers,
    private val active: () -> UserId?,
    private val clock: Clock,
) : FriendsRepository by inner {
    override suspend fun visits(member: UserId): List<Visit> =
        kept("visits/${member.value}", VISITS) { inner.visits(member) }

    override suspend fun sets(visit: Visit): List<WorkoutSet> =
        kept("sets/${visit.id.value}", SETS) { inner.sets(visit) }

    override suspend fun machines(member: UserId): List<Machine> =
        kept("machines/${member.value}", MACHINES) { inner.machines(member) }

    override suspend fun mates(viewer: UserId): List<Friend> =
        kept("mates/${viewer.value}", FRIENDS) { inner.mates(viewer) }

    override suspend fun groupGym(viewer: UserId): GroupGym =
        kept("groupGym/${viewer.value}", GYM) { inner.groupGym(viewer) }

    override suspend fun oldestGroupVisit(viewer: UserId): Visit? =
        kept("oldestGroupVisit/${viewer.value}", OLDEST) { inner.oldestGroupVisit(viewer) }

    override suspend fun groupLinks(viewer: UserId): List<MachineLink> =
        kept("groupLinks/${viewer.value}", LINKS) { inner.groupLinks(viewer) }

    override suspend fun groupPeaks(viewer: UserId): List<MachinePeaks> =
        kept("groupPeaks/${viewer.value}", PEAKS) { inner.groupPeaks(viewer) }

    override suspend fun groupVisits(
        viewer: UserId,
        from: CalendarDay,
        to: CalendarDay,
    ): List<FriendVisit> {
        val request = "groupVisits/${viewer.value}"
        val account = active()
        if (currentCoroutineContext()[KeptOnly] != null) {
            return keptAnswer(account, request, WINDOWS).filter { it.visit.within(from, to) }
        }
        val fresh = inner.groupVisits(viewer, from, to)
        if (account == null) return fresh
        val earlier = answers.answer(account, request)?.let { WINDOWS.read(it) }.orEmpty()
        val since = clock.now() - KEPT_FOR
        val all = earlier.filterNot { it.visit.within(from, to) } + fresh
        keep(account, request, WINDOWS, all.filter { it.visit.recordedAt >= since })
        return fresh
    }

    private suspend fun <D, W> kept(
        request: String,
        form: Form<D, W>,
        read: suspend () -> D,
    ): D {
        val account = active()
        if (currentCoroutineContext()[KeptOnly] != null) return keptAnswer(account, request, form)
        return read().also { if (account != null) keep(account, request, form, it) }
    }

    private suspend fun <D, W> keptAnswer(
        account: UserId?,
        request: String,
        form: Form<D, W>,
    ): D {
        val text = account?.let { answers.answer(it, request) }
        return form.read(checkNotNull(text) { "Nothing kept for $request" })
    }

    private suspend fun <D, W> keep(
        account: UserId,
        request: String,
        form: Form<D, W>,
        answer: D,
    ) = answers.keep(account, request, form.write(answer), clock.now())
}

// The server's groupVisits filter: the visit's day, else a recording within a day of the window.
private fun Visit.within(
    from: CalendarDay,
    to: CalendarDay,
): Boolean {
    val day =
        day ?: return recordedAt >= from.plusDays(-1).at(0L, Duration.ZERO) &&
            recordedAt < to.plusDays(2).at(0L, Duration.ZERO)
    return day in from..to
}

/** How a domain answer [D] is kept as the JSON of its wire form [W]. */
private class Form<D, W>(
    private val serializer: KSerializer<W>,
    private val toWire: (D) -> W,
    private val toDomain: (W) -> D,
) {
    fun write(answer: D): String = Json.encodeToString(serializer, toWire(answer))

    fun read(text: String): D = toDomain(Json.decodeFromString(serializer, text))
}

private fun <D, W> listForm(
    serializer: KSerializer<W>,
    toWire: (D) -> W,
    toDomain: (W) -> D,
) = Form(ListSerializer(serializer), { it.map(toWire) }, { it.map(toDomain) })

@Serializable
private class KeptFriend(
    val id: String,
    val name: String,
    val photo: String?,
    val picture: String?,
) {
    fun toFriend() = Friend(UserId(id), name, Avatar(photoIdOf(photo), picture))

    companion object {
        fun of(friend: Friend) =
            KeptFriend(
                friend.userId.value,
                friend.displayName,
                friend.avatar.photo?.value,
                friend.avatar.picture,
            )
    }
}

@Serializable
private class KeptMachine(
    val row: MachineRow,
    val tags: Set<String>,
) {
    fun toMachine() = row.toMachine().copy(tags = tags)

    companion object {
        fun of(machine: Machine) = KeptMachine(MachineRow.of(machine), machine.tags)
    }
}

@Serializable
private class KeptFriendVisit(
    val friend: KeptFriend,
    val visit: VisitRow,
)

@Serializable
private class KeptFriendMachine(
    val machine: KeptMachine,
    val owner: KeptFriend,
)

@Serializable
private class KeptGym(
    val machines: List<KeptFriendMachine>,
    val links: List<MachineLinkRow>,
    val photos: List<PhotoRow>,
)

private val VISITS = listForm(VisitRow.serializer(), VisitRow::of, VisitRow::toVisit)

private val SETS =
    listForm(WorkoutSetRow.serializer(), WorkoutSetRow::of, WorkoutSetRow::toWorkoutSet)

private val MACHINES = listForm(KeptMachine.serializer(), KeptMachine::of, KeptMachine::toMachine)

private val FRIENDS = listForm(KeptFriend.serializer(), KeptFriend::of, KeptFriend::toFriend)

private val OLDEST =
    Form<Visit?, VisitRow?>(VisitRow.serializer().nullable, {
        it?.let(VisitRow::of)
    }, { it?.toVisit() })

private val LINKS =
    listForm(MachineLinkRow.serializer(), MachineLinkRow::of, MachineLinkRow::toMachineLink)

private val WINDOWS =
    listForm(
        KeptFriendVisit.serializer(),
        { it: FriendVisit -> KeptFriendVisit(KeptFriend.of(it.friend), VisitRow.of(it.visit)) },
        { FriendVisit(it.friend.toFriend(), it.visit.toVisit()) },
    )

private val GYM =
    Form(
        KeptGym.serializer(),
        { gym: GroupGym ->
            KeptGym(
                gym.machines.map {
                    KeptFriendMachine(KeptMachine.of(it.machine), KeptFriend.of(it.owner))
                },
                gym.links.map(MachineLinkRow::of),
                gym.photos.map(PhotoRow::of),
            )
        },
        { kept ->
            GroupGym(
                kept.machines.map { FriendMachine(it.machine.toMachine(), it.owner.toFriend()) },
                kept.links.map { it.toMachineLink() },
                kept.photos.map { it.toPhoto() },
            )
        },
    )

private val PEAKS =
    listForm(MachinePeaksRow.serializer(), MachinePeaksRow::of, MachinePeaksRow::toPeaks)
