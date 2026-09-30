package monster.greyde.kachalochka.ui.measures

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.domain.measures.MeasureId
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
class MeasuresScreenTest {
    private val gym = FakeGym()
    private val seeded = missingDefaults(null, emptySet())
    private val weight = seeded.first { it.kind == MeasureKind.Weight }
    private val waist = seeded.first { it.kind == MeasureKind.Waist }
    private val chest = seeded.first { it.kind == MeasureKind.Chest }

    init {
        runBlocking {
            seeded.forEach { gym.measures.upsert(it) }
            listOf(gym.today.plusDays(-7) to 82.4, gym.today.plusDays(-1) to 82.0).forEach {
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

    @Test
    fun a_row_reads_its_latest_value_change_and_age_and_the_button_starts_a_measurement() {
        var started = 0
        runScreenTest(gym, screen = { MeasuresScreen({}, {}, { started++ }, {}) }) {
            onNodeWithTag("top-bar-title").assertTextEquals("Замеры")
            val id = weight.id.value
            onNodeWithTag("measure-value-$id", useUnmergedTree = true).assertTextEquals("82 кг")
            onNodeWithTag("measure-delta-$id", useUnmergedTree = true)
                .assertTextEquals("−0.4")
            onNodeWithTag("measure-ago-$id", useUnmergedTree = true).assertTextEquals("вчера")
            onNodeWithTag("measure-value-${waist.id.value}", useUnmergedTree = true)
                .assertDoesNotExist()

            onNodeWithTag("new-measurement").performClick()
            waitForIdle()
        }
        assertEquals(1, started)
    }

    @Test
    fun tapping_a_row_opens_its_measure_but_not_while_ordering() {
        val opened = mutableListOf<MeasureId>()
        runScreenTest(
            gym,
            screen = { MeasuresScreen({}, {}, {}, onOpenMeasure = { opened += it }) },
        ) {
            onNodeWithTag("measure-chevron-${waist.id.value}", useUnmergedTree = true)
                .assertIsDisplayed()
            onNodeWithTag("measure-row-${waist.id.value}").performClick()
            waitForIdle()
            onNodeWithTag("reorder-toggle").performClick()
            waitForIdle()
            onNodeWithTag("measure-chevron-${waist.id.value}", useUnmergedTree = true)
                .assertDoesNotExist()
            onNodeWithTag("measure-row-${weight.id.value}").performClick()
            waitForIdle()
        }
        assertEquals(listOf(waist.id), opened)
    }

    @Test
    fun a_measure_added_in_the_dialog_is_listed_last() {
        runScreenTest(gym, screen = { MeasuresScreen({}, {}, {}, {}) }) {
            onNodeWithTag("add-measure").performClick()
            waitForIdle()
            onNodeWithTag("new-measure-name").performTextInput("Предплечье")
            onNodeWithTag("new-measure-unit").performTextInput("см")
            onNodeWithTag("confirm-new-measure").performClick()
            waitForIdle()

            val added = runBlocking { gym.measures.all(null) }.last()
            assertEquals("Предплечье", added.name)
            onNodeWithTag("measure-row-${added.id.value}").performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun ordering_drags_a_measure_by_its_handle() {
        runScreenTest(gym, screen = { MeasuresScreen({}, {}, {}, {}) }) {
            onNodeWithTag("drag-measure-${chest.id.value}").assertDoesNotExist()
            onNodeWithTag("reorder-toggle").assertTextEquals("Порядок").performClick()
            waitForIdle()
            onNodeWithTag("reorder-toggle").assertTextEquals("Готово")

            onNodeWithTag("drag-measure-${chest.id.value}").performTouchInput {
                down(center)
                repeat(20) { moveBy(Offset(0f, -30f)) }
                up()
            }
            waitForIdle()

            assertTrue(
                onNodeWithTag(
                    "measure-row-${chest.id.value}",
                ).fetchSemanticsNode().positionInRoot.y <
                    onNodeWithTag("measure-row-${weight.id.value}")
                        .fetchSemanticsNode()
                        .positionInRoot.y,
            )
        }
        assertEquals(
            listOf(chest.id, weight.id, waist.id),
            runBlocking { gym.measures.all(null) }.take(3).map { it.id },
        )
    }
}
