package monster.greyde.kachalochka.core.domain.gym

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TagsTest {
    @Test
    fun tags_that_differ_only_in_letter_case_or_outer_spaces_are_one_tag() {
        assertTrue(sameTag("Ноги", " нОГИ "))
        assertFalse(sameTag("Ноги", "Руки"))
        assertEquals(tagKey("Ноги"), tagKey("НОГИ "))
    }

    @Test
    fun a_key_is_trimmed_with_inner_spaces_joined_to_one_in_lower_case() {
        assertEquals("жим лёжа", tagKey(" Жим \t  ЛЁЖА\n"))
        assertTrue(sameTag("жим лёжа", "Жим  лёжа"))
    }

    @Test
    fun each_tag_is_kept_once_as_met_first() {
        assertEquals(listOf("Legs", "Arms"), distinctTags(listOf("Legs", "Arms", "legs", "ARMS")))
    }

    @Test
    fun sorted_tags_are_distinct_and_in_alphabetical_order_without_letter_case() {
        assertEquals(listOf("biceps", "Legs"), sortedTags(listOf("Legs", "biceps", "LEGS")))
    }

    @Test
    fun a_tag_set_holds_a_tag_in_any_letter_case() {
        val tags = setOf("Legs", "Press")

        assertTrue(hasTag(tags, "legs"))
        assertFalse(hasTag(tags, "Back"))
        assertTrue(hasAllTags(tags, setOf("LEGS", "press")))
        assertFalse(hasAllTags(tags, setOf("legs", "Back")))
    }

    @Test
    fun a_toggle_takes_out_the_tag_in_any_case_or_else_adds_it() {
        assertEquals(setOf("Press"), toggledTag(setOf("Legs", "Press"), "LEGS"))
        assertEquals(setOf("Legs", "Back"), toggledTag(setOf("Legs"), "Back"))
    }

    @Test
    fun linked_machines_suggest_each_tag_the_machine_lacks_once() {
        assertEquals(
            listOf("arms", "legs"),
            suggestedTags(linked = listOf("legs", "back", "arms", "legs"), tags = setOf("back")),
        )
        assertEquals(emptyList(), suggestedTags(listOf("legs"), setOf("legs")))
    }
}
