package monster.greyde.kachalochka.core.domain.gym

import kotlin.test.Test
import kotlin.test.assertFalse
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
}
