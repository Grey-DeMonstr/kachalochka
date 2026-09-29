package monster.greyde.kachalochka.ui.strings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class LanguageTest {
    @Test
    fun the_system_language_picks_russian_only_for_russian() {
        assertSame(RuStrings, AppLanguage.System.strings("ru"))
        assertSame(RuStrings, AppLanguage.System.strings("ru-RU"))
        assertSame(EnStrings, AppLanguage.System.strings("en-US"))
        assertSame(EnStrings, AppLanguage.System.strings("de"))
    }

    @Test
    fun a_chosen_language_ignores_the_system() {
        assertSame(EnStrings, AppLanguage.English.strings("ru"))
        assertSame(RuStrings, AppLanguage.Russian.strings("en"))
    }

    @Test
    fun an_unknown_stored_name_reads_as_the_system_language() {
        assertEquals(AppLanguage.English, languageOrSystem("English"))
        assertEquals(AppLanguage.System, languageOrSystem("Klingon"))
        assertEquals(AppLanguage.System, languageOrSystem(null))
    }

    @Test
    fun counts_follow_each_language_s_plural_rules() {
        assertEquals(
            listOf(
                "1 подход",
                "2 подхода",
                "5 подходов",
                "11 подходов",
                "21 подход",
                "22 подхода",
            ),
            listOf(1, 2, 5, 11, 21, 22).map(RuStrings::sets),
        )
        assertEquals(listOf("1 set", "2 sets"), listOf(1, 2).map(EnStrings::sets))
        assertEquals("3 days ago", EnStrings.daysAgo(3))
    }

    @Test
    fun the_app_s_strings_switch_as_a_whole() {
        try {
            AppStrings.set(EnStrings)
            assertSame(EnStrings, AppStrings.current)
        } finally {
            AppStrings.set(RuStrings)
        }
        assertSame(RuStrings, AppStrings.flow.value)
    }
}
