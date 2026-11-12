package monster.greyde.kachalochka.core.data.friends

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.db.inMemoryDatabase
import monster.greyde.kachalochka.core.domain.friends.Friend
import monster.greyde.kachalochka.core.domain.friends.FriendMachine
import monster.greyde.kachalochka.core.domain.friends.FriendVisit
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.friends.GroupGym
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.DayBest
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkId
import monster.greyde.kachalochka.core.domain.gym.MachinePeaks
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.PhotoId
import monster.greyde.kachalochka.core.domain.gym.SetPeak
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.identity.Avatar
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

private val IVAN = UserId("11111111-1111-4111-8111-111111111111")
private val MISHA = UserId("22222222-2222-4222-8222-222222222222")
private val OLEG =
    Friend(
        UserId("33333333-3333-4333-8333-333333333333"),
        "Oleg",
        Avatar(PhotoId.random(), "https://example.test/oleg.png"),
    )

/** A server that gives what a test puts in it, and fails every other read. */
private class Server :
    FriendsRepository by SupabaseFriendsRepository(
        lazy {
            error("offline")
        },
        Clock.System,
    ) {
    var offline = false
    var reads = 0
    var visits = emptyList<Visit>()
    var sets = emptyList<WorkoutSet>()
    var machines = emptyList<Machine>()
    var mates = emptyList<Friend>()
    var gym = GroupGym(emptyList(), emptyList(), emptyList())
    var windowVisits = emptyList<FriendVisit>()
    var oldest: Visit? = null
    var links = emptyList<MachineLink>()
    var peaks = emptyList<MachinePeaks>()

    private fun <T> answer(value: T): T {
        reads++
        check(!offline) { "offline" }
        return value
    }

    override suspend fun visits(member: UserId) = answer(visits)

    override suspend fun sets(visit: Visit) = answer(sets)

    override suspend fun machines(member: UserId) = answer(machines)

    override suspend fun mates(viewer: UserId) = answer(mates)

    override suspend fun groupGym(viewer: UserId) = answer(gym)

    override suspend fun groupVisits(
        viewer: UserId,
        from: CalendarDay,
        to: CalendarDay,
    ) = answer(windowVisits)

    override suspend fun oldestGroupVisit(viewer: UserId) = answer(oldest)

    override suspend fun groupLinks(viewer: UserId) = answer(links)

    override suspend fun groupPeaks(viewer: UserId) = answer(peaks)
}

class KeptFriendsTest {
    private var now = Instant.parse("2023-11-14T12:00:00Z")
    private val clock =
        object : Clock {
            override fun now() = now
        }
    private val server = Server()
    private var active: UserId? = IVAN
    private val friends =
        KeptFriends(server, SqlFriendAnswers(inMemoryDatabase(), Dispatchers.Unconfined), {
            active
        }, clock)

    private val press =
        Machine.new("Leg press", OLEG.userId, now).copy(tags = setOf("legs", "chest"))

    private fun visitOn(day: CalendarDay) =
        Visit(
            VisitId.random(),
            OLEG.userId,
            day,
            day.at(10 * 3_600_000L, Duration.ZERO),
            now,
            false,
        )

    private fun setIn(visit: Visit) =
        WorkoutSet(
            WorkoutSetId.random(),
            OLEG.userId,
            visit.id,
            press.id,
            80.0,
            8,
            0,
            visit.recordedAt,
            now,
            false,
            "narrow",
            DayBest(75.0, 8, 2),
        )

    @Test
    fun every_answer_the_server_gave_reads_back_the_same_from_the_device() =
        runTest {
            val visit = visitOn(CalendarDay(2023, 11, 13))
            val set = setIn(visit)
            val link =
                MachineLink(
                    MachineLinkId.random(),
                    OLEG.userId,
                    press.id,
                    MachineId.random(),
                    now,
                    false,
                )
            server.visits = listOf(visit)
            server.sets = listOf(set)
            server.machines = listOf(press)
            server.mates = listOf(OLEG)
            server.gym =
                GroupGym(
                    listOf(FriendMachine(press, OLEG)),
                    listOf(link),
                    listOf(Photo.new(press.id, OLEG.userId, now)),
                )
            server.windowVisits = listOf(FriendVisit(OLEG, visit))
            server.oldest = visit
            server.links = listOf(link)
            server.peaks =
                listOf(MachinePeaks(press.id, SetPeak(85.0, 6), SetPeak(20.0, 12), now, 3))
            val day = CalendarDay(2023, 11, 13)

            val fresh =
                listOf(
                    friends.visits(OLEG.userId),
                    friends.sets(visit),
                    friends.machines(OLEG.userId),
                    friends.mates(IVAN),
                    friends.groupGym(IVAN),
                    friends.groupVisits(IVAN, day, day),
                    friends.oldestGroupVisit(IVAN),
                    friends.groupLinks(IVAN),
                    friends.groupPeaks(IVAN),
                )
            server.offline = true
            val kept =
                keptOnly {
                    listOf(
                        friends.visits(OLEG.userId),
                        friends.sets(visit),
                        friends.machines(OLEG.userId),
                        friends.mates(IVAN),
                        friends.groupGym(IVAN),
                        friends.groupVisits(IVAN, day, day),
                        friends.oldestGroupVisit(IVAN),
                        friends.groupLinks(IVAN),
                        friends.groupPeaks(IVAN),
                    )
                }

            assertEquals(fresh, kept)
        }

    @Test
    fun a_kept_only_read_with_nothing_kept_fails_without_asking_the_server() =
        runTest {
            assertFails { keptOnly { friends.visits(OLEG.userId) } }

            assertEquals(0, server.reads)
        }

    @Test
    fun another_account_never_reads_what_was_kept_for_the_first() =
        runTest {
            server.mates = listOf(OLEG)
            friends.mates(IVAN)
            active = MISHA

            assertFails { keptOnly { friends.mates(IVAN) } }
        }

    @Test
    fun a_window_read_again_replaces_its_kept_visits_and_keeps_the_others() =
        runTest {
            val older = visitOn(CalendarDay(2023, 11, 1))
            val removed = visitOn(CalendarDay(2023, 11, 10))
            val added = visitOn(CalendarDay(2023, 11, 12))
            val newest = CalendarDay(2023, 11, 8) to CalendarDay(2023, 11, 14)
            server.windowVisits = listOf(FriendVisit(OLEG, older))
            friends.groupVisits(IVAN, CalendarDay(2023, 10, 25), CalendarDay(2023, 11, 7))
            server.windowVisits = listOf(FriendVisit(OLEG, removed))
            friends.groupVisits(IVAN, newest.first, newest.second)
            server.windowVisits = listOf(FriendVisit(OLEG, added))
            friends.groupVisits(IVAN, newest.first, newest.second)

            val kept =
                keptOnly { friends.groupVisits(IVAN, CalendarDay(2023, 10, 25), newest.second) }

            assertEquals(listOf(older, added), kept.map { it.visit })
            assertEquals(
                listOf(added),
                keptOnly {
                    friends.groupVisits(
                        IVAN,
                        newest.first,
                        newest.second,
                    )
                }.map { it.visit },
            )
        }

    @Test
    fun visits_recorded_more_than_a_month_ago_are_not_kept() =
        runTest {
            val old = visitOn(CalendarDay(2023, 10, 10))
            val recent = visitOn(CalendarDay(2023, 10, 20))
            server.windowVisits = listOf(FriendVisit(OLEG, old), FriendVisit(OLEG, recent))

            friends.groupVisits(IVAN, CalendarDay(2023, 10, 1), CalendarDay(2023, 10, 21))

            assertEquals(
                listOf(recent),
                keptOnly {
                    friends.groupVisits(IVAN, CalendarDay(2023, 10, 1), CalendarDay(2023, 10, 21))
                }.map { it.visit },
            )
        }

    @Test
    fun an_answer_not_read_again_for_a_month_is_forgotten() =
        runTest {
            server.mates = listOf(OLEG)
            friends.mates(IVAN)
            now += 32.days

            server.machines = listOf(press)
            friends.machines(OLEG.userId)

            assertFails { keptOnly { friends.mates(IVAN) } }
        }
}
