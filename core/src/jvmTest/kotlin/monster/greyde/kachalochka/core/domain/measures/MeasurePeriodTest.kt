package monster.greyde.kachalochka.core.domain.measures

import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class MeasurePeriodTest {
    private val today = CalendarDay(2026, 9, 28)
    private val weight = MeasureId("11111111-1111-4111-8111-111111111111")

    private fun value(day: CalendarDay) =
        Measurement(
            MeasurementId.random(),
            null,
            weight,
            day,
            day.day.toDouble(),
            Instant.fromEpochSeconds(0),
            false,
        )

    private fun days(values: List<Measurement>) = values.map { it.day }

    @Test
    fun a_month_starts_on_the_same_day_of_the_previous_month() {
        val values =
            listOf(today, CalendarDay(2026, 8, 28), CalendarDay(2026, 8, 27)).map(::value)

        assertEquals(
            listOf(CalendarDay(2026, 8, 28), today),
            days(inPeriod(values, MeasurePeriod.Month, today)),
        )
    }

    @Test
    fun a_period_ending_past_a_short_month_starts_on_its_last_day() {
        val values =
            listOf(CalendarDay(2026, 2, 28), CalendarDay(2026, 2, 27)).map(::value)

        assertEquals(
            listOf(CalendarDay(2026, 2, 28)),
            days(inPeriod(values, MeasurePeriod.Month, CalendarDay(2026, 3, 31))),
        )
    }

    @Test
    fun longer_periods_reach_back_their_months() {
        val values =
            listOf(
                CalendarDay(2026, 6, 28),
                CalendarDay(2026, 6, 27),
                CalendarDay(2026, 3, 28),
                CalendarDay(2025, 9, 28),
                CalendarDay(2025, 9, 27),
            ).map(::value)

        assertEquals(1, inPeriod(values, MeasurePeriod.Quarter, today).size)
        assertEquals(3, inPeriod(values, MeasurePeriod.HalfYear, today).size)
        assertEquals(4, inPeriod(values, MeasurePeriod.Year, today).size)
    }

    @Test
    fun all_keeps_every_value_oldest_first() {
        val values =
            listOf(today, CalendarDay(2020, 1, 1), CalendarDay(2026, 8, 1)).map(::value)

        assertEquals(
            listOf(CalendarDay(2020, 1, 1), CalendarDay(2026, 8, 1), today),
            days(inPeriod(values, MeasurePeriod.All, today)),
        )
    }

    @Test
    fun periods_are_one_three_six_and_twelve_months_and_everything() {
        assertEquals(
            listOf(1, 3, 6, 12, null),
            MeasurePeriod.entries.map { it.months },
        )
    }
}
