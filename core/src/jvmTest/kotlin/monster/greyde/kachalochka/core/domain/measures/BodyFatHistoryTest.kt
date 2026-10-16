package monster.greyde.kachalochka.core.domain.measures

import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.profile.Sex
import kotlin.test.Test
import kotlin.test.assertEquals

class BodyFatHistoryTest {
    private fun day(n: Int) = CalendarDay(2026, 3, n)

    private val birth = CalendarDay(1996, 3, 10)

    private fun inputs(
        sex: Sex = Sex.Male,
        age: Int? = null,
        heightCm: Double? = 180.0,
        weightKg: Double? = null,
        waistCm: Double? = null,
        neckCm: Double? = null,
        hipsCm: Double? = null,
    ) = BodyInputs(sex, age, heightCm, weightKg, waistCm, neckCm, hipsCm)

    private fun history(
        method: BodyFatMethod,
        values: Map<MeasureKind, Map<CalendarDay, Double>>,
        sex: Sex? = Sex.Male,
    ) = bodyFatHistory(method, sex, birth, 180.0, values)

    @Test
    fun every_day_of_a_measure_gets_a_point_with_the_age_of_that_day() {
        val points =
            history(
                BodyFatMethod.Deurenberg,
                mapOf(MeasureKind.Weight to mapOf(day(12) to 81.0, day(1) to 80.0)),
            )

        assertEquals(
            listOf(
                day(1) to bodyFat(BodyFatMethod.Deurenberg, inputs(age = 29, weightKg = 80.0)),
                day(12) to bodyFat(BodyFatMethod.Deurenberg, inputs(age = 30, weightKg = 81.0)),
            ),
            points,
        )
    }

    @Test
    fun points_start_on_the_first_day_all_measures_have_a_value() {
        val points =
            history(
                BodyFatMethod.Navy,
                mapOf(
                    MeasureKind.Waist to mapOf(day(1) to 90.0, day(11) to 80.0),
                    MeasureKind.Neck to mapOf(day(6) to 38.0),
                ),
            )

        assertEquals(
            listOf(
                day(6) to bodyFat(BodyFatMethod.Navy, inputs(waistCm = 85.0, neckCm = 38.0)),
                day(11) to bodyFat(BodyFatMethod.Navy, inputs(waistCm = 80.0, neckCm = 38.0)),
            ),
            points,
        )
    }

    @Test
    fun a_missing_value_lies_on_the_line_between_its_neighbours() {
        val points =
            history(
                BodyFatMethod.Ymca,
                mapOf(
                    MeasureKind.Weight to mapOf(day(1) to 80.0, day(2) to 81.0, day(5) to 84.0),
                    MeasureKind.Waist to mapOf(day(1) to 90.0, day(5) to 86.0),
                ),
            )

        assertEquals(
            listOf(
                day(1) to bodyFat(BodyFatMethod.Ymca, inputs(weightKg = 80.0, waistCm = 90.0)),
                day(2) to bodyFat(BodyFatMethod.Ymca, inputs(weightKg = 81.0, waistCm = 89.0)),
                day(5) to bodyFat(BodyFatMethod.Ymca, inputs(weightKg = 84.0, waistCm = 86.0)),
            ),
            points,
        )
    }

    @Test
    fun a_measure_keeps_its_last_value_after_it() {
        val points =
            history(
                BodyFatMethod.Ymca,
                mapOf(
                    MeasureKind.Weight to mapOf(day(1) to 80.0, day(9) to 78.0),
                    MeasureKind.Waist to mapOf(day(1) to 90.0),
                ),
            )

        assertEquals(
            day(9) to bodyFat(BodyFatMethod.Ymca, inputs(weightKg = 78.0, waistCm = 90.0)),
            points.last(),
        )
    }

    @Test
    fun only_the_measures_the_method_reads_for_the_sex_give_days() {
        val values =
            mapOf(
                MeasureKind.Waist to mapOf(day(1) to 90.0),
                MeasureKind.Neck to mapOf(day(1) to 38.0),
                MeasureKind.Hips to mapOf(day(3) to 100.0),
                MeasureKind.Chest to mapOf(day(4) to 100.0),
            )

        assertEquals(listOf(day(1)), history(BodyFatMethod.Navy, values).map { it.first })
        assertEquals(
            listOf(day(3)),
            history(BodyFatMethod.Navy, values, Sex.Female).map { it.first },
        )
    }

    @Test
    fun a_meaningless_result_is_left_out() {
        val points =
            history(
                BodyFatMethod.Navy,
                mapOf(
                    MeasureKind.Waist to mapOf(day(1) to 90.0, day(2) to 38.0, day(3) to 90.0),
                    MeasureKind.Neck to mapOf(day(1) to 38.0),
                ),
            )

        assertEquals(listOf(day(1), day(3)), points.map { it.first })
    }

    @Test
    fun a_method_without_all_its_inputs_has_no_points() {
        val weighed = mapOf(MeasureKind.Weight to mapOf(day(1) to 80.0))

        assertEquals(emptyList(), history(BodyFatMethod.Ymca, weighed))
        assertEquals(emptyList(), history(BodyFatMethod.Deurenberg, weighed, sex = null))
        assertEquals(
            emptyList(),
            bodyFatHistory(BodyFatMethod.Deurenberg, Sex.Male, null, 180.0, weighed),
        )
    }
}
