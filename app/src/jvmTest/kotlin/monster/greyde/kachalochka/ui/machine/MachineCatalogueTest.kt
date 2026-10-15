package monster.greyde.kachalochka.ui.machine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkId
import monster.greyde.kachalochka.core.domain.gym.MachineLinkRepository
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.linkedCopy
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import monster.greyde.kachalochka.ui.friends.PASHA
import monster.greyde.kachalochka.ui.friends.signedInGym
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    private val catalogue =
        MachineCatalogue(
            gym.machines,
            gym.photos,
            gym.machineLinks,
            gym.friends,
            gym.clock,
            gym.sync,
            gym.accounts,
            gym.avatars,
        )

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
            val slow =
                MachineCatalogue(
                    gym.machines,
                    gym.photos,
                    slowLinks,
                    gym.friends,
                    gym.clock,
                    gym.sync,
                    gym.accounts,
                    gym.avatars,
                )

            val reads = List(2) { launch { slow.group(ME.userId) } }
            testScheduler.advanceUntilIdle()
            gate.complete(Unit)
            reads.joinAll()

            assertEquals(1, gym.machineLinks.rows.size)
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
    fun a_group_read_fails_when_the_network_does_not_answer() =
        runTest {
            olegsGym()
            gym.friends.offline = true

            assertFailsWith<IllegalStateException> { catalogue.group(ME.userId) }
        }
}
