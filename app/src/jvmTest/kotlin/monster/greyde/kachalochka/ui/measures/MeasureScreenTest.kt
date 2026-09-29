package monster.greyde.kachalochka.ui.measures

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.measures.Measure
import monster.greyde.kachalochka.core.domain.measures.MeasureId
import monster.greyde.kachalochka.core.domain.measures.MeasureKind
import monster.greyde.kachalochka.core.domain.measures.Measurement
import monster.greyde.kachalochka.core.domain.measures.MeasurementId
import monster.greyde.kachalochka.core.domain.measures.missingDefaults
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
import monster.greyde.kachalochka.runScreenTestInEnglish
import monster.greyde.kachalochka.ui.friends.IVAN_SESSION
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MeasureScreenTest {
    private val gym = FakeGym()
    private val seeded = missingDefaults(null, emptySet())
    private val weight = seeded.first { it.kind == MeasureKind.Weight }
    private val forearm =
        Measure(MeasureId.random(), null, "Предплечье", "см", null, 7, gym.clock.current, false)
    private val lastWeek = CalendarDay(2023, 11, 7)
    private val today = CalendarDay(2023, 11, 14)

    init {
        runBlocking {
            (seeded + forearm).forEach { gym.measures.upsert(it) }
            listOf(CalendarDay(2023, 9, 1) to 83.0, lastWeek to 82.4, today to 82.0).forEach {
                gym.measurements.upsert(
                    Measurement(
                        MeasurementId.random(),
                        null,
                        weight.id,
                        it.first,
                        it.second,
                        gym.clock.current,
                        false,
                    ),
                )
            }
        }
    }

    private fun runMeasure(
        measure: MeasureId = weight.id,
        onOpenDay: (CalendarDay) -> Unit = {},
        onGone: () -> Unit = {},
        assertions: ComposeUiTest.() -> Unit,
    ) = runScreenTest(
        gym,
        screen = { MeasureScreen(measure, {}, {}, onOpenDay, onGone) },
        assertions = assertions,
    )

    @Test
    fun the_measure_shows_its_chart_latest_value_and_change_over_three_months() {
        runMeasure {
            onNodeWithTag("top-bar-title").assertTextEquals("Вес")
            onNodeWithTag("period-Quarter").assertIsSelected()
            onNodeWithTag("measure-chart").assertIsDisplayed()
            onNodeWithTag("measure-latest", useUnmergedTree = true).assertTextEquals("82 кг")
            onNodeWithTag("measure-change", useUnmergedTree = true).assertTextEquals("−1")
        }
    }

    @Test
    fun a_measure_speaks_english() =
        runScreenTestInEnglish(gym, screen = { MeasureScreen(weight.id, {}, {}, {}, {}) }) {
            onNodeWithTag("top-bar-title").assertTextEquals("Weight")
            onNodeWithTag("period-Quarter").assertTextEquals("3 mo")
            onNodeWithTag("measure-latest", useUnmergedTree = true).assertTextEquals("82 kg")
        }

    @Test
    fun a_period_with_one_value_shows_the_value_instead_of_a_line() {
        runBlocking {
            gym.measurements.rows.values
                .first { it.day == today }
                .let { gym.measurements.upsert(it.copy(deleted = true)) }
        }
        runMeasure {
            onNodeWithTag("period-Month").performClick()
            waitForIdle()

            onNodeWithTag("period-Month").assertIsSelected()
            onNodeWithTag("measure-chart").assertDoesNotExist()
            onNodeWithTag("measure-single-value", useUnmergedTree = true)
                .assertTextEquals("82.4 кг")
            onNodeWithTag("measure-change", useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test
    fun tapping_a_value_in_the_history_opens_its_day() {
        val opened = mutableListOf<CalendarDay>()
        runMeasure(onOpenDay = { opened += it }) {
            onNodeWithTag("history-${lastWeek.iso}").performScrollTo().performClick()
            waitForIdle()
        }
        assertEquals(listOf(lastWeek), opened)
    }

    @Test
    fun a_measure_a_formula_reads_has_no_menu() {
        runMeasure {
            onNodeWithTag("top-bar-title").assertTextEquals("Вес")
            onNodeWithTag("measure-menu").assertDoesNotExist()
        }
    }

    @Test
    fun the_menu_renames_the_user_s_own_measure() {
        runMeasure(forearm.id) {
            onNodeWithTag("measure-menu").performClick()
            onNodeWithTag("edit-measure").performClick()
            waitForIdle()
            onNodeWithTag("edit-measure-name").performTextClearance()
            onNodeWithTag("edit-measure-name").performTextInput("Масса")
            onNodeWithTag("confirm-edit-measure").performClick()
            waitForIdle()

            onNodeWithTag("top-bar-title").assertTextEquals("Масса")
        }
        assertEquals(
            "Масса",
            gym.measures.rows
                .getValue(forearm.id)
                .name,
        )
    }

    @Test
    fun the_screen_leaves_once_another_account_without_the_measure_is_active() {
        var left = 0
        runMeasure(onGone = { left++ }) {
            onNodeWithTag("top-bar-title").assertTextEquals("Вес")

            gym.withAccounts(IVAN_SESSION, active = IVAN_SESSION)
            waitForIdle()
        }
        assertEquals(1, left)
    }

    @Test
    fun the_menu_deletes_the_measure_after_confirmation() {
        var deleted = 0
        runMeasure(forearm.id, onGone = { deleted++ }) {
            onNodeWithTag("measure-menu").performClick()
            onNodeWithTag("delete-measure").performClick()
            waitForIdle()
            onNodeWithTag("confirm-delete-measure").performClick()
            waitForIdle()
        }
        assertEquals(1, deleted)
        assertTrue(
            gym.measures.rows
                .getValue(forearm.id)
                .deleted,
        )
    }
}
