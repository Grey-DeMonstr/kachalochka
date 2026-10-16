package monster.greyde.kachalochka.core.domain.measures

import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class CycleTest {
    private val oct1 = CalendarDay(2026, 10, 1)

    private fun day(offset: Long) = oct1.plusDays(offset)

    @Test
    fun a_recorded_day_of_the_cycle_says_when_the_cycle_began() {
        val anchors = cycleAnchors(mapOf(CalendarDay(2026, 10, 12) to 12.0, oct1 to 1.0))

        assertEquals(
            listOf(CycleAnchor(oct1, oct1), CycleAnchor(CalendarDay(2026, 10, 12), oct1)),
            anchors,
        )
    }

    @Test
    fun a_value_below_one_tells_no_start() {
        assertEquals(emptyList(), cycleAnchors(mapOf(oct1 to 0.0)))
    }

    @Test
    fun the_cycle_runs_on_after_the_last_value_and_back_before_the_first() {
        val cycle = Cycle(cycleAnchors(mapOf(day(4) to 5.0)), 28)

        assertEquals(1, cycle.dayOn(oct1))
        assertEquals(5, cycle.dayOn(day(4)))
        assertEquals(28, cycle.dayOn(day(27)))
        assertEquals(1, cycle.dayOn(day(28)))
        assertEquals(28, cycle.dayOn(day(-1)))
        assertEquals(1, cycle.dayOn(day(-28)))
    }

    @Test
    fun a_new_value_is_the_last_one_plus_the_days_since_wrapped_by_the_length() {
        val cycle = Cycle(cycleAnchors(mapOf(oct1 to 20.0)), 30)

        assertEquals(29, cycle.dayOn(day(9)))
        assertEquals(1, cycle.dayOn(day(11)))
        assertEquals(6, cycle.dayOn(day(16)))
    }

    @Test
    fun each_start_rules_until_the_next() {
        // A 28-day length, but the next cycle began on day 25.
        val cycle = Cycle(cycleAnchors(mapOf(oct1 to 1.0, day(26) to 2.0)), 28)

        assertEquals(24, cycle.dayOn(day(23)))
        assertEquals(1, cycle.dayOn(day(25)))
        assertEquals(2, cycle.dayOn(day(26)))
        assertEquals(4, cycle.dayOn(day(28)))
    }

    @Test
    fun the_value_recorded_last_rules_after_it_whatever_start_it_tells() {
        // Day 3 on 5 October, then day 10 on 9 October: a start four days earlier.
        val cycle = Cycle(cycleAnchors(mapOf(day(4) to 3.0, day(8) to 10.0)), 28)

        assertEquals(10, cycle.dayOn(day(8)))
        assertEquals(12, cycle.dayOn(day(10)))
    }

    @Test
    fun the_phases_are_days_one_to_five_and_the_three_days_twelve_to_fourteen_before_the_end() {
        val cycle = Cycle(cycleAnchors(mapOf(oct1 to 1.0)), 28)

        val bands = cycle.bands(day(-3), day(31))

        assertEquals(
            listOf(
                PhaseBand(CyclePhase.Menstruation, oct1, day(4)),
                PhaseBand(CyclePhase.Ovulation, day(13), day(15)),
                PhaseBand(CyclePhase.Menstruation, day(28), day(31)),
            ),
            bands,
        )
    }

    @Test
    fun a_band_cut_by_the_range_starts_with_the_range() {
        val cycle = Cycle(cycleAnchors(mapOf(oct1 to 1.0)), 28)

        assertEquals(
            listOf(PhaseBand(CyclePhase.Menstruation, day(2), day(4))),
            cycle.bands(day(2), day(10)),
        )
    }

    @Test
    fun the_ovulation_window_follows_the_length() {
        val cycle = Cycle(cycleAnchors(mapOf(oct1 to 1.0)), 35)

        assertEquals(
            listOf(PhaseBand(CyclePhase.Ovulation, day(20), day(22))),
            cycle.bands(day(10), day(30)),
        )
    }

    @Test
    fun in_a_short_cycle_menstruation_hides_the_ovulation_days_it_covers() {
        val cycle = Cycle(cycleAnchors(mapOf(oct1 to 1.0)), 18)

        assertEquals(
            listOf(
                PhaseBand(CyclePhase.Menstruation, oct1, day(4)),
                PhaseBand(CyclePhase.Ovulation, day(5), day(5)),
            ),
            cycle.bands(oct1, day(10)),
        )
    }

    @Test
    fun the_phases_show_for_a_month_three_months_and_all_values_within_three_months() {
        val today = CalendarDay(2026, 10, 16)

        assertTrue(showsCycle(MeasurePeriod.Month, today, today))
        assertTrue(showsCycle(MeasurePeriod.Quarter, today, today))
        assertFalse(showsCycle(MeasurePeriod.HalfYear, today, today))
        assertFalse(showsCycle(MeasurePeriod.Year, today, today))
        assertTrue(showsCycle(MeasurePeriod.All, CalendarDay(2026, 7, 17), today))
        assertFalse(showsCycle(MeasurePeriod.All, CalendarDay(2026, 7, 16), today))
    }

    @Test
    fun a_woman_s_recorded_days_of_the_cycle_make_her_cycle_of_the_profile_s_length() {
        val epoch = Instant.fromEpochSeconds(0)
        val woman = Profile.new(null, epoch).copy(sex = Sex.Female)
        val measures = missingDefaults(null, emptySet(), Sex.Female)
        val cycleDay = measures.single { it.kind == MeasureKind.CycleDay }
        val weight = measures.single { it.kind == MeasureKind.Weight }

        fun value(
            measure: Measure,
            day: CalendarDay,
            value: Double,
        ) = Measurement(MeasurementId.random(), null, measure.id, day, value, epoch, false)
        val values = listOf(value(cycleDay, day(4), 5.0), value(weight, day(4), 80.0))

        assertEquals(
            Cycle(listOf(CycleAnchor(day(4), oct1)), DEFAULT_CYCLE_LENGTH),
            cycleOf(woman, measures, values),
        )
        assertEquals(31, cycleOf(woman.copy(cycleLength = 31), measures, values)?.length)
        assertNull(cycleOf(woman.copy(sex = Sex.Male), measures, values))
        assertNull(cycleOf(null, measures, values))
        assertNull(cycleOf(woman, measures, values.drop(1)))
        assertNull(cycleOf(woman, measures - cycleDay, values))
    }
}
