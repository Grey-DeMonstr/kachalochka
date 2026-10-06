package monster.greyde.kachalochka.core.domain.friends

import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineClusters
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkId
import monster.greyde.kachalochka.core.domain.gym.PRESS
import monster.greyde.kachalochka.core.domain.gym.ROW
import monster.greyde.kachalochka.core.domain.gym.T0
import monster.greyde.kachalochka.core.domain.gym.VISIT_A
import monster.greyde.kachalochka.core.domain.gym.VISIT_B
import monster.greyde.kachalochka.core.domain.gym.VISIT_C
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.linkedCopy
import monster.greyde.kachalochka.core.domain.gym.machine
import monster.greyde.kachalochka.core.domain.gym.set
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals

class FriendResultsTest {
    private val me = UserId("11111111-1111-4111-8111-111111111111")
    private val oleg = Friend(UserId("33333333-3333-4333-8333-333333333333"), "Олег")
    private val pasha = Friend(UserId("44444444-4444-4444-8444-444444444444"), "Паша")

    private fun Friend.trained(
        visit: VisitId,
        weight: Double,
        atSeconds: Long,
    ): WorkoutSet = set(visit, weight, 8, atSeconds).copy(userId = userId)

    @Test
    fun each_friend_s_newest_set_names_their_latest_visit_newest_first() {
        val sets =
            listOf(
                oleg.trained(VISIT_A, 80.0, 0),
                pasha.trained(VISIT_C, 60.0, 3_600),
                oleg.trained(VISIT_B, 85.0, 86_400),
            )

        assertEquals(listOf(VISIT_B, VISIT_C), latestVisitsByMember(sets, limit = 3))
        assertEquals(listOf(VISIT_B), latestVisitsByMember(sets, limit = 1))
    }

    @Test
    fun a_result_holds_the_visit_s_sets_in_visit_order_under_its_friend_and_machine() {
        val later = oleg.trained(VISIT_B, 85.0, 120)
        val earlier = oleg.trained(VISIT_B, 80.0, 60)
        val older = oleg.trained(VISIT_A, 70.0, 0)
        val his = pasha.trained(VISIT_C, 60.0, 0)
        val press = machine(PRESS, "Жим ногами").copy(unit = WeightUnit.Lb)

        val results =
            friendResults(
                listOf(oleg, pasha),
                listOf(VISIT_B, VISIT_C),
                listOf(later, his, older, earlier),
                listOf(machine(ROW, "Тяга"), press),
            )

        assertEquals(
            listOf(
                FriendResult(oleg, press, listOf(earlier, later)),
                FriendResult(pasha, press, listOf(his)),
            ),
            results,
        )
    }

    @Test
    fun a_visit_of_someone_who_is_not_a_friend_is_left_out() {
        val stranger =
            set(VISIT_A, 70.0, 8, 0)
                .copy(userId = UserId("55555555-5555-4555-8555-555555555555"))

        assertEquals(
            emptyList(),
            friendResults(
                listOf(oleg),
                listOf(VISIT_A),
                listOf(stranger),
                listOf(machine(PRESS, "Жим ногами")),
            ),
        )
    }

    @Test
    fun a_friend_s_machine_linked_to_one_of_the_viewer_s_reads_under_the_viewer_s_name() {
        val mine = machine(PRESS, "Жим ногами")
        val (copy, link) = linkedCopy(mine, oleg.userId, T0)
        val theirLinked = copy.copy(name = "Платформа")
        val theirOwn = machine(ROW, "Тяга").copy(userId = oleg.userId)

        assertEquals(
            mapOf(theirLinked.id to "Жим ногами", theirOwn.id to "Тяга"),
            namesForViewer(
                listOf(theirLinked, theirOwn),
                listOf(mine),
                MachineClusters(listOf(link)),
            ),
        )
    }

    @Test
    fun a_friend_s_machine_linked_to_the_viewer_s_through_another_friend_reads_under_its_name() {
        val mine = machine(PRESS, "Жим ногами")
        val (olegs, olegLink) = linkedCopy(mine, oleg.userId, T0)
        val (pashas, pashaLink) = linkedCopy(olegs, pasha.userId, T0)

        assertEquals(
            mapOf(pashas.id to "Жим ногами"),
            namesForViewer(
                listOf(pashas.copy(name = "Платформа")),
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
        val mine = Machine.new("Жим ногами", me, T0)
        val (olegs, link) = linkedCopy(mine, oleg.userId, T0)

        assertEquals(
            emptyList(),
            rowsOf(listOf(FriendMachine(olegs, oleg)), listOf(mine), listOf(link)),
        )
    }

    @Test
    fun a_cluster_of_friends_machines_offers_the_one_without_links_of_its_own() {
        val pashas = pasha.owns("Жим ногами")
        val (olegs, link) = linkedCopy(pashas.machine, oleg.userId, T0)

        assertEquals(
            listOf(pashas),
            rowsOf(listOf(FriendMachine(olegs, oleg), pashas), emptyList(), listOf(link)),
        )
    }

    @Test
    fun equally_original_machines_are_offered_by_owner_name() {
        val olegs = oleg.owns("Жим ногами")
        val pashas = pasha.owns("Платформа")
        val links = listOf(pashas.linkedTo(olegs), olegs.linkedTo(pashas))

        assertEquals(listOf(olegs), rowsOf(listOf(pashas, olegs), emptyList(), links))
    }

    @Test
    fun unlinked_friends_machines_are_each_offered_by_name() {
        val row = oleg.owns("тяга")
        val press = pasha.owns("Жим ногами")

        assertEquals(listOf(press, row), rowsOf(listOf(row, press), emptyList(), emptyList()))
    }

    @Test
    fun every_friends_machine_is_offered_with_its_cluster_original_first() {
        val pashas = pasha.owns("Жим ногами")
        val (olegs, link) = linkedCopy(pashas.machine, oleg.userId, T0)
        val olegsCopy = FriendMachine(olegs, oleg)
        val row = oleg.owns("Гакк")
        val mine = Machine.new("Тяга", me, T0)
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
        val mine = Machine.new("Жим ногами", me, T0)
        val (pashas, pashaLink) = linkedCopy(mine, pasha.userId, T0)
        val (olegsPlatform, platformLink) = linkedCopy(pashas, oleg.userId, T0)
        val (olegsPress, pressLink) = linkedCopy(mine, oleg.userId, T0)
        val friendMachines =
            listOf(
                FriendMachine(pashas, pasha),
                FriendMachine(olegsPlatform.copy(name = "платформа"), oleg),
                FriendMachine(olegsPress.copy(name = "Жим"), oleg),
                oleg.owns("Тяга"),
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
        val mine = Machine.new("Жим ногами", me, T0)

        assertEquals(
            emptyList(),
            linkedFriendMachines(
                mine.id,
                listOf(oleg.owns("Жим ногами")),
                MachineClusters(emptyList()),
            ),
        )
    }
}
