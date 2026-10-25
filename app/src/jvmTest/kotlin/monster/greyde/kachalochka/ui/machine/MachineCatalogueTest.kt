package monster.greyde.kachalochka.ui.machine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkId
import monster.greyde.kachalochka.core.domain.gym.MachineLinkRepository
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.linkedCopy
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.family.SASHA
import monster.greyde.kachalochka.ui.family.childAccount
import monster.greyde.kachalochka.ui.friends.IVAN_SESSION
import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import monster.greyde.kachalochka.ui.friends.PASHA
import monster.greyde.kachalochka.ui.friends.signedInGym
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class MachineCatalogueTest {
    private val gym = signedInGym()
    private val t0 = gym.clock.current
    private val myPress = Machine.new("Жим ногами", ME.userId, t0)
    private val olegCopy = linkedCopy(myPress, OLEG.userId, t0)
    private val olegPress = olegCopy.first
    private val olegRow = Machine.new("Тяга", OLEG.userId, t0)
    private val catalogue = gym.catalogue

    /** Links whose writes after the first [kept] fail. */
    private fun failingLinks(kept: Int = 0) =
        object : MachineLinkRepository by gym.machineLinks {
            var writes = 0

            override suspend fun upsert(link: MachineLink) {
                if (writes++ >= kept) error("disk full")
                gym.machineLinks.upsert(link)
            }
        }

    /** Олег shares a group with Иван; his copy of the press links to Иван's, his row to nothing. */
    private suspend fun olegsGym() {
        gym.friends.group("Зал на Лесной", owner = OLEG, ME)
        gym.friends.machines += listOf(olegPress, olegRow)
        gym.friends.links += olegCopy.second
        gym.machines.upsert(myPress)
    }

    @Test
    fun own_holds_the_account_s_live_machines_photos_and_links() =
        runTest {
            val photo = Photo.new(myPress.id, ME.userId, t0)
            val link =
                MachineLink(MachineLinkId.random(), ME.userId, myPress.id, olegRow.id, t0, false)
            gym.machines.upsert(myPress)
            gym.machines.upsert(Machine.new("Ушла", ME.userId, t0).copy(deleted = true))
            gym.photos.upsert(photo)
            gym.machineLinks.upsert(link)

            val own = catalogue.own(ME.userId)

            assertEquals(
                OwnMachines(ME.userId, listOf(myPress), listOf(photo), listOf(link), me = ME),
                own,
            )
        }

    @Test
    fun a_friend_s_photo_stands_for_an_own_machine_linked_to_it_once_the_group_read_lands() =
        runTest {
            olegsGym()
            val olegPhoto = Photo.new(olegPress.id, OLEG.userId, t0)
            gym.friends.photos += olegPhoto
            val own = catalogue.own(ME.userId)

            assertNull(ShownMachines(own, null).cover(myPress.id))

            val group = catalogue.group(ME.userId)

            assertEquals(olegPhoto, ShownMachines(own, group).cover(myPress.id))
        }

    @Test
    fun a_chosen_friend_s_photo_stands_for_the_machine_while_the_group_read_has_it() =
        runTest {
            olegsGym()
            val mine = Photo.new(myPress.id, ME.userId, t0)
            val olegPhoto = Photo.new(olegPress.id, OLEG.userId, t0)
            gym.photos.upsert(mine)
            gym.friends.photos += olegPhoto
            gym.machines.upsert(myPress.copy(coverPhoto = olegPhoto.id))
            val own = catalogue.own(ME.userId)

            assertEquals(mine, ShownMachines(own, null).cover(myPress.id))
            assertEquals(
                olegPhoto,
                ShownMachines(own, catalogue.group(ME.userId)).cover(myPress.id),
            )
        }

    @Test
    fun a_friend_s_machine_shows_the_cover_its_owner_chose() =
        runTest {
            val first = Photo.new(olegRow.id, OLEG.userId, t0)
            val chosen = Photo.new(olegRow.id, OLEG.userId, t0 + 1.minutes)
            olegsGym()
            gym.friends.machines.replaceAll {
                if (it.id ==
                    olegRow.id
                ) {
                    it.copy(coverPhoto = chosen.id)
                } else {
                    it
                }
            }
            gym.friends.photos += listOf(first, chosen)

            val shown = ShownMachines(catalogue.own(ME.userId), catalogue.group(ME.userId))

            assertEquals(chosen, shown.cover(olegRow.id))
        }

    @Test
    fun friends_machines_are_offered_one_per_cluster_without_an_own_machine() =
        runTest {
            olegsGym()

            val shown = ShownMachines(catalogue.own(ME.userId), catalogue.group(ME.userId))

            assertEquals(listOf(olegRow), shown.offered.map { it.machine })
            assertTrue(shown.clusters.sameMachine(myPress.id, olegPress.id))
        }

    @Test
    fun linked_rows_are_the_own_machines_of_the_cluster_then_the_friends_linked_ones() =
        runTest {
            olegsGym()
            val squat = Machine.new("Гакк", ME.userId, t0)
            gym.machines.upsert(squat)
            gym.machineLinks.upsert(
                MachineLink(MachineLinkId.random(), ME.userId, myPress.id, squat.id, t0, false),
            )
            val own = catalogue.own(ME.userId)

            assertEquals(listOf(squat), ShownMachines(own, null).linkedRows(myPress.id))
            assertEquals(
                listOf(squat, olegPress),
                ShownMachines(own, catalogue.group(ME.userId)).linkedRows(myPress.id),
            )
        }

    @Test
    fun signed_out_own_linked_machines_are_linked_rows() =
        runTest {
            val press = Machine.new("Жим ногами", null, t0)
            val squat = Machine.new("Гакк", null, t0)
            gym.machines.upsert(press)
            gym.machines.upsert(squat)
            gym.machineLinks.upsert(
                MachineLink(MachineLinkId.random(), null, press.id, squat.id, t0, false),
            )

            val own = catalogue.own(null)

            assertNull(own.me)
            assertEquals(listOf(squat), ShownMachines(own, null).linkedRows(press.id))
        }

    @Test
    fun a_group_read_for_another_account_is_not_shown() =
        runTest {
            olegsGym()
            val group = catalogue.group(ME.userId)

            val shown = ShownMachines(catalogue.own(PASHA.userId), group)

            assertNull(shown.group)
            assertEquals(emptyList(), shown.offered)
        }

    @Test
    fun clusters_join_the_account_s_links_with_its_group_mates() =
        runTest {
            olegsGym()

            val clusters = catalogue.clusters(ME.userId)

            assertTrue(clusters.sameMachine(myPress.id, olegPress.id))
        }

    /** Паша joins the group; his machine of his own links to [to]. */
    private fun pashaLinksTo(to: Machine): Machine {
        gym.friends.group("Зал на Лесной", owner = OLEG, ME, PASHA)
        val pashas = Machine.new("Платформа", PASHA.userId, t0)
        gym.friends.machines += pashas
        gym.friends.links +=
            MachineLink(MachineLinkId.random(), PASHA.userId, pashas.id, to.id, t0, false)
        return pashas
    }

    private fun linkedPairs(): Set<Pair<MachineId, MachineId>> =
        gym.machineLinks.rows.values
            .map { it.machineId to it.linkedMachineId }
            .toSet()

    @Test
    fun a_group_read_links_an_own_machine_to_each_friend_s_machine_of_its_cluster_and_syncs() =
        runTest {
            olegsGym()
            val pashas = pashaLinksTo(olegPress)
            gym.clock.current += 1.minutes

            val group = catalogue.group(ME.userId)

            val written =
                gym.machineLinks.rows.values
                    .single()
            val now = gym.clock.current
            assertEquals(
                MachineLink(written.id, ME.userId, myPress.id, pashas.id, now, false),
                written,
            )
            assertTrue(written in group.links)
            assertEquals(1, gym.sync.requests)
        }

    @Test
    fun a_group_read_with_every_link_direct_writes_nothing() =
        runTest {
            olegsGym()

            catalogue.group(ME.userId)

            assertEquals(emptyMap(), gym.machineLinks.rows)
            assertEquals(0, gym.sync.requests)
        }

    @Test
    fun group_reads_at_the_same_time_write_each_link_once() =
        runTest {
            olegsGym()
            pashaLinksTo(olegPress)
            val gate = CompletableDeferred<Unit>()
            gym.friends.gate = gate
            // The read suspends, as a database's does, so the other upkeep could run in between.
            val slowLinks =
                object : MachineLinkRepository by gym.machineLinks {
                    override suspend fun all(owner: UserId?): List<MachineLink> =
                        gym.machineLinks.all(owner).also { yield() }
                }
            val slow = gym.catalogueOver(slowLinks)

            val reads = List(2) { launch { slow.group(ME.userId) } }
            testScheduler.advanceUntilIdle()
            gate.complete(Unit)
            reads.joinAll()

            assertEquals(1, gym.machineLinks.rows.size)
        }

    @Test
    fun a_failed_upkeep_keeps_the_group_read_and_is_recorded() =
        runTest {
            olegsGym()
            val pashas = pashaLinksTo(olegPress)

            val group = gym.catalogueOver(failingLinks()).group(ME.userId)

            assertEquals(
                setOf(olegPress, olegRow, pashas),
                group.friends.map { it.machine }.toSet(),
            )
            assertTrue(group.clusters.sameMachine(myPress.id, pashas.id))
            assertEquals(emptyMap(), gym.machineLinks.rows)
            assertEquals(listOf("disk full"), gym.failures.map { it.message })
        }

    @Test
    fun a_failed_upkeep_after_the_chosen_link_keeps_the_link_and_syncs() =
        runTest {
            olegsGym()
            val pashas = pashaLinksTo(olegRow)
            val mine = Machine.new("Тяга", ME.userId, t0)
            gym.machines.upsert(mine)
            val linking = gym.catalogueOver(failingLinks(kept = 1))

            linking.link(ME.userId, mine.id, olegRow.id, linking.group(ME.userId))

            assertEquals(setOf(mine.id to olegRow.id), linkedPairs())
            assertEquals(1, gym.failures.size)
            assertEquals(1, gym.sync.requests)
            assertTrue(
                gym.machineLinks.rows.values
                    .none { it.linkedMachineId == pashas.id },
            )
        }

    @Test
    fun a_failed_upkeep_after_taking_keeps_the_copy_and_its_link() =
        runTest {
            olegsGym()
            pashaLinksTo(olegRow)
            val taking = gym.catalogueOver(failingLinks(kept = 1))

            val copy = taking.take(ME.userId, olegRow, taking.group(ME.userId))

            assertEquals(copy, gym.machines.rows[copy.id])
            assertEquals(setOf(copy.id to olegRow.id), linkedPairs())
            assertEquals(1, gym.failures.size)
            assertEquals(1, gym.sync.requests)
        }

    @Test
    fun an_upkeep_stopped_while_writing_still_asks_for_a_sync() =
        runTest {
            olegsGym()
            pashaLinksTo(olegPress)
            val stuck =
                object : MachineLinkRepository by gym.machineLinks {
                    override suspend fun upsert(link: MachineLink) {
                        gym.machineLinks.upsert(link)
                        awaitCancellation()
                    }
                }

            val read = launch { gym.catalogueOver(stuck).group(ME.userId) }
            testScheduler.advanceUntilIdle()
            read.cancel()
            read.join()

            assertEquals(1, gym.machineLinks.rows.size)
            assertEquals(1, gym.sync.requests)
            assertEquals(emptyList(), gym.failures)
        }

    @Test
    fun a_group_read_for_another_account_writes_that_account_s_links() =
        runTest {
            olegsGym()
            pashaLinksTo(olegPress)
            gym.friends.machines += myPress
            val mine = Machine.new("Жим", PASHA.userId, t0)
            gym.machines.upsert(mine)
            gym.machineLinks.upsert(
                MachineLink(MachineLinkId.random(), PASHA.userId, mine.id, olegRow.id, t0, false),
            )
            gym.friends.links +=
                MachineLink(MachineLinkId.random(), OLEG.userId, olegRow.id, myPress.id, t0, false)

            catalogue.group(PASHA.userId)

            val written =
                gym.machineLinks
                    .all(PASHA.userId)
                    .map { it.linkedMachineId }
                    .toSet()
            assertEquals(setOf(olegRow.id, olegPress.id, myPress.id), written)
            assertEquals(emptyList(), gym.machineLinks.all(ME.userId))
        }

    @Test
    fun taking_a_friend_s_machine_writes_the_account_s_copy_linked_to_its_cluster_and_syncs() =
        runTest {
            olegsGym()
            val pashas = pashaLinksTo(olegRow)
            val group = catalogue.group(ME.userId)

            val copy = catalogue.take(ME.userId, olegRow, group)

            assertEquals(copy, gym.machines.rows[copy.id])
            assertEquals(ME.userId, copy.userId)
            assertEquals(olegRow.name, copy.name)
            assertEquals(setOf(copy.id to olegRow.id, copy.id to pashas.id), linkedPairs())
            assertEquals(1, gym.sync.requests)
        }

    @Test
    fun a_taken_machine_keeps_the_friend_s_tags() =
        runTest {
            olegsGym()
            gym.machines.upsert(myPress.copy(tags = setOf("ноги")))
            val tagged = olegRow.copy(tags = setOf("ноги", "спина"))
            gym.friends.machines.replaceAll { if (it.id == olegRow.id) tagged else it }

            val copy = catalogue.take(ME.userId, tagged, catalogue.group(ME.userId))

            assertEquals(setOf("ноги", "спина"), gym.machines.byId(copy.id)?.tags)
        }

    @Test
    fun a_friend_s_machine_stands_in_as_the_account_s_machine_of_its_cluster() =
        runTest {
            olegsGym()

            val own = catalogue.friendStandIn(ME.userId, olegPress.id, catalogue.group(ME.userId))

            assertEquals(myPress, own)
            assertEquals(listOf(myPress), gym.machines.all(ME.userId))
        }

    @Test
    fun a_friend_s_machine_without_an_own_one_in_its_cluster_stands_in_as_a_linked_copy() =
        runTest {
            olegsGym()

            val own = catalogue.friendStandIn(ME.userId, olegRow.id, catalogue.group(ME.userId))

            assertEquals("Тяга", own?.name)
            assertEquals(own, own?.id?.let { gym.machines.byId(it) })
            assertTrue(own?.id to olegRow.id in linkedPairs())
            assertEquals(1, gym.sync.requests)
        }

    @Test
    fun friend_stand_ins_at_the_same_time_write_one_copy() =
        runTest {
            olegsGym()
            // The read suspends, as a database's does, so the other stand-in could run in between.
            val slowLinks =
                object : MachineLinkRepository by gym.machineLinks {
                    override suspend fun all(owner: UserId?): List<MachineLink> =
                        gym.machineLinks.all(owner).also { yield() }
                }
            val slow = gym.catalogueOver(slowLinks)
            val group = slow.group(ME.userId)

            val copies =
                List(2) { async { slow.friendStandIn(ME.userId, olegRow.id, group) } }.awaitAll()

            assertEquals(1, copies.toSet().size)
            assertEquals(1, gym.machines.all(ME.userId).count { it.name == olegRow.name })
        }

    @Test
    fun a_machine_the_group_read_lacks_has_no_stand_in() =
        runTest {
            olegsGym()

            assertNull(
                catalogue.friendStandIn(ME.userId, MachineId.random(), catalogue.group(ME.userId)),
            )
        }

    @Test
    fun linking_an_own_machine_to_a_friend_s_links_it_to_the_whole_cluster() =
        runTest {
            olegsGym()
            val pashas = pashaLinksTo(olegRow)
            val mine = Machine.new("Тяга", ME.userId, t0)
            gym.machines.upsert(mine)
            val group = catalogue.group(ME.userId)

            catalogue.link(ME.userId, mine.id, olegRow.id, group)

            assertEquals(setOf(mine.id to olegRow.id, mine.id to pashas.id), linkedPairs())
            assertEquals(1, gym.sync.requests)
        }

    @Test
    fun without_a_group_read_a_link_joins_only_the_friend_s_machine() =
        runTest {
            olegsGym()
            pashaLinksTo(olegRow)
            val mine = Machine.new("Тяга", ME.userId, t0)
            gym.machines.upsert(mine)

            catalogue.link(ME.userId, mine.id, olegRow.id, group = null)

            assertEquals(setOf(mine.id to olegRow.id), linkedPairs())
            assertEquals(1, gym.sync.requests)
        }

    @Test
    fun group_reads_for_one_account_at_the_same_time_share_one_request() =
        runTest {
            olegsGym()
            val gate = CompletableDeferred<Unit>()
            gym.friends.gate = gate

            val reads = List(3) { async { catalogue.group(ME.userId) } }
            testScheduler.advanceUntilIdle()
            gate.complete(Unit)
            val groups = reads.awaitAll()

            assertEquals(1, gym.friends.reads)
            assertEquals(1, groups.toSet().size)
        }

    @Test
    fun a_shared_read_stopped_with_its_screen_is_read_again_for_the_others() =
        runTest {
            olegsGym()
            val gate = CompletableDeferred<Unit>()
            gym.friends.gate = gate

            val first = launch { catalogue.group(ME.userId) }
            val second = async { catalogue.group(ME.userId) }
            testScheduler.advanceUntilIdle()
            first.cancel()
            testScheduler.advanceUntilIdle()
            gate.complete(Unit)

            val group = second.await()
            assertEquals(setOf(olegPress, olegRow), group.friends.map { it.machine }.toSet())
            assertEquals(2, gym.friends.reads)
        }

    @Test
    fun the_last_group_read_shows_with_the_own_links_as_they_are_now() =
        runTest {
            olegsGym()
            assertNull(catalogue.lastGroup(ME.userId))
            catalogue.group(ME.userId)
            gym.friends.offline = true
            val mine = Machine.new("Тяга", ME.userId, t0)
            gym.machines.upsert(mine)
            gym.machineLinks.upsert(
                MachineLink(MachineLinkId.random(), ME.userId, mine.id, olegRow.id, t0, false),
            )

            val last = assertNotNull(catalogue.lastGroup(ME.userId))

            assertEquals(setOf(olegPress, olegRow), last.friends.map { it.machine }.toSet())
            assertTrue(last.clusters.sameMachine(myPress.id, olegPress.id))
            assertTrue(last.clusters.sameMachine(mine.id, olegRow.id))
            assertNull(catalogue.lastGroup(PASHA.userId))
        }

    private fun set(
        machine: Machine,
        weight: Double = 70.0,
    ) = WorkoutSet(
        WorkoutSetId.random(),
        machine.userId,
        VisitId.random(),
        machine.id,
        weight,
        10,
        0,
        t0,
        t0,
        false,
    )

    private suspend fun merge(
        owner: UserId?,
        kept: Machine,
        removed: Machine,
        convert: Boolean = false,
        adjust: Boolean = false,
    ) = catalogue.merge(owner, kept.id, removed.id, convert = convert, adjust = adjust)

    /** Иван's second machine, read with the group so the last group read is in memory. */
    private suspend fun mineWithGroupRead(): Machine {
        olegsGym()
        val mine = Machine.new("Жим", ME.userId, t0)
        gym.machines.upsert(mine)
        catalogue.group(ME.userId)
        return mine
    }

    @Test
    fun a_merge_moves_friends_links_into_the_removed_machine_and_drops_the_last_group_read() =
        runTest {
            val mine = mineWithGroupRead()

            assertEquals(MergeOutcome.Done, merge(ME.userId, kept = mine, removed = myPress))

            assertEquals(listOf(myPress.id to mine.id), gym.friends.repointed)
            assertNull(catalogue.lastGroup(ME.userId))
            assertTrue(
                gym.machines.rows
                    .getValue(myPress.id)
                    .deleted,
            )
        }

    @Test
    fun a_removed_machine_no_friend_links_to_is_not_repointed() =
        runTest {
            val mine = mineWithGroupRead()

            assertEquals(MergeOutcome.Done, merge(ME.userId, kept = myPress, removed = mine))

            assertEquals(emptyList(), gym.friends.repointed)
            assertNotNull(catalogue.lastGroup(ME.userId))
            assertTrue(
                gym.machines.rows
                    .getValue(mine.id)
                    .deleted,
            )
        }

    @Test
    fun offline_a_merge_writes_nothing_and_keeps_the_last_group_read() =
        runTest {
            val mine = mineWithGroupRead()
            val moved = set(myPress)
            gym.sets.upsert(moved)
            val requests = gym.sync.requests
            gym.friends.offline = true

            assertEquals(MergeOutcome.Offline, merge(ME.userId, kept = mine, removed = myPress))

            assertEquals(moved, gym.sets.rows[moved.id])
            assertFalse(
                gym.machines.rows
                    .getValue(myPress.id)
                    .deleted,
            )
            assertNotNull(catalogue.lastGroup(ME.userId))
            assertEquals(requests, gym.sync.requests)
        }

    @Test
    fun a_merge_moves_the_own_links_to_the_kept_machine_and_asks_for_one_sync() =
        runTest {
            val removed = Machine.new("Жим", ME.userId, t0)
            val kept = Machine.new("Жим лёжа", ME.userId, t0)
            gym.machines.upsert(removed)
            gym.machines.upsert(kept)
            val x = MachineId.random()
            val y = MachineId.random()
            val fromRemoved = ownLink(ME.userId, removed.id, x)
            val keptHas = ownLink(ME.userId, kept.id, y)
            val duplicate = ownLink(ME.userId, removed.id, y)
            listOf(fromRemoved, keptHas, duplicate).forEach { gym.machineLinks.upsert(it) }
            gym.clock.current += 1.minutes
            val now = gym.clock.current

            assertEquals(MergeOutcome.Done, merge(ME.userId, kept = kept, removed = removed))

            val rows = gym.machineLinks.rows
            assertEquals(
                fromRemoved.copy(machineId = kept.id, updatedAt = now),
                rows[fromRemoved.id],
            )
            assertEquals(duplicate.copy(deleted = true, updatedAt = now), rows[duplicate.id])
            assertEquals(keptHas, rows[keptHas.id])
            assertEquals(
                removed.copy(deleted = true, updatedAt = now),
                gym.machines.rows[removed.id],
            )
            assertEquals(1, gym.sync.requests)
        }

    @Test
    fun a_merge_moves_the_removed_machine_s_sets_and_photos_to_the_kept_one() =
        runTest {
            val removed = Machine.new("Жим", ME.userId, t0)
            gym.machines.upsert(removed)
            gym.machines.upsert(myPress)
            val moved = set(removed)
            val stays = set(myPress)
            gym.sets.upsert(moved)
            gym.sets.upsert(stays)
            val photo = Photo.new(removed.id, ME.userId, t0)
            gym.photos.upsert(photo)
            gym.clock.current += 1.minutes
            val now = gym.clock.current

            assertEquals(MergeOutcome.Done, merge(ME.userId, kept = myPress, removed = removed))

            assertEquals(
                moved.copy(machineId = myPress.id, updatedAt = now),
                gym.sets.rows[moved.id],
            )
            assertEquals(stays, gym.sets.rows[stays.id])
            assertEquals(
                listOf(photo.copy(machineId = myPress.id, updatedAt = now)),
                gym.photos.forMachine(myPress.id),
            )
        }

    @Test
    fun a_merge_converts_the_moved_sets_to_the_kept_machine_s_unit_when_asked() =
        runTest {
            val removed = Machine.new("Жим", ME.userId, t0)
            val kept = myPress.copy(unit = WeightUnit.Lb, weightStep = 5.0)
            gym.machines.upsert(removed)
            gym.machines.upsert(kept)
            val imported = set(removed, weight = 22.7)
            gym.sets.upsert(imported)

            merge(ME.userId, kept = kept, removed = removed, convert = true)

            assertEquals(
                50.0,
                gym.sets.rows
                    .getValue(imported.id)
                    .weight,
            )
        }

    @Test
    fun a_merge_keeps_the_moved_sets_totals_under_the_kept_platform_when_asked() =
        runTest {
            val removed = Machine.new("Жим", ME.userId, t0)
            val kept = myPress.copy(platformWeight = 25.0)
            gym.machines.upsert(removed)
            gym.machines.upsert(kept)
            val imported = set(removed)
            gym.sets.upsert(imported)

            merge(ME.userId, kept = kept, removed = removed, adjust = true)

            assertEquals(
                45.0,
                gym.sets.rows
                    .getValue(imported.id)
                    .weight,
            )
        }

    @Test
    fun a_merge_writes_the_sets_then_the_links_and_the_machine_last() =
        runTest {
            val removed = Machine.new("Жим", ME.userId, t0)
            gym.machines.upsert(removed)
            gym.machines.upsert(myPress)
            gym.sets.upsert(set(removed))
            gym.machineLinks.upsert(ownLink(ME.userId, removed.id, MachineId.random()))
            val order = mutableListOf<String>()
            val machines =
                object : MachineRepository by gym.machines {
                    override suspend fun upsert(machine: Machine) {
                        order += "machine"
                        gym.machines.upsert(machine)
                    }
                }
            val sets =
                object : WorkoutSetRepository by gym.sets {
                    override suspend fun upsert(set: WorkoutSet) {
                        order += "set"
                        gym.sets.upsert(set)
                    }
                }
            val links =
                object : MachineLinkRepository by gym.machineLinks {
                    override suspend fun upsert(link: MachineLink) {
                        order += "link"
                        gym.machineLinks.upsert(link)
                    }
                }

            gym
                .catalogueOver(links, machines, sets)
                .merge(ME.userId, myPress.id, removed.id, convert = false, adjust = false)

            assertEquals(listOf("set", "link", "machine"), order)
        }

    @Test
    fun a_merge_of_a_machine_that_is_gone_writes_nothing() =
        runTest {
            gym.machines.upsert(myPress)
            val gone = Machine.new("Жим", ME.userId, t0).copy(deleted = true)
            gym.machines.upsert(gone)

            assertEquals(MergeOutcome.Gone, merge(ME.userId, kept = myPress, removed = gone))
            assertEquals(MergeOutcome.Gone, merge(ME.userId, kept = gone, removed = myPress))

            assertFalse(
                gym.machines.rows
                    .getValue(myPress.id)
                    .deleted,
            )
            assertEquals(0, gym.sync.requests)
        }

    @Test
    fun a_merge_for_an_account_no_longer_active_writes_nothing() =
        runTest {
            val removed = Machine.new("Жим", ME.userId, t0)
            gym.machines.upsert(removed)
            gym.machines.upsert(myPress)
            gym.withChild(childAccount(SASHA, IVAN_SESSION))
            gym.accounts.switchTo(SASHA.userId)

            assertEquals(MergeOutcome.Gone, merge(ME.userId, kept = myPress, removed = removed))

            assertFalse(
                gym.machines.rows
                    .getValue(removed.id)
                    .deleted,
            )
            assertEquals(0, gym.sync.requests)
        }

    @Test
    fun without_an_account_a_merge_stays_on_the_device() =
        runTest {
            val device = FakeGym()
            val removed = Machine.new("Жим", null, t0)
            val kept = Machine.new("Жим лёжа", null, t0)
            device.machines.upsert(removed)
            device.machines.upsert(kept)

            val outcome = device.catalogue.merge(null, kept.id, removed.id, false, false)

            assertEquals(MergeOutcome.Done, outcome)
            assertTrue(
                device.machines.rows
                    .getValue(removed.id)
                    .deleted,
            )
            assertEquals(0, device.friends.reads)
            assertEquals(1, device.sync.requests)
        }

    @Test
    fun a_group_read_fails_when_the_network_does_not_answer() =
        runTest {
            olegsGym()
            gym.friends.offline = true

            assertFailsWith<IllegalStateException> { catalogue.group(ME.userId) }
        }

    private fun ownLink(
        owner: UserId,
        machine: MachineId,
        linked: MachineId,
    ) = MachineLink(MachineLinkId.random(), owner, machine, linked, t0, false)

    @Test
    fun a_device_account_s_copy_is_made_once_and_asks_for_a_sync() =
        runTest {
            val sashas = myPress.copy(userId = SASHA.userId)
            gym.machines.upsert(sashas)

            val first = catalogue.ownCopy(ME.userId, sashas)
            val second = catalogue.ownCopy(ME.userId, sashas)

            assertEquals(first, second)
            assertEquals(2, gym.machines.rows.size)
            assertEquals(sashas.name, first.name)
            assertEquals(1, gym.sync.requests)
        }

    @Test
    fun a_device_account_s_copy_keeps_the_tags_and_links_to_the_shown_machine() =
        runTest {
            val sashas = myPress.copy(userId = SASHA.userId, tags = setOf("ноги", "спина"))
            gym.machines.upsert(sashas)
            gym.machines.upsert(olegRow.copy(userId = ME.userId, tags = setOf("ноги")))

            catalogue.ownCopy(ME.userId, sashas)

            val copy =
                gym.machines.rows.values
                    .single { it.userId == ME.userId && it.name == myPress.name }
            assertEquals(setOf("ноги", "спина"), copy.tags)
            val link =
                gym.machineLinks.rows.values
                    .single()
            assertEquals(ME.userId, link.userId)
            assertEquals(copy.id, link.machineId)
            assertEquals(sashas.id, link.linkedMachineId)
        }

    @Test
    fun a_device_account_s_copy_links_to_every_machine_the_shown_one_is_linked_with() =
        runTest {
            val sashas = myPress.copy(userId = SASHA.userId)
            val sashasOld = Machine.new("Жим", SASHA.userId, t0)
            val olegs = Machine.new("Платформа", OLEG.userId, t0)
            gym.machines.upsert(sashas)
            gym.machines.upsert(sashasOld)
            gym.machineLinks.upsert(ownLink(SASHA.userId, sashas.id, olegs.id))
            gym.machineLinks.upsert(ownLink(SASHA.userId, sashasOld.id, olegs.id))

            val copy = catalogue.ownCopy(ME.userId, sashas)

            assertEquals(
                setOf(sashas.id, olegs.id, sashasOld.id),
                gym.machineLinks
                    .all(ME.userId)
                    .onEach { assertEquals(copy.id, it.machineId) }
                    .map { it.linkedMachineId }
                    .toSet(),
            )
        }

    @Test
    fun the_owner_s_machine_linked_with_the_shown_one_stands_in() =
        runTest {
            val sashas = myPress.copy(userId = SASHA.userId)
            val platform = Machine.new("Платформа", ME.userId, t0)
            val press = myPress.copy(id = MachineId.random())
            gym.machines.upsert(sashas)
            gym.machines.upsert(platform)
            gym.machines.upsert(press)
            gym.machineLinks.upsert(ownLink(SASHA.userId, sashas.id, platform.id))

            assertEquals(platform, catalogue.ownCopy(ME.userId, sashas))
            assertEquals(3, gym.machines.rows.size)
        }

    @Test
    fun an_own_link_also_joins_the_owner_s_machine_to_the_shown_one() =
        runTest {
            val sashas = myPress.copy(userId = SASHA.userId)
            val platform = Machine.new("Платформа", ME.userId, t0)
            gym.machines.upsert(sashas)
            gym.machines.upsert(platform)
            gym.machineLinks.upsert(ownLink(ME.userId, platform.id, sashas.id))

            assertEquals(platform, catalogue.ownCopy(ME.userId, sashas))
        }

    @Test
    fun the_owner_s_machine_of_the_same_name_in_any_letter_case_stands_in() =
        runTest {
            val sashas = myPress.copy(userId = SASHA.userId)
            val mine = Machine.new("ЖИМ НОГАМИ", ME.userId, t0)
            gym.machines.upsert(sashas)
            gym.machines.upsert(mine)

            assertEquals(mine, catalogue.ownCopy(ME.userId, sashas))
            assertTrue(gym.machineLinks.rows.isEmpty())
        }

    @Test
    fun a_device_stand_in_writes_nothing() =
        runTest {
            val sashas = Machine.new("Гакк", SASHA.userId, t0)
            gym.machines.upsert(myPress)
            gym.machines.upsert(sashas)

            assertNull(catalogue.deviceStandIn(ME.userId, sashas))
            assertEquals(myPress, catalogue.deviceStandIn(ME.userId, myPress))
            assertTrue(gym.machineLinks.rows.isEmpty())
            assertEquals(2, gym.machines.rows.size)
            assertEquals(0, gym.sync.requests)
        }

    @Test
    fun a_device_copy_stopped_while_writing_still_asks_for_a_sync() =
        runTest {
            val sashas = Machine.new("Гакк", SASHA.userId, t0)
            gym.machines.upsert(sashas)
            val stuck =
                object : MachineLinkRepository by gym.machineLinks {
                    override suspend fun upsert(link: MachineLink) {
                        gym.machineLinks.upsert(link)
                        awaitCancellation()
                    }
                }

            val copying = launch { gym.catalogueOver(stuck).ownCopy(ME.userId, sashas) }
            testScheduler.advanceUntilIdle()
            copying.cancel()
            copying.join()

            assertEquals(1, gym.machineLinks.rows.size)
            assertEquals(1, gym.sync.requests)
        }

    @Test
    fun a_device_copy_stopped_before_its_link_still_asks_for_a_sync() =
        runTest {
            val sashas = Machine.new("Гакк", SASHA.userId, t0)
            gym.machines.upsert(sashas)
            val stuck =
                object : MachineRepository by gym.machines {
                    override suspend fun upsert(machine: Machine) {
                        gym.machines.upsert(machine)
                        awaitCancellation()
                    }
                }

            val copying =
                launch { gym.catalogueOver(gym.machineLinks, stuck).ownCopy(ME.userId, sashas) }
            testScheduler.advanceUntilIdle()
            copying.cancel()
            copying.join()

            assertEquals(2, gym.machines.rows.size)
            assertTrue(gym.machineLinks.rows.isEmpty())
            assertEquals(1, gym.sync.requests)
        }

    @Test
    fun an_own_machine_is_its_own_copy_and_nothing_is_written() =
        runTest {
            gym.machines.upsert(myPress)

            assertEquals(myPress, catalogue.ownCopy(ME.userId, myPress))
            assertTrue(gym.machineLinks.rows.isEmpty())
            assertEquals(1, gym.machines.rows.size)
            assertEquals(0, gym.sync.requests)
        }

    @Test
    fun a_device_copy_and_a_group_read_at_the_same_time_write_each_link_once() =
        runTest {
            // Паша's rows are on the device too, as a second account's are.
            gym.friends.group("Зал на Лесной", owner = OLEG, ME, PASHA)
            val pashas = Machine.new("Тяга", PASHA.userId, t0)
            val pashaLink = ownLink(PASHA.userId, pashas.id, olegRow.id)
            gym.friends.machines += listOf(olegRow, pashas)
            gym.friends.links += pashaLink
            gym.machines.upsert(pashas)
            gym.machineLinks.upsert(pashaLink)
            val gate = CompletableDeferred<Unit>()
            // The copy's first link lands, then its write waits, so the upkeep could run between.
            val paused =
                object : MachineLinkRepository by gym.machineLinks {
                    var writes = 0

                    override suspend fun upsert(link: MachineLink) {
                        gym.machineLinks.upsert(link)
                        if (writes++ == 0) gate.await()
                    }
                }
            val racing = gym.catalogueOver(paused)

            val copying = async { racing.ownCopy(ME.userId, pashas) }
            testScheduler.advanceUntilIdle()
            val reading = launch { racing.group(ME.userId) }
            testScheduler.advanceUntilIdle()
            gate.complete(Unit)
            val copy = copying.await()
            reading.join()

            assertEquals(
                1,
                gym.machineLinks.rows.values
                    .count { it.machineId == copy.id && it.linkedMachineId == olegRow.id },
            )
        }

    private suspend fun linkedMine(): Pair<Machine, MachineLink> {
        olegsGym()
        val mine = Machine.new("Тяга", ME.userId, t0)
        gym.machines.upsert(mine)
        val link = ownLink(ME.userId, mine.id, olegRow.id)
        gym.machineLinks.upsert(link)
        catalogue.group(ME.userId)
        return mine to link
    }

    @Test
    fun unlinking_breaks_friends_links_then_deletes_the_own_ones_and_drops_the_last_group_read() =
        runTest {
            val (mine, link) = linkedMine()
            gym.clock.current += 1.minutes

            assertTrue(catalogue.unlink(ME.userId, mine.id))

            assertEquals(listOf(mine.id), gym.friends.broken)
            assertEquals(
                link.copy(deleted = true, updatedAt = gym.clock.current),
                gym.machineLinks.rows[link.id],
            )
            assertNull(catalogue.lastGroup(ME.userId))
            assertEquals(1, gym.sync.requests)
        }

    @Test
    fun offline_unlinking_writes_nothing_and_keeps_the_last_group_read() =
        runTest {
            val (mine, link) = linkedMine()
            val requests = gym.sync.requests
            gym.friends.offline = true

            assertFalse(catalogue.unlink(ME.userId, mine.id))

            assertEquals(link, gym.machineLinks.rows[link.id])
            assertNotNull(catalogue.lastGroup(ME.userId))
            assertEquals(requests, gym.sync.requests)
        }

    @Test
    fun signed_out_unlinking_deletes_the_own_links_without_the_server() =
        runTest {
            val press = Machine.new("Жим ногами", null, t0)
            val squat = Machine.new("Гакк", null, t0)
            gym.machines.upsert(press)
            gym.machines.upsert(squat)
            val link = MachineLink(MachineLinkId.random(), null, press.id, squat.id, t0, false)
            gym.machineLinks.upsert(link)
            val requests = gym.sync.requests

            assertTrue(catalogue.unlink(null, press.id))

            assertTrue(
                gym.machineLinks.rows
                    .getValue(link.id)
                    .deleted,
            )
            assertEquals(emptyList(), gym.friends.broken)
            assertEquals(0, gym.friends.reads)
            assertEquals(requests + 1, gym.sync.requests)
        }

    @Test
    fun unlinking_a_machine_only_friends_link_to_still_asks_for_a_sync() =
        runTest {
            olegsGym()
            val requests = gym.sync.requests

            assertTrue(catalogue.unlink(ME.userId, myPress.id))

            assertEquals(listOf(myPress.id), gym.friends.broken)
            assertEquals(emptyMap(), gym.machineLinks.rows)
            assertEquals(requests + 1, gym.sync.requests)
        }
}
