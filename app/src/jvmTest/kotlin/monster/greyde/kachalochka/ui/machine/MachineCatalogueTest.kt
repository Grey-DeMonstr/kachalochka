package monster.greyde.kachalochka.ui.machine

import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkId
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.linkedCopy
import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import monster.greyde.kachalochka.ui.friends.PASHA
import monster.greyde.kachalochka.ui.friends.signedInGym
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

            assertEquals(OwnMachines(ME.userId, listOf(myPress), listOf(photo), listOf(link)), own)
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

    @Test
    fun taking_a_friend_s_machine_writes_the_account_s_copy_linked_to_it_and_syncs() =
        runTest {
            olegsGym()

            val copy = catalogue.take(ME.userId, olegRow)

            assertEquals(copy, gym.machines.rows[copy.id])
            assertEquals(ME.userId, copy.userId)
            assertEquals(olegRow.name, copy.name)
            val link =
                gym.machineLinks.rows.values
                    .single()
            assertEquals(copy.id, link.machineId)
            assertEquals(olegRow.id, link.linkedMachineId)
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
