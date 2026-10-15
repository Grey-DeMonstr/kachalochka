package monster.greyde.kachalochka.core.domain.gym

import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class DirectLinksTest {
    private val me = UserId("11111111-1111-4111-8111-111111111111")
    private val oleg = UserId("33333333-3333-4333-8333-333333333333")
    private val pasha = UserId("44444444-4444-4444-8444-444444444444")

    private val a = MachineId("a0000000-0000-4000-8000-00000000000a")
    private val a2 = MachineId("a2000000-0000-4000-8000-00000000000a")
    private val b = MachineId("b0000000-0000-4000-8000-00000000000b")
    private val c = MachineId("c0000000-0000-4000-8000-00000000000c")
    private val d = MachineId("d0000000-0000-4000-8000-00000000000d")
    private val now = T0 + 5.minutes

    private val ids =
        listOf(
            MachineLinkId("01000000-0000-4000-8000-000000000001"),
            MachineLinkId("02000000-0000-4000-8000-000000000002"),
            MachineLinkId("03000000-0000-4000-8000-000000000003"),
            MachineLinkId("04000000-0000-4000-8000-000000000004"),
        )

    private fun link(
        owner: UserId,
        from: MachineId,
        to: MachineId,
        deleted: Boolean = false,
    ) = MachineLink(MachineLinkId.random(), owner, from, to, T0, deleted)

    private fun missing(
        own: List<MachineId>,
        friends: List<MachineId>,
        links: List<MachineLink>,
    ): List<MachineLink> {
        val next = ids.iterator()
        return missingDirectLinks(me, own, friends, links, now) { next.next() }
    }

    @Test
    fun an_own_machine_links_to_every_friend_s_machine_of_its_cluster_it_has_no_link_with() {
        val links = listOf(link(me, a, b), link(oleg, b, c), link(pasha, d, b))

        val written = missing(listOf(a), listOf(b, c, d), links)

        assertEquals(
            listOf(
                MachineLink(ids[0], me, a, c, now, false),
                MachineLink(ids[1], me, a, d, now, false),
            ),
            written,
        )
    }

    @Test
    fun a_link_in_either_direction_counts_and_a_deleted_one_does_not() {
        val links =
            listOf(link(me, a, b), link(oleg, b, c), link(pasha, d, a), link(me, a, c, true))

        val written = missing(listOf(a), listOf(b, c, d), links)

        assertEquals(listOf(a to c), written.map { it.machineId to it.linkedMachineId })
    }

    @Test
    fun own_machines_of_one_cluster_link_to_the_friends_machines_but_never_to_each_other() {
        val links = listOf(link(me, a, b), link(me, a2, b), link(oleg, b, c))

        val written = missing(listOf(a2, a), listOf(b, c), links)

        assertEquals(
            listOf(a to c, a2 to c),
            written.map { it.machineId to it.linkedMachineId },
        )
    }

    @Test
    fun only_the_friends_machines_shown_are_linked_to() {
        val gone = MachineId.random()
        val links = listOf(link(me, a, b), link(oleg, b, gone))

        assertEquals(emptyList(), missing(listOf(a), listOf(b), links))
    }

    @Test
    fun a_machine_without_links_or_already_linked_to_its_whole_cluster_needs_nothing() {
        val links = listOf(link(me, a, b), link(oleg, b, c), link(me, a, c))

        assertEquals(emptyList(), missing(listOf(a, a2), listOf(b, c, d), links))
    }
}
