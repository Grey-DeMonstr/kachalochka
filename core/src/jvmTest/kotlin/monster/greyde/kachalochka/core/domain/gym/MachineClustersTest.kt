package monster.greyde.kachalochka.core.domain.gym

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MachineClustersTest {
    private val a = MachineId.random()
    private val b = MachineId.random()
    private val c = MachineId.random()
    private val d = MachineId.random()

    private fun link(
        from: MachineId,
        to: MachineId,
        deleted: Boolean = false,
    ) = MachineLink(MachineLinkId.random(), null, from, to, T0, deleted)

    @Test
    fun links_join_machines_through_any_number_of_hops() {
        val clusters = MachineClusters(listOf(link(a, b), link(b, c)))

        assertEquals(setOf(a, b, c), clusters.of(a))
        assertEquals(setOf(a, b, c), clusters.of(c))
        assertTrue(clusters.sameMachine(a, c))
    }

    @Test
    fun a_deleted_link_joins_nothing() {
        val clusters = MachineClusters(listOf(link(a, b, deleted = true)))

        assertEquals(setOf(a), clusters.of(a))
        assertFalse(clusters.sameMachine(a, b))
    }

    @Test
    fun a_machine_without_links_is_a_cluster_of_its_own() {
        val clusters = MachineClusters(listOf(link(a, b)))

        assertEquals(setOf(d), clusters.of(d))
        assertTrue(clusters.sameMachine(d, d))
    }

    @Test
    fun a_link_joins_both_ways() {
        val clusters = MachineClusters(listOf(link(a, b), link(c, b)))

        assertEquals(setOf(a, b, c), clusters.of(b))
        assertTrue(clusters.sameMachine(c, a))
    }

    @Test
    fun a_cycle_of_links_still_ends() {
        val clusters = MachineClusters(listOf(link(a, b), link(b, c), link(c, a)))

        assertEquals(setOf(a, b, c), clusters.of(b))
        assertFalse(clusters.sameMachine(a, d))
    }
}
