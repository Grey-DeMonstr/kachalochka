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
    fun each_tag_is_kept_once_in_the_spelling_met_first() {
        assertEquals(listOf("Ноги", "Руки"), distinctTags(listOf("Ноги", "Руки", "ноги", "РУКИ")))
    }

    @Test
    fun sorted_tags_are_distinct_and_in_alphabetical_order_without_letter_case() {
        assertEquals(listOf("бицепс", "Ноги"), sortedTags(listOf("Ноги", "бицепс", "НОГИ")))
    }

    @Test
    fun a_tag_takes_the_known_spelling_when_one_is_known() {
        val known = listOf("Ноги", "Спина")

        assertEquals("Ноги", spelledAs("НОГИ", known))
        assertEquals("Грудь", spelledAs("Грудь", known))
        assertEquals(setOf("Ноги", "Грудь"), tagsSpelledAs(setOf("ноги", "Грудь"), known))
    }

    @Test
    fun a_tag_set_holds_a_tag_in_any_letter_case() {
        val tags = setOf("Ноги", "Жим")

        assertTrue(hasTag(tags, "ноги"))
        assertFalse(hasTag(tags, "Спина"))
        assertTrue(hasAllTags(tags, setOf("НОГИ", "жим")))
        assertFalse(hasAllTags(tags, setOf("ноги", "Спина")))
    }

    @Test
    fun a_toggle_takes_out_the_tag_in_any_case_or_else_adds_it() {
        assertEquals(setOf("Жим"), toggledTag(setOf("Ноги", "Жим"), "НОГИ"))
        assertEquals(setOf("Ноги", "Спина"), toggledTag(setOf("Ноги"), "Спина"))
    }

    @Test
    fun linked_machines_suggest_each_tag_the_machine_lacks_once_in_the_account_s_spelling() {
        assertEquals(
            listOf("Жим", "Ноги"),
            suggestedTags(
                linked = listOf("ноги", "спина", "Жим", "НОГИ"),
                tags = setOf("СПИНА"),
                known = setOf("Ноги", "Спина"),
            ),
        )
        assertEquals(emptyList(), suggestedTags(listOf("ноги"), setOf("Ноги"), setOf("Ноги")))
    }
}
