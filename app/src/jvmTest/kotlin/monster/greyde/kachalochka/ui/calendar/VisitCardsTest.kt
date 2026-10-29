package monster.greyde.kachalochka.ui.calendar

import monster.greyde.kachalochka.core.domain.gym.DayBest
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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

    private fun set(
        visit: VisitId,
        machine: Machine,
        weight: Double,
        reps: Int,
        position: Int = 0,
        prior: DayBest? = null,
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
        priorBest = prior,
    )

    private fun rows(
        visitSets: List<WorkoutSet>,
        preferred: PreferredWeightUnit = PreferredWeightUnit.Kg,
    ): List<ExerciseUi> = exerciseRows(visitSets, machines, preferred)

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
    fun a_heavier_best_than_the_prior_shows_the_weight_gained() {
        val sets = listOf(set(today, press, 82.5, 8, prior = DayBest(80.0, 6, 1)))

        assertEquals("+2.5 кг", rows(sets).single().growth)
    }

    @Test
    fun at_the_same_weight_more_reps_show_the_reps_gained() {
        val sets = listOf(set(today, press, 80.0, 10, prior = DayBest(80.0, 8, 2)))

        assertEquals("+2 повт.", rows(sets).single().growth)
    }

    @Test
    fun at_the_same_best_set_more_sets_show_the_sets_gained() {
        val prior = DayBest(16.0, 10, 1)
        val sets =
            listOf(
                set(today, press, 16.0, 10, prior = prior),
                set(today, press, 16.0, 10, position = 1, prior = prior),
            )

        assertEquals("+1 подход", rows(sets).single().growth)
    }

    @Test
    fun a_second_best_weight_set_with_fewer_reps_is_no_gain() {
        val prior = DayBest(16.0, 10, 1)
        val sets =
            listOf(
                set(today, press, 16.0, 10, prior = prior),
                set(today, press, 16.0, 8, position = 1, prior = prior),
            )

        assertEquals(null, rows(sets).single().growth)
    }

    @Test
    fun a_weaker_or_equal_day_shows_no_gain() {
        val weaker = set(today, press, 75.0, 12, prior = DayBest(80.0, 6, 1))
        val fewerSets = set(today, row, 16.0, 10, prior = DayBest(16.0, 10, 2))

        assertEquals(listOf(null, null), rows(listOf(weaker, fewerSets)).map { it.growth })
    }

    @Test
    fun the_first_day_on_a_machine_is_marked_new() {
        val shown = rows(listOf(set(today, gravitron, 30.0, 8, prior = DayBest.NONE))).single()

        assertEquals(null, shown.growth)
        assertTrue(shown.isNew)
    }

    @Test
    fun a_set_without_a_computed_prior_best_shows_neither_gain_nor_chip() {
        val shown = rows(listOf(set(today, press, 80.0, 8))).single()

        assertEquals(null, shown.growth)
        assertFalse(shown.isNew)
    }

    @Test
    fun on_a_gravitron_less_counterweight_is_the_gain() {
        val shown =
            rows(
                listOf(set(today, gravitron, 25.0, 8, prior = DayBest(30.0, 8, 1))),
            ).single()

        assertEquals("(-)25 кг × 8", shown.best)
        assertEquals("+5 кг", shown.growth)
    }

    @Test
    fun weights_and_gains_show_in_the_viewer_s_unit() {
        val inPounds = press.copy(unit = WeightUnit.Lb)
        val sets = listOf(set(today, inPounds, 110.0, 8, prior = DayBest(100.0, 8, 1)))

        val shown =
            exerciseRows(
                sets,
                mapOf(press.id to inPounds),
                PreferredWeightUnit.Kg,
            ).single()

        assertEquals("50 кг × 8", shown.best)
        assertEquals("+4.5 кг", shown.growth)
    }

    @Test
    fun a_machine_the_viewer_cannot_read_is_left_out() {
        val unknown = set(today, press, 70.0, 10).copy(machineId = MachineId.random())

        assertEquals(emptyList(), rows(listOf(unknown)))
    }
}
