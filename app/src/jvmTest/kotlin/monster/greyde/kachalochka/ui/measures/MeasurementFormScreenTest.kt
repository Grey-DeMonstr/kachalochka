package monster.greyde.kachalochka.ui.measures

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.measures.MeasureKind
import monster.greyde.kachalochka.core.domain.measures.Measurement
import monster.greyde.kachalochka.core.domain.measures.MeasurementId
import monster.greyde.kachalochka.core.domain.measures.missingDefaults
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MeasurementFormScreenTest {
    private val gym = FakeGym()
    private val saturday = CalendarDay(2023, 11, 11)
    private val seeded = missingDefaults(null, emptySet())
    private val weight = seeded.first { it.kind == MeasureKind.Weight }
    private val waist = seeded.first { it.kind == MeasureKind.Waist }
    private val weighed =
        Measurement(
            MeasurementId.random(),
            null,
            weight.id,
            saturday,
            82.4,
            gym.clock.current,
            false,
        )

    init {
        runBlocking {
            seeded.forEach { gym.measures.upsert(it) }
            gym.measurements.upsert(weighed)
        }
    }

    @Test
    fun a_value_typed_for_today_is_saved() {
        var saved = 0
        runScreenTest(gym, screen = {
            MeasurementFormScreen(null, {}, {}, onSaved = { saved++ }, onDeleted = {})
        }) {
            onNodeWithTag("top-bar-title").assertTextEquals("Замер")
            onNodeWithTag("measurement-day").assertTextEquals("Сегодня, 14 ноября")
            onNodeWithTag("measure-hint-${weight.id.value}", useUnmergedTree = true)
                .assertTextEquals("82,4")
            onNodeWithTag("delete-measurement").assertDoesNotExist()

            onNodeWithTag("measure-field-${waist.id.value}").performTextInput("90,5")
            onNodeWithTag("save-measurement").performClick()
            waitForIdle()
        }
        assertEquals(1, saved)
        assertEquals(
            listOf(90.5),
            runBlocking { gym.measurements.all(null) }
                .filter { it.day == gym.today }
                .map { it.value },
        )
    }

    @Test
    fun the_day_button_picks_another_day_and_loads_its_values() {
        runScreenTest(gym, screen = { MeasurementFormScreen(null, {}, {}, {}, {}) }) {
            onNodeWithTag("measurement-day").performClick()
            waitForIdle()
            onNodeWithTag("day-picker").assertExists()

            onNodeWithTag("day-2023-11-11").performClick()
            waitForIdle()

            onNodeWithTag("day-picker").assertDoesNotExist()
            onNodeWithTag("measurement-day").assertTextEquals("Суббота, 11 ноября")
            onNodeWithTag("measure-field-${weight.id.value}").assertTextEquals("82,4")
        }
    }

    @Test
    fun the_calculator_asks_for_the_body_and_fills_the_fat_field_with_a_result() {
        val bodyFat = seeded.first { it.kind == MeasureKind.BodyFat }
        runScreenTest(gym, screen = { MeasurementFormScreen(null, {}, {}, {}, {}) }) {
            onNodeWithTag("calculate-fat").performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag("fat-sheet").assertExists()
            onNodeWithTag("body-sex-male").performClick()
            onNodeWithTag("body-birth-year").performTextInput("1993")
            onNodeWithTag("body-height").performTextInput("180")
            onNodeWithTag("save-body-params").performClick()
            waitForIdle()

            onNodeWithTag("fat-method-Navy").performClick()
            waitForIdle()
            onNodeWithTag("fat-sheet").assertExists()
            // 82,4 kg at 180 cm is a BMI of 25,4; aged 30.
            onNodeWithTag("fat-method-Deurenberg").performClick()
            waitForIdle()

            onNodeWithTag("fat-sheet").assertDoesNotExist()
            onNodeWithTag("measure-field-${bodyFat.id.value}").assertTextEquals("21,2")
        }
        assertEquals(1993, runBlocking { gym.profiles.forOwner(null) }?.birthYear)
        assertEquals(1, runBlocking { gym.measurements.all(null) }.size)
    }

    @Test
    fun deleting_asks_first_and_clears_the_day() {
        var deleted = 0
        runScreenTest(gym, screen = {
            MeasurementFormScreen(saturday, {}, {}, onSaved = {}, onDeleted = { deleted++ })
        }) {
            onNodeWithTag("delete-measurement").performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag("confirm-delete-measurement").performClick()
            waitForIdle()
        }
        assertEquals(1, deleted)
        assertTrue(
            gym.measurements.rows
                .getValue(weighed.id)
                .deleted,
        )
    }
}
