package monster.greyde.kachalochka.ui.stats

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
import monster.greyde.kachalochka.runScreenTestInEnglish
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalTestApi::class)
class StatisticsScreenTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val press = Machine.new("Жим ногами", null, t0)
    private val row = Machine.new("Тяга", null, t0)

    init {
        runBlocking {
            gym.machines.upsert(press)
            gym.machines.upsert(row)
            listOf(t0 - 40.days to 70.0, t0 - 1.days to 75.0).forEach { (at, weight) ->
                gym.sets.upsert(
                    WorkoutSet(
                        WorkoutSetId.random(),
                        null,
                        VisitId.random(),
                        press.id,
                        weight,
                        8,
                        0,
                        at,
                        at,
                        false,
                    ),
                )
            }
        }
    }

    @Test
    fun a_card_of_the_overall_view_opens_its_machine() =
        runScreenTest(gym, screen = { StatisticsScreen(null, {}, {}) }) {
            onNodeWithTag("stats-choice").assertTextContains("Общая")
            onNodeWithTag("period-month").assertIsOn()
            onNodeWithTag(
                "stats-overall-title",
            ).assertTextEquals("УПРАЖНЕНИЯ ЗА ПЕРИОД · С 14 ОКТЯБРЯ")
            onNodeWithTag("stats-change-${press.id.value}", useUnmergedTree = true)
                .assertTextContains("+5 кг")
            onNodeWithTag("stats-card-${row.id.value}").assertDoesNotExist()

            onNodeWithTag("stats-card-${press.id.value}").performClick()
            waitForIdle()

            onNodeWithTag("stats-choice").assertTextContains("Жим ногами")
            onNodeWithTag("stats-chart-title").assertTextEquals("ЛУЧШИЙ ПОДХОД · С 14 ОКТЯБРЯ")
            onNodeWithTag("stats-best").assertTextEquals("75 кг × 8")
            onNodeWithTag("stats-history-date-0").assertTextEquals("13 ноября")
            onNodeWithTag("stats-history-results-1").assertTextEquals("70кг 1x8")
        }

    @Test
    fun the_overall_view_shows_improvements_only_at_first_and_exports_its_cards() =
        runScreenTest(gym, screen = { StatisticsScreen(null, {}, {}) }) {
            onNodeWithTag("stats-improvements-only").assertIsOn()
            onNodeWithTag("stats-export").performClick()
            waitForIdle()

            assertEquals(listOf("за месяц\n\nЖим ногами +5 кг (75 кг × 8)"), gym.texts.shared)
            onNodeWithTag("stats-notice").assertTextEquals("Скопировано")

            onNodeWithTag("stats-card-${press.id.value}").performClick()
            waitForIdle()
            onNodeWithTag("stats-export").assertDoesNotExist()
            onNodeWithTag("stats-improvements-only").assertDoesNotExist()
        }

    @Test
    fun the_dropdown_and_the_period_chips_change_what_is_shown() =
        runScreenTest(gym, screen = { StatisticsScreen(press.id, {}, {}) }) {
            onNodeWithTag("stats-choice").performClick()
            onNodeWithTag("stats-choice-${row.id.value}").performClick()
            waitForIdle()

            onNodeWithTag("stats-empty").assertTextEquals("Нет подходов за период")

            onNodeWithTag("period-threemonths").performClick()
            onNodeWithTag("stats-choice").performClick()
            onNodeWithTag("stats-choice-overall").performClick()
            waitForIdle()

            onNodeWithTag(
                "stats-overall-title",
            ).assertTextEquals("УПРАЖНЕНИЯ ЗА ПЕРИОД · С 14 АВГУСТА")
        }

    @Test
    fun grouping_by_tags_heads_the_cards_and_the_dropdown_and_growth_can_be_chosen() {
        runBlocking { gym.machines.upsert(press.copy(tags = setOf("Ноги"))) }
        runScreenTest(gym, screen = { StatisticsScreen(null, {}, {}) }) {
            onNodeWithTag("stats-section-Ноги").assertDoesNotExist()

            onNodeWithTag("stats-group-by-tag").performClick()
            waitForIdle()

            onNodeWithTag("stats-section-Ноги").assertTextEquals("Ноги")
            onNodeWithTag("stats-choice").performClick()
            onNodeWithTag("stats-choice-section-Ноги").assertTextEquals("Ноги")
            onNodeWithTag("stats-choice-overall").performClick()
            onNodeWithTag("stats-sort-growth").assertTextEquals("Рост").performClick()
            onNodeWithTag("stats-sort-growth").assertIsOn()
        }
    }

    @Test
    fun statistics_speak_english() =
        runScreenTestInEnglish(gym, screen = { StatisticsScreen(null, {}, {}) }) {
            onNodeWithTag("stats-choice").assertTextContains("Overall")
            onNodeWithTag("period-year").assertTextEquals("Year")
            onNodeWithTag(
                "stats-overall-title",
            ).assertTextEquals("MACHINES IN THE PERIOD · SINCE 14 OCTOBER")
        }
}
