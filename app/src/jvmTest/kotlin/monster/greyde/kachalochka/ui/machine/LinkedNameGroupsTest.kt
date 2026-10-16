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
        val press = linked(ME, "Жим")
        val pull = linked(OLEG, "Тяга")
        val pressToo = linked(PASHA, "Жим")
        val groups = groupLinkedNames(listOf(press, pull, pressToo), ownName = null)
        assertEquals(listOf("Жим", "Тяга"), groups.map { it.name })
        assertEquals(listOf(press, pressToo), groups[0].machines)
    }

    @Test
    fun names_differing_in_case_or_outer_spaces_are_one_name() {
        val groups =
            groupLinkedNames(listOf(linked(ME, "Жим"), linked(OLEG, " жим ")), ownName = null)
        assertEquals(listOf("Жим"), groups.map { it.name })
        assertEquals(2, groups.single().machines.size)
    }

    @Test
    fun the_name_of_the_shown_machine_is_not_written() {
        val press = linked(ME, "ЖИМ")
        val groups = groupLinkedNames(listOf(press, linked(OLEG, "Тяга")), ownName = "Жим")
        assertEquals(listOf(null, "Тяга"), groups.map { it.name })
        assertEquals(listOf(press), groups[0].machines)
    }
}
