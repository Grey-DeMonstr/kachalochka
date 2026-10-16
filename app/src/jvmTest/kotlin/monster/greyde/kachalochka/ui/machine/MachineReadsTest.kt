package monster.greyde.kachalochka.ui.machine

import app.cash.turbine.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkRepository
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.gym.linkedCopy
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.friends.IVAN_SESSION
import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import monster.greyde.kachalochka.ui.friends.PASHA
import monster.greyde.kachalochka.ui.friends.signedInGym
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class MachineReadsTest {
    private val gym = signedInGym()
    private val t0 = gym.clock.current
    private val myPress = Machine.new("Жим ногами", ME.userId, t0)
    private val olegCopy = linkedCopy(myPress, OLEG.userId, t0)
    private val olegPress = olegCopy.first
    private val olegRow = Machine.new("Тяга", OLEG.userId, t0)
    private val reads = gym.machineReads
    private val misha =
        AccountSession(
            Account(UserId("22222222-2222-4222-8222-222222222222"), "m@example.test", "Миша"),
            "access",
            "refresh",
            t0,
        )

    /** Олег shares a group with Иван; his press copy links to Иван's, his row to nothing. */
    private suspend fun olegsGym(on: FakeGym = gym) {
        on.friends.group("Зал на Лесной", owner = OLEG, ME)
        on.friends.machines += listOf(olegPress, olegRow)
        on.friends.links += olegCopy.second
        on.machines.upsert(myPress)
    }

    private fun setOn(
        machine: Machine,
        owner: UserId?,
    ) = WorkoutSet(
        WorkoutSetId.random(),
        owner,
        VisitId.random(),
        machine.id,
        80.0,
        8,
        1,
        t0,
        t0,
        false,
    )

    private fun MachineRead.friendIds(): Set<MachineId> =
        shown.group
            ?.friends
            .orEmpty()
            .map { it.machine.id }
            .toSet()

    @Test
    fun own_machines_come_first_while_the_group_is_read() =
        runTest {
            olegsGym()
            val gate = CompletableDeferred<Unit>().also { gym.friends.gate = it }

            reads.machines(ME.userId).test(timeout = 10.seconds) {
                val first = awaitItem()
                assertEquals(
                    listOf(myPress.id),
                    first.shown.own.machines
                        .map { it.id },
                )
                assertNull(first.group)
                assertTrue(first.waiting)

                gate.complete(Unit)
                val next = awaitItem()
                assertTrue(next.group!!.fresh)
                assertEquals(setOf(olegPress.id, olegRow.id), next.friendIds())
                awaitComplete()
            }
        }

    @Test
    fun a_cached_group_comes_with_the_own_machines_and_the_fresh_one_follows() =
        runTest {
            olegsGym()
            gym.catalogue.group(ME.userId)
            val newer = Machine.new("Жим лёжа", OLEG.userId, t0)
            gym.friends.machines += newer
            val gate = CompletableDeferred<Unit>().also { gym.friends.gate = it }

            reads.machines(ME.userId).test(timeout = 10.seconds) {
                val first = awaitItem()
                assertFalse(first.group!!.fresh)
                assertEquals(setOf(olegPress.id, olegRow.id), first.friendIds())

                gate.complete(Unit)
                val next = awaitItem()
                assertTrue(next.group!!.fresh)
                assertTrue(newer.id in next.friendIds())
                awaitComplete()
            }
        }

    @Test
    fun a_failed_read_keeps_the_last_good_group_and_says_offline() =
        runTest {
            olegsGym()
            gym.catalogue.group(ME.userId)
            gym.friends.offline = true

            reads.machines(ME.userId).test(timeout = 10.seconds) {
                assertFalse(awaitItem().group!!.fresh)
                val next = awaitItem()
                assertTrue(next.offline)
                assertFalse(next.group!!.fresh)
                assertEquals(setOf(olegPress.id, olegRow.id), next.friendIds())
                awaitComplete()
            }
        }

    @Test
    fun a_failed_read_without_an_earlier_one_shows_only_own_machines() =
        runTest {
            olegsGym()
            gym.friends.offline = true

            reads.machines(ME.userId).test(timeout = 10.seconds) {
                assertTrue(awaitItem().waiting)
                val next = awaitItem()
                assertTrue(next.offline)
                assertNull(next.shown.group)
                assertFalse(next.waiting)
                awaitComplete()
            }
        }

    @Test
    fun signed_out_only_own_machines_come_and_no_friends_are_asked() =
        runTest {
            val out = FakeGym()
            val machine = Machine.new("Тяга", null, t0)
            out.machines.upsert(machine)

            out.machineReads.machines(null).test(timeout = 10.seconds) {
                val only = awaitItem()
                assertEquals(
                    listOf(machine.id),
                    only.shown.own.machines
                        .map { it.id },
                )
                assertNull(only.group)
                assertFalse(only.waiting)
                awaitComplete()
            }

            assertEquals(0, out.friends.reads)
        }

    @Test
    fun a_read_for_an_account_no_longer_active_ends_without_its_group() =
        runTest {
            val on = FakeGym().withAccounts(IVAN_SESSION, misha, active = IVAN_SESSION)
            olegsGym(on)
            val gate = CompletableDeferred<Unit>().also { on.friends.gate = it }

            on.machineReads.machines(ME.userId).test(timeout = 10.seconds) {
                awaitItem()
                on.accounts.switchTo(misha.account.userId)
                gate.complete(Unit)
                awaitComplete()
            }
        }

    @Test
    fun a_read_for_another_account_than_the_active_one_emits_nothing() =
        runTest {
            reads.machines(PASHA.userId).test(timeout = 10.seconds) { awaitComplete() }
            reads.group(PASHA.userId).test(timeout = 10.seconds) { awaitComplete() }
        }

    @Test
    fun the_group_stream_brings_the_cached_read_then_the_fresh_one() =
        runTest {
            olegsGym()

            reads.group(ME.userId).test(timeout = 10.seconds) {
                assertTrue(awaitItem().fresh)
                awaitComplete()
            }
            reads.group(ME.userId).test(timeout = 10.seconds) {
                assertFalse(awaitItem().fresh)
                assertTrue(awaitItem().fresh)
                awaitComplete()
            }
        }

    @Test
    fun two_screens_reading_at_once_send_one_request() =
        runTest {
            olegsGym()
            val gate = CompletableDeferred<Unit>().also { gym.friends.gate = it }

            val screens = List(2) { async { reads.machines(ME.userId).toList() } }
            runCurrent()
            gate.complete(Unit)
            screens.awaitAll()

            assertEquals(1, gym.friends.reads)
        }

    @Test
    fun cards_bring_the_peaks_and_the_friends_colours_after_the_group() =
        runTest {
            olegsGym()
            gym.sets.upsert(setOn(myPress, ME.userId))
            gym.friends.sets += setOn(olegPress, OLEG.userId)

            val all = reads.machines(ME.userId, withCards = true).toList()

            assertEquals(listOf(myPress.id), all.first().ownPeaks.map { it.machineId })
            assertTrue(all.first().friendPeaks.isEmpty())
            assertTrue(all.first().colors.isEmpty())
            assertEquals(listOf(olegPress.id), all.last().friendPeaks.map { it.machineId })
            assertEquals(setOf(OLEG.userId), all.last().colors.keys)
        }

    @Test
    fun failed_cards_keep_the_last_good_peaks_and_colours() =
        runTest {
            olegsGym()
            gym.friends.sets += setOf(olegPress, olegRow).map { setOn(it, OLEG.userId) }
            val before = reads.machines(ME.userId, withCards = true).toList().last()
            assertTrue(before.friendPeaks.isNotEmpty())
            assertTrue(before.colors.isNotEmpty())
            gym.friends.offline = true

            val again = reads.machines(ME.userId, withCards = true).toList()

            assertEquals(before.friendPeaks, again.first().friendPeaks)
            assertEquals(before.colors, again.first().colors)
            assertEquals(before.friendPeaks, again.last().friendPeaks)
            assertEquals(before.colors, again.last().colors)
        }

    @Test
    fun kept_cards_belong_to_the_account_they_were_read_for() =
        runTest {
            val on = FakeGym().withAccounts(IVAN_SESSION, misha, active = IVAN_SESSION)
            olegsGym(on)
            on.friends.sets += setOn(olegRow, OLEG.userId)
            val full =
                on.machineReads
                    .machines(ME.userId, withCards = true)
                    .toList()
                    .last()
            assertTrue(full.friendPeaks.isNotEmpty())
            on.accounts.switchTo(misha.account.userId)

            val first = on.machineReads.machines(misha.account.userId, withCards = true).first()

            assertTrue(first.friendPeaks.isEmpty())
            assertTrue(first.colors.isEmpty())
        }

    @Test
    fun signed_out_cards_carry_the_local_peaks() =
        runTest {
            val out = FakeGym()
            val machine = Machine.new("Тяга", null, t0)
            out.machines.upsert(machine)
            out.sets.upsert(setOn(machine, null))

            val only =
                out.machineReads
                    .machines(null, withCards = true)
                    .toList()
                    .single()

            assertEquals(listOf(machine.id), only.ownPeaks.map { it.machineId })
            assertTrue(only.friendPeaks.isEmpty())
            assertEquals(0, out.friends.reads)
        }

    @Test
    fun a_group_stream_stops_when_the_account_changes_after_the_cached_read() =
        runTest {
            val on = FakeGym().withAccounts(IVAN_SESSION, misha, active = IVAN_SESSION)
            olegsGym(on)
            on.catalogue.group(ME.userId)
            val before = on.friends.reads

            val seen = mutableListOf<GroupRead>()
            on.machineReads.group(ME.userId).collect {
                seen += it
                on.accounts.switchTo(misha.account.userId)
            }

            assertEquals(1, seen.size)
            assertEquals(before, on.friends.reads)
        }

    @Test
    fun cards_are_not_read_once_the_account_has_changed() =
        runTest {
            val on = FakeGym().withAccounts(IVAN_SESSION, misha, active = IVAN_SESSION)
            olegsGym(on)
            on.friends.sets += setOn(olegRow, OLEG.userId)

            var seen = 0
            on.machineReads.machines(ME.userId, withCards = true).collect {
                if (++seen == 2) on.accounts.switchTo(misha.account.userId)
            }

            assertEquals(2, seen)
            assertEquals(1, on.friends.reads)
        }

    @Test
    fun without_cards_no_peaks_or_colours_are_read() =
        runTest {
            olegsGym()

            val all = reads.machines(ME.userId).toList()

            assertNotNull(all.last().group)
            assertEquals(1, gym.friends.reads)
        }

    @Test
    fun a_cached_read_that_cannot_be_rebuilt_does_not_break_the_stream() =
        runTest {
            olegsGym()
            var failing = false
            val links =
                object : MachineLinkRepository {
                    override suspend fun upsert(link: MachineLink) = gym.machineLinks.upsert(link)

                    override suspend fun all(owner: UserId?): List<MachineLink> {
                        check(!failing) { "links unreadable" }
                        return gym.machineLinks.all(owner)
                    }
                }
            val over = gym.machineReadsOver(gym.catalogueOver(links))
            over.group(ME.userId).toList()
            failing = true

            val again = over.group(ME.userId).toList()

            assertTrue(again.last().offline)
        }
}
