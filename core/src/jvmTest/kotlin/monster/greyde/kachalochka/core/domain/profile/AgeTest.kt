package monster.greyde.kachalochka.core.domain.profile

import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import kotlin.test.Test
import kotlin.test.assertEquals

class AgeTest {
    private val born = CalendarDay(1990, 6, 15)

    @Test
    fun age_counts_whole_years_and_turns_on_the_birthday() {
        assertEquals(35, ageOn(born, CalendarDay(2026, 6, 14)))
        assertEquals(36, ageOn(born, CalendarDay(2026, 6, 15)))
        assertEquals(36, ageOn(born, CalendarDay(2026, 12, 31)))
    }

    @Test
    fun a_leap_day_birthday_turns_on_the_first_of_march_in_other_years() {
        val leap = CalendarDay(2000, 2, 29)

        assertEquals(25, ageOn(leap, CalendarDay(2026, 2, 28)))
        assertEquals(26, ageOn(leap, CalendarDay(2026, 3, 1)))
    }
}
