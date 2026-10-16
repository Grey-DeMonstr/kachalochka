package monster.greyde.kachalochka.core.domain.gym

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NameSearchTest {
    @Test
    fun a_blank_query_matches_every_name() {
        assertTrue(nameMatches("Жим ногами", "  "))
    }

    @Test
    fun a_part_of_the_name_matches_in_any_letter_case() {
        assertTrue(nameMatches("Жим ногами", "НОГ"))
        assertTrue(nameMatches("Жим ногами", " жим "))
    }

    @Test
    fun up_to_three_letters_must_match_exactly() {
        assertFalse(nameMatches("Жим ногами", "жом"))
    }

    @Test
    fun four_or_five_letters_allow_one_typo() {
        assertTrue(nameMatches("Жим ногами", "нгами"))
        assertTrue(nameMatches("Жим ногами", "ногпм"))
        assertFalse(nameMatches("Жим ногами", "нкгпм"))
    }

    @Test
    fun six_to_eight_letters_allow_two_typos() {
        assertTrue(nameMatches("Разгибание ног", "розгебан"))
        assertFalse(nameMatches("Разгибание ног", "рзгбне"))
    }

    @Test
    fun nine_letters_or_more_allow_three_typos() {
        assertTrue(nameMatches("Приседания в Смите", "присидание в смит"))
        assertFalse(nameMatches("Приседания в Смите", "присидание в смету"))
        assertFalse(nameMatches("Тяга", "присидание"))
    }

    @Test
    fun the_matched_part_is_where_the_query_stands_in_the_name_in_any_letter_case() {
        assertEquals(4..6, matchedPart("Жим ногами", " НОГ "))
        assertEquals(0..2, matchedPart("Жим ногами", "жим"))
    }

    @Test
    fun the_matched_part_of_a_query_with_typos_is_the_closest_part_of_the_name() {
        assertEquals(0..7, matchedPart("Разгибание ног", "розгебан"))
        assertEquals(4..8, matchedPart("Жим ногами", "ногпм"))
    }

    @Test
    fun of_equally_close_parts_the_matched_one_ends_on_the_query_s_last_letter() {
        assertEquals(4..8, matchedPart("Жим ногами", "ногм"))
    }

    @Test
    fun a_blank_or_unmatched_query_has_no_matched_part() {
        assertNull(matchedPart("Жим ногами", "  "))
        assertNull(matchedPart("Жим ногами", "жом"))
    }
}
