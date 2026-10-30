package monster.greyde.kachalochka.ui.calendar

import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import kotlin.test.Test
import kotlin.test.assertEquals

class FeedPeopleTest {
    @Test
    fun a_stored_pair_reads_back_and_anything_else_is_left_out() {
        val selected = SelectedPerson(ME.userId, OLEG.userId)

        assertEquals(
            setOf(selected),
            selectedPeople(listOf(selected.stored(), "oleg", "a/b", "")),
        )
    }
}
