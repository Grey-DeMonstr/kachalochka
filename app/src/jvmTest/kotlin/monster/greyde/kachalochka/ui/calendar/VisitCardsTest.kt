package monster.greyde.kachalochka.ui.calendar

import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class VisitCardsTest {
    private val t0 = Instant.parse("2023-11-14T10:00:00Z")
    private val press = Machine.new("Жим ногами", null, t0)
    private val row = Machine.new("Тяга верхнего блока", null, t0)
    private val gravitron =
        Machine.new("Гравитрон", null, t0).copy(weightMode = WeightMode.Counterweight)
    private val machines = listOf(press, row, gravitron).associateBy { it.id }
    private val today = VisitId.random()
    private val earlier = VisitId.random()
    private val older = VisitId.random()
    private val days =
        mapOf(
            today to CalendarDay(2023, 11, 14),
            earlier to CalendarDay(2023, 11, 12),
            older to CalendarDay(2023, 11, 1),
        )

    private fun set(
        visit: VisitId,
        machine: Machine,
        weight: Double,
        reps: Int,
        position: Int = 0,
    ) = WorkoutSet(
        WorkoutSetId.random(),
        null,
        visit,
        machine.id,
        weight,
        reps,
        position,
        t0 + position.minutes,
        t0,
        false,
    )

    private fun rows(
        visitSets: List<WorkoutSet>,
        history: List<WorkoutSet> = visitSets,
        preferred: PreferredWeightUnit = PreferredWeightUnit.Kg,
    ): List<ExerciseUi> {
        val own = OwnHistory(history, days)
        return exerciseRows(
            visitSets,
            machines,
            preferred,
        ) { own.before(it, days.getValue(today)) }
    }

    @Test
    fun each_machine_shows_its_best_set_of_the_day_in_visit_order() {
        val sets =
            listOf(
                set(today, row, 40.0, 12, position = 0),
                set(today, press, 70.0, 10, position = 1),
                set(today, press, 80.0, 8, position = 2),
                set(today, press, 80.0, 6, position = 3),
            )

        assertEquals(
            listOf(
                ExerciseUi("Тяга верхнего блока", "40 кг × 12", null),
                ExerciseUi("Жим ногами", "80 кг × 8", null),
            ),
            rows(sets),
        )
    }

    @Test
    fun a_heavier_best_than_on_all_earlier_days_shows_the_weight_gained() {
        val sets = listOf(set(today, press, 82.5, 8))
        val history = sets + set(earlier, press, 70.0, 10) + set(older, press, 80.0, 6)

        assertEquals("+2.5 кг", rows(sets, history).single().growth)
    }

    @Test
    fun at_the_same_weight_more_reps_than_ever_before_show_the_reps_gained() {
        val sets = listOf(set(today, press, 80.0, 10))
        val history = sets + set(earlier, press, 80.0, 8) + set(older, press, 80.0, 7)

        assertEquals("+2 повт.", rows(sets, history).single().growth)
    }

    @Test
    fun no_gain_shows_for_a_weaker_or_equal_day_or_for_the_first_day_on_a_machine() {
        val weaker = listOf(set(today, press, 75.0, 12))
        val equal = listOf(set(today, row, 40.0, 10))
        val first = listOf(set(today, gravitron, 30.0, 8))
        val history =
            weaker + equal + first + set(older, press, 80.0, 6) + set(earlier, row, 40.0, 10)

        assertEquals(
            listOf(null, null, null),
            rows(weaker + equal + first, history).map { it.growth },
        )
    }

    @Test
    fun the_day_s_other_sets_and_later_days_are_not_earlier() {
        val sets = listOf(set(today, press, 70.0, 10), set(today, press, 75.0, 10, position = 1))
        val later = VisitId.random()
        val own =
            OwnHistory(
                sets + set(later, press, 90.0, 10),
                days + (later to CalendarDay(2023, 11, 15)),
            )

        val rows =
            exerciseRows(sets, machines, PreferredWeightUnit.Kg) {
                own.before(it, days.getValue(today))
            }

        assertEquals(null, rows.single().growth)
    }

    @Test
    fun on_a_gravitron_less_counterweight_is_the_gain() {
        val sets = listOf(set(today, gravitron, 25.0, 8))
        val history = sets + set(earlier, gravitron, 30.0, 8)

        val shown = rows(sets, history).single()
        assertEquals("(-)25 кг × 8", shown.best)
        assertEquals("+5 кг", shown.growth)
    }

    @Test
    fun weights_and_gains_show_in_the_viewer_s_unit() {
        val inPounds = press.copy(unit = WeightUnit.Lb)
        val sets = listOf(set(today, inPounds, 110.0, 8))
        val own = OwnHistory(sets + set(earlier, inPounds, 100.0, 8), days)

        val shown =
            exerciseRows(sets, mapOf(press.id to inPounds), PreferredWeightUnit.Kg) {
                own.before(it, days.getValue(today))
            }.single()

        assertEquals("50 кг × 8", shown.best)
        assertEquals("+4.5 кг", shown.growth)
    }

    @Test
    fun a_machine_the_viewer_cannot_read_is_left_out() {
        val unknown = set(today, press, 70.0, 10).copy(machineId = MachineId.random())

        assertEquals(emptyList(), rows(listOf(unknown)))
    }
}
