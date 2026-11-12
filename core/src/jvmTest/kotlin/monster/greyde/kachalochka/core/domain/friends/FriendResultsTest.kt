package monster.greyde.kachalochka.core.domain.friends

import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineClusters
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkId
import monster.greyde.kachalochka.core.domain.gym.MachinePeaks
import monster.greyde.kachalochka.core.domain.gym.PRESS
import monster.greyde.kachalochka.core.domain.gym.ROW
import monster.greyde.kachalochka.core.domain.gym.SetPeak
import monster.greyde.kachalochka.core.domain.gym.T0
import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.linkedCopy
import monster.greyde.kachalochka.core.domain.gym.machine
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals

class FriendResultsTest {
    private val me = UserId("11111111-1111-4111-8111-111111111111")
    private val oleg = Friend(UserId("33333333-3333-4333-8333-333333333333"), "Oleg")
    private val pasha = Friend(UserId("44444444-4444-4444-8444-444444444444"), "Pasha")

    @Test
    fun a_friend_s_machine_linked_to_one_of_the_viewer_s_reads_under_the_viewer_s_name() {
        val mine = machine(PRESS, "Leg press")
        val (copy, link) = linkedCopy(mine, oleg.userId, T0)
        val theirLinked = copy.copy(name = "Platform")
        val theirOwn = machine(ROW, "Row").copy(userId = oleg.userId)

        assertEquals(
            mapOf(theirLinked.id to "Leg press", theirOwn.id to "Row"),
            namesForViewer(
                listOf(theirLinked, theirOwn),
                listOf(mine),
                MachineClusters(listOf(link)),
            ),
        )
    }

    @Test
    fun a_friend_s_machine_linked_to_the_viewer_s_through_another_friend_reads_under_its_name() {
        val mine = machine(PRESS, "Leg press")
        val (olegs, olegLink) = linkedCopy(mine, oleg.userId, T0)
        val (pashas, pashaLink) = linkedCopy(olegs, pasha.userId, T0)

        assertEquals(
            mapOf(pashas.id to "Leg press"),
            namesForViewer(
                listOf(pashas.copy(name = "Platform")),
                listOf(mine),
                MachineClusters(listOf(olegLink, pashaLink)),
            ),
        )
    }

    private fun Friend.owns(name: String) = FriendMachine(Machine.new(name, userId, T0), this)

    private fun FriendMachine.linkedTo(other: FriendMachine) =
        MachineLink(MachineLinkId.random(), owner.userId, machine.id, other.machine.id, T0, false)

    private fun rowsOf(
        friendMachines: List<FriendMachine>,
        own: List<Machine>,
        links: List<MachineLink>,
    ) = friendMachineRows(friendMachines, own, MachineClusters(links), links)

    @Test
    fun a_cluster_holding_an_own_machine_offers_no_friend_row() {
        val mine = Machine.new("Leg press", me, T0)
        val (olegs, link) = linkedCopy(mine, oleg.userId, T0)

        assertEquals(
            emptyList(),
            rowsOf(listOf(FriendMachine(olegs, oleg)), listOf(mine), listOf(link)),
        )
    }

    @Test
    fun a_cluster_of_friends_machines_offers_the_one_without_links_of_its_own() {
        val pashas = pasha.owns("Leg press")
        val (olegs, link) = linkedCopy(pashas.machine, oleg.userId, T0)

        assertEquals(
            listOf(pashas),
            rowsOf(listOf(FriendMachine(olegs, oleg), pashas), emptyList(), listOf(link)),
        )
    }

    @Test
    fun equally_original_machines_are_offered_by_owner_name() {
        val olegs = oleg.owns("Leg press")
        val pashas = pasha.owns("Platform")
        val links = listOf(pashas.linkedTo(olegs), olegs.linkedTo(pashas))

        assertEquals(listOf(olegs), rowsOf(listOf(pashas, olegs), emptyList(), links))
    }

    @Test
    fun unlinked_friends_machines_are_each_offered_by_name() {
        val row = oleg.owns("row")
        val press = pasha.owns("Leg press")

        assertEquals(listOf(press, row), rowsOf(listOf(row, press), emptyList(), emptyList()))
    }

    @Test
    fun every_friends_machine_is_offered_with_its_cluster_original_first() {
        val pashas = pasha.owns("Leg press")
        val (olegs, link) = linkedCopy(pashas.machine, oleg.userId, T0)
        val olegsCopy = FriendMachine(olegs, oleg)
        val row = oleg.owns("Hack squat")
        val mine = Machine.new("Row", me, T0)
        val (olegsRow, rowLink) = linkedCopy(mine, oleg.userId, T0)

        assertEquals(
            listOf(listOf(row), listOf(pashas, olegsCopy)),
            friendMachineGroups(
                listOf(olegsCopy, row, pashas, FriendMachine(olegsRow, oleg)),
                listOf(mine),
                MachineClusters(listOf(link, rowLink)),
                listOf(link, rowLink),
            ),
        )
    }

    @Test
    fun a_machine_is_linked_with_its_cluster_s_friends_machines_by_owner_then_name() {
        val mine = Machine.new("Leg press", me, T0)
        val (pashas, pashaLink) = linkedCopy(mine, pasha.userId, T0)
        val (olegsPlatform, platformLink) = linkedCopy(pashas, oleg.userId, T0)
        val (olegsPress, pressLink) = linkedCopy(mine, oleg.userId, T0)
        val friendMachines =
            listOf(
                FriendMachine(pashas, pasha),
                FriendMachine(olegsPlatform.copy(name = "platform"), oleg),
                FriendMachine(olegsPress.copy(name = "Leg press"), oleg),
                oleg.owns("Row"),
            )

        assertEquals(
            listOf(friendMachines[2], friendMachines[1], friendMachines[0]),
            linkedFriendMachines(
                mine.id,
                friendMachines,
                MachineClusters(listOf(pashaLink, platformLink, pressLink)),
            ),
        )
    }

    @Test
    fun an_unlinked_machine_is_linked_with_no_friend_s_machine() {
        val mine = Machine.new("Leg press", me, T0)

        assertEquals(
            emptyList(),
            linkedFriendMachines(
                mine.id,
                listOf(oleg.owns("Leg press")),
                MachineClusters(emptyList()),
            ),
        )
    }

    private fun peaksOf(
        machine: Machine,
        heaviest: SetPeak,
        lightest: SetPeak = heaviest,
    ) = MachinePeaks(machine.id, heaviest, lightest, T0, 1)

    @Test
    fun every_friend_with_a_linked_machine_that_has_sets_gets_a_record_by_name() {
        val mine = Machine.new("Leg press", me, T0)
        val names = listOf("Vera", "Anna", "Oleg", "Pasha", "Boris")
        val friends =
            names.mapIndexed {
                i,
                name,
                ->
                Friend(UserId("0000000$i-0000-4000-8000-000000000000"), name)
            }
        val linked = friends.map { linkedCopy(mine, it.userId, T0) }
        val friendMachines = friends.zip(linked).map { (f, l) -> FriendMachine(l.first, f) }
        val peaks = friendMachines.map { peaksOf(it.machine, SetPeak(80.0, 6)) }

        val records =
            friendRecords(
                mine.id,
                friendMachines,
                MachineClusters(linked.map { it.second }),
                peaks,
            )

        assertEquals(
            listOf("Anna", "Boris", "Oleg", "Pasha", "Vera"),
            records.map { it.friend.displayName },
        )
        assertEquals(List(5) { SetPeak(80.0, 6) }, records.map { it.record })
    }

    @Test
    fun a_linked_machine_without_sets_gets_no_record() {
        val mine = Machine.new("Leg press", me, T0)
        val (olegs, link) = linkedCopy(mine, oleg.userId, T0)

        assertEquals(
            emptyList(),
            friendRecords(
                mine.id,
                listOf(FriendMachine(olegs, oleg)),
                MachineClusters(listOf(link)),
                emptyList(),
            ),
        )
    }

    @Test
    fun a_counterweight_machine_gives_its_lightest_set() {
        val mine = Machine.new("Pull-up", me, T0)
        val (copy, link) = linkedCopy(mine, oleg.userId, T0)
        val olegs = copy.copy(weightMode = WeightMode.Counterweight)

        val records =
            friendRecords(
                mine.id,
                listOf(FriendMachine(olegs, oleg)),
                MachineClusters(listOf(link)),
                listOf(peaksOf(olegs, SetPeak(40.0, 8), SetPeak(10.0, 5))),
            )

        assertEquals(listOf(FriendRecord(oleg, olegs, SetPeak(10.0, 5))), records)
    }
}
