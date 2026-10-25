package monster.greyde.kachalochka.ui.visit

import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.SetValues
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val t0 = Instant.fromEpochSeconds(1_700_000_000)

private fun set(
    weight: Double,
    reps: Int,
    comment: String = "",
) = WorkoutSet(
    WorkoutSetId.random(),
    null,
    VisitId.random(),
    MachineId.random(),
    weight,
    reps,
    0,
    t0,
    t0,
    false,
    comment,
)

class SetFormTest {
    private val form = SetForm().apply { suggest(SetValues(50.0, 10)) }

    @Test
    fun a_typed_weight_shows_as_typed_and_counts_rounded() {
        form.typeWeight("52,25")

        assertEquals("52,25", form.weightShown)
        assertTrue(form.valid)
        assertEquals(52.25, form.values.weight)
    }

    @Test
    fun a_negative_or_unreadable_weight_is_not_valid() {
        form.typeWeight("-5")
        assertFalse(form.valid)

        form.typeWeight("abc")
        assertFalse(form.valid)
        assertEquals(50.0, form.values.weight)
    }

    @Test
    fun reps_are_valid_from_one_to_the_maximum() {
        form.typeReps("0")
        assertFalse(form.valid)

        form.typeReps("1000")
        assertFalse(form.valid)

        form.typeReps("12")
        assertTrue(form.valid)
        assertEquals(12, form.values.reps)
    }

    @Test
    fun a_step_drops_what_was_typed() {
        form.typeWeight("abc")
        form.changeWeight(2.5, 1)

        assertEquals(52.5, form.values.weight)
        assertTrue(form.valid)
    }

    @Test
    fun a_comment_is_cut_at_its_length() {
        form.typeComment("x".repeat(COMMENT_LENGTH + 5))

        assertEquals(COMMENT_LENGTH, form.comment.length)
    }

    @Test
    fun an_edit_starts_from_the_set_and_a_close_drops_it() {
        val edited = set(70.0, 8, "тяжело")

        form.openOn(edited)
        assertTrue(form.isOpen)
        assertEquals(edited, form.editing)
        assertEquals(SetValues(70.0, 8), form.values)
        assertEquals("тяжело", form.comment)

        form.close()
        assertFalse(form.isOpen)
        assertNull(form.editing)
        assertEquals("", form.comment)
    }

    @Test
    fun a_new_set_keeps_the_values_but_not_the_comment() {
        form.typeComment("старое")

        form.openNew()

        assertTrue(form.isOpen)
        assertNull(form.editing)
        assertEquals("", form.comment)
        assertEquals(SetValues(50.0, 10), form.values)
    }
}
