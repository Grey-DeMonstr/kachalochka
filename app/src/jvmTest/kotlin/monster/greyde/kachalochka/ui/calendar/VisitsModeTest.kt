package monster.greyde.kachalochka.ui.calendar

import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.ui.settings.InMemoryDeviceStore
import kotlin.test.Test
import kotlin.test.assertEquals

class VisitsModeTest {
    @Test
    fun a_stored_name_reads_back_and_anything_else_is_the_calendar() {
        assertEquals(VisitsMode.Feed, visitsModeOrCalendar("Feed"))
        assertEquals(VisitsMode.Calendar, visitsModeOrCalendar("Calendar"))
        assertEquals(VisitsMode.Calendar, visitsModeOrCalendar("List"))
        assertEquals(VisitsMode.Calendar, visitsModeOrCalendar(null))
    }

    @Test
    fun the_mode_kept_in_memory_starts_on_the_calendar_and_keeps_the_choice() =
        runTest {
            val preference = VisitsModePreference(InMemoryDeviceStore())
            assertEquals(VisitsMode.Calendar, preference.mode.value)

            preference.set(VisitsMode.Feed)

            assertEquals(VisitsMode.Feed, preference.mode.value)
        }
}
