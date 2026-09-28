package monster.greyde.kachalochka.core.domain.measures

import monster.greyde.kachalochka.core.domain.profile.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BodyFatTest {
    private val none = BodyInputs(null, null, null, null, null, null, null)
    private val man =
        BodyInputs(
            sex = Sex.Male,
            age = 30,
            heightCm = 180.0,
            weightKg = 80.0,
            waistCm = 85.0,
            neckCm = 38.0,
            hipsCm = null,
        )
    private val woman =
        BodyInputs(
            sex = Sex.Female,
            age = 25,
            heightCm = 165.0,
            weightKg = 60.0,
            waistCm = 70.0,
            neckCm = 32.0,
            hipsCm = 95.0,
        )

    private fun assertPercent(
        expected: Double,
        actual: Double?,
    ) = assertEquals(expected, assertNotNull(actual), 0.1)

    @Test
    fun navy_for_a_man_uses_waist_neck_and_height() {
        // 495 / (1.0324 − 0.19077·log10(47) + 0.15456·log10(178)) − 450
        assertPercent(16.4, bodyFat(BodyFatMethod.Navy, man.copy(heightCm = 178.0)))
    }

    @Test
    fun navy_for_a_woman_adds_the_hips() {
        // 495 / (1.29579 − 0.35004·log10(133) + 0.22100·log10(165)) − 450
        assertPercent(24.9, bodyFat(BodyFatMethod.Navy, woman))
    }

    @Test
    fun ymca_works_in_pounds_and_inches() {
        // (−98.42 + 4.15·33.46 − 0.082·176.37) / 176.37 · 100
        assertPercent(14.7, bodyFat(BodyFatMethod.Ymca, man))
        // (−76.76 + 4.15·27.56 − 0.082·132.28) / 132.28 · 100
        assertPercent(20.2, bodyFat(BodyFatMethod.Ymca, woman))
    }

    @Test
    fun deurenberg_uses_bmi_age_and_sex() {
        // 1.20·24.69 + 0.23·30 − 10.8 − 5.4
        assertPercent(20.3, bodyFat(BodyFatMethod.Deurenberg, man))
        // 1.20·22.04 + 0.23·25 − 5.4
        assertPercent(26.8, bodyFat(BodyFatMethod.Deurenberg, woman))
    }

    @Test
    fun no_method_answers_without_the_sex() {
        BodyFatMethod.entries.forEach { method ->
            assertNull(bodyFat(method, man.copy(sex = null)), method.name)
            assertEquals(BodyInput.Sex, missingInputs(method, man.copy(sex = null)).first())
        }
    }

    @Test
    fun missing_inputs_name_what_each_method_needs() {
        assertEquals(
            listOf(BodyInput.Sex, BodyInput.Height, BodyInput.Waist, BodyInput.Neck),
            missingInputs(BodyFatMethod.Navy, none),
        )
        assertEquals(
            listOf(BodyInput.Sex, BodyInput.Weight, BodyInput.Waist),
            missingInputs(BodyFatMethod.Ymca, none),
        )
        assertEquals(
            listOf(BodyInput.Sex, BodyInput.Age, BodyInput.Height, BodyInput.Weight),
            missingInputs(BodyFatMethod.Deurenberg, none),
        )
        assertEquals(emptyList(), missingInputs(BodyFatMethod.Navy, man))
        assertEquals(
            listOf(BodyInput.Hips),
            missingInputs(BodyFatMethod.Navy, woman.copy(hipsCm = null)),
        )
        assertNull(bodyFat(BodyFatMethod.Navy, woman.copy(hipsCm = null)))
    }

    @Test
    fun a_meaningless_result_is_no_result() {
        assertNull(bodyFat(BodyFatMethod.Navy, man.copy(neckCm = 85.0)))
        assertNull(bodyFat(BodyFatMethod.Navy, man.copy(neckCm = 90.0)))
        assertNull(bodyFat(BodyFatMethod.Ymca, man.copy(waistCm = 40.0)))
    }
}
