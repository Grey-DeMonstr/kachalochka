package monster.greyde.kachalochka.core.domain.gym

import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days

class PlatformShiftTest {
    private val ivan = UserId("11111111-1111-4111-8111-111111111111")
    private val bare = Machine.new("Жим ногами", ivan, T0)
    private val now = T0 + 1.days

    private fun platform(
        weight: Double,
        included: Boolean,
    ) = bare.copy(platformWeight = weight, platformIncluded = included)

    private fun set(weight: Double) =
        WorkoutSet(
            WorkoutSetId.random(),
            ivan,
            VisitId.random(),
            bare.id,
            weight,
            10,
            0,
            T0,
            T0,
            false,
        )

    @Test
    fun a_platform_beside_the_name_is_taken_off_totals_recorded_without_it() {
        assertEquals(-25.0, platformShift(from = bare, to = platform(25.0, included = false)))
    }

    @Test
    fun a_platform_added_to_the_record_changes_no_weight() {
        assertEquals(0.0, platformShift(from = bare, to = platform(25.0, included = true)))
    }

    @Test
    fun a_platform_moved_into_the_record_is_added_to_every_weight() {
        val beside = platform(25.0, included = false)

        assertEquals(25.0, platformShift(from = beside, to = platform(25.0, included = true)))
    }

    @Test
    fun a_heavier_platform_beside_the_name_takes_off_the_difference() {
        val before = platform(20.0, included = false)

        assertEquals(-2.5, platformShift(from = before, to = platform(22.5, included = false)))
    }

    @Test
    fun the_shift_is_rounded_like_any_weight() {
        val before = platform(0.3, included = false)

        assertEquals(0.2, platformShift(from = before, to = platform(0.1, included = false)))
    }

    @Test
    fun shifted_sets_keep_everything_but_the_weight_and_the_update_time() {
        val recorded = set(70.0)

        val shifted = shiftedSets(listOf(recorded), -25.0, now)

        assertEquals(listOf(recorded.copy(weight = 45.0, updatedAt = now)), shifted)
    }

    @Test
    fun a_shifted_weight_never_drops_below_zero() {
        assertEquals(listOf(0.0), shiftedSets(listOf(set(20.0)), -25.0, now).map { it.weight })
    }
}
