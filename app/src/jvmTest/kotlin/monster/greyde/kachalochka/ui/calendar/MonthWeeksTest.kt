package monster.greyde.kachalochka.ui.calendar

import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.CalendarMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MonthWeeksTest {
    @Test
    fun a_month_marks_visit_days_today_and_the_chosen_day_and_locks_the_future() {
        val weeks =
            monthWeeks(
                CalendarMonth(2023, 11),
                visitDays = setOf(CalendarDay(2023, 11, 12)),
                today = CalendarDay(2023, 11, 14),
                selected = CalendarDay(2023, 11, 10),
            )
        val days = weeks.flatten().filterNotNull().associateBy { it.day.day }

        assertEquals(2, weeks.first().count { it == null })
        assertEquals(true to false, days.getValue(12).hasVisit to days.getValue(11).hasVisit)
        assertTrue(days.getValue(14).today)
        assertTrue(days.getValue(10).selected)
        assertEquals(true to false, days.getValue(14).enabled to days.getValue(15).enabled)
    }
}
