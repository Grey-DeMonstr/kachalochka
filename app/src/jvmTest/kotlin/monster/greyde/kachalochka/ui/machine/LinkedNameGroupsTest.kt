package monster.greyde.kachalochka.ui.machine

import monster.greyde.kachalochka.core.domain.friends.Friend
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import monster.greyde.kachalochka.ui.friends.PASHA
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class LinkedNameGroupsTest {
    private fun linked(
        owner: Friend,
        name: String,
    ) = LinkedMachineUi(
        Machine.new(name, owner.userId, Instant.fromEpochSeconds(0)).id,
        owner,
        name,
    )

    @Test
    fun machines_with_one_name_share_it() {
        val press = linked(ME, "Press")
        val pull = linked(OLEG, "Row")
        val pressToo = linked(PASHA, "Press")
        val groups = groupLinkedNames(listOf(press, pull, pressToo), ownName = null)
        assertEquals(listOf("Press", "Row"), groups.map { it.name })
        assertEquals(listOf(press, pressToo), groups[0].machines)
    }

    @Test
    fun names_differing_in_case_or_outer_spaces_are_one_name() {
        val groups =
            groupLinkedNames(listOf(linked(ME, "Press"), linked(OLEG, " press ")), ownName = null)
        assertEquals(listOf("Press"), groups.map { it.name })
        assertEquals(2, groups.single().machines.size)
    }

    @Test
    fun the_name_of_the_shown_machine_is_not_written() {
        val press = linked(ME, "PRESS")
        val groups = groupLinkedNames(listOf(press, linked(OLEG, "Row")), ownName = "Press")
        assertEquals(listOf(null, "Row"), groups.map { it.name })
        assertEquals(listOf(press), groups[0].machines)
    }
}
