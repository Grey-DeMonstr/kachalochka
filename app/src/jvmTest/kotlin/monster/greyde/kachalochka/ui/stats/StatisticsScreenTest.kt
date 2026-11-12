package monster.greyde.kachalochka.ui.stats

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
import monster.greyde.kachalochka.runScreenTestInRussian
import monster.greyde.kachalochka.ui.strings.RuStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalTestApi::class)
class StatisticsScreenTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val press = Machine.new("Leg press", null, t0)
    private val row = Machine.new("Row", null, t0)

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
            onNodeWithTag("stats-choice").assertTextContains("Overall")
            onNodeWithTag("period-month").assertIsOn()
            onNodeWithTag(
                "stats-overall-title",
            ).assertTextEquals("MACHINES IN THE PERIOD · SINCE 14 OCTOBER")
            onNodeWithTag("stats-change-${press.id.value}", useUnmergedTree = true)
                .assertTextContains("+5 kg")
            onNodeWithTag("stats-card-${row.id.value}").assertDoesNotExist()

            onNodeWithTag("stats-card-${press.id.value}").performClick()
            waitForIdle()

            onNodeWithTag("stats-choice").assertTextContains("Leg press")
            onNodeWithTag("stats-chart-title").assertDoesNotExist()
            onNodeWithTag("stats-best-trophy", useUnmergedTree = true).assertExists()
            onNodeWithTag("stats-best").assertTextEquals("75 kg × 8")
            onNodeWithTag("stats-history-date-0", useUnmergedTree = true)
                .assertTextEquals("13 November")
            onNodeWithTag("stats-history-results-1", useUnmergedTree = true)
                .assertTextEquals("70kg 1x8")
        }

    @Test
    fun a_result_row_opens_its_visit_on_the_machine() {
        val opened = mutableListOf<Pair<CalendarDay, MachineId>>()
        runScreenTest(gym, screen = {
            StatisticsScreen(press.id, {}, {}, onOpenVisit = { day, id -> opened += day to id })
        }) {
            onNodeWithTag("stats-history-1").performClick()
            waitForIdle()
        }
        assertEquals(listOf(gym.today.plusDays(-40) to press.id), opened)
    }

    @Test
    fun the_overall_view_shows_improvements_only_at_first_and_shares_its_cards() =
        runScreenTest(gym, screen = { StatisticsScreen(null, {}, {}) }) {
            onNodeWithTag("stats-improvements-only").assertIsOn()
            onNodeWithTag("stats-export").performClick()
            waitForIdle()
            onNodeWithTag("share-as-text").performClick()
            waitForIdle()

            assertEquals(listOf("past month\n\nLeg press +5 kg (75 kg × 8)"), gym.texts.shared)
            onNodeWithTag("stats-notice").assertTextEquals("Copied")

            onNodeWithTag("stats-card-${press.id.value}").performClick()
            waitForIdle()
            onNodeWithTag("stats-export").assertDoesNotExist()
            onNodeWithTag("stats-improvements-only").assertDoesNotExist()
        }

    @Test
    fun the_export_opens_a_popup_that_cancel_closes_without_sharing() =
        runScreenTest(gym, screen = { StatisticsScreen(null, {}, {}) }) {
            onNodeWithTag("stats-export").performClick()
            waitForIdle()
            onNodeWithTag("share-as-text").assertExists()
            onNodeWithTag("share-group-by-tag").assertDoesNotExist()

            onNodeWithTag("share-cancel").performClick()
            waitForIdle()

            onNodeWithTag("share-as-text").assertDoesNotExist()
            assertEquals(emptyList(), gym.texts.shared)
        }

    @Test
    fun the_overview_is_shared_as_a_picture() {
        runScreenTest(gym, screen = { StatisticsScreen(null, {}, {}) }) {
            onNodeWithTag("stats-export").performClick()
            waitForIdle()
            onNodeWithTag("share-as-image").performClick()
            waitUntil { gym.cardShares.shared.isNotEmpty() }
        }
        assertEquals(
            1080,
            gym.cardShares.shared
                .single()
                .width,
        )
    }

    @Test
    fun a_long_overview_is_shared_as_several_pictures_at_once() {
        runBlocking {
            gym.pictureRows.set(1)
            listOf(t0 - 40.days to 40.0, t0 - 1.days to 45.0).forEach { (at, weight) ->
                gym.sets.upsert(
                    WorkoutSet(
                        WorkoutSetId.random(),
                        null,
                        VisitId.random(),
                        row.id,
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
        runScreenTest(gym, screen = { StatisticsScreen(null, {}, {}) }) {
            onNodeWithTag("stats-export").performClick()
            waitForIdle()
            onNodeWithTag("share-as-image").performClick()
            waitUntil { gym.cardShares.pictureCalls.isNotEmpty() }
        }
        assertEquals(listOf(2), gym.cardShares.pictureCalls.map { it.size })
    }

    @Test
    fun the_share_popup_has_only_text_where_a_card_cannot_be_shared() {
        gym.cardShares.available = false
        runScreenTest(gym, screen = { StatisticsScreen(null, {}, {}) }) {
            onNodeWithTag("stats-export").performClick()
            waitForIdle()
            onNodeWithTag("share-as-text").assertExists()
            onNodeWithTag("share-as-image").assertDoesNotExist()
        }
    }

    @Test
    fun the_search_filters_the_machines_shows_the_found_part_and_the_cross_cancels_it() =
        runScreenTest(gym, screen = { StatisticsScreen(press.id, {}, {}) }) {
            onNodeWithTag("stats-search").assertDoesNotExist()
            onNodeWithTag("stats-choice").performClick()
            waitForIdle()

            onNodeWithTag("stats-choice-overall").assertExists()
            onNodeWithTag("stats-choice-check-${press.id.value}", useUnmergedTree = true)
                .assertExists()
            onNodeWithTag("stats-choice-check-${row.id.value}", useUnmergedTree = true)
                .assertDoesNotExist()
            onNodeWithTag("stats-search").assertIsFocused().performTextInput("pre")
            waitForIdle()

            onNodeWithTag("stats-choice-overall").assertDoesNotExist()
            onNodeWithTag("stats-choice-${row.id.value}").assertDoesNotExist()
            val name =
                onNodeWithTag("stats-choice-name-${press.id.value}", useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .config[SemanticsProperties.Text]
                    .single()
            assertEquals("Leg press", name.text)
            assertEquals(listOf(4 until 7), name.spanStyles.map { it.start until it.end })

            onNodeWithTag("stats-search").performTextInput("xyz")
            waitForIdle()
            onNodeWithTag("stats-choice-empty").assertTextEquals("Nothing found")

            onNodeWithTag("stats-search-cancel").performClick()
            waitForIdle()
            onNodeWithTag("stats-choice-list").assertDoesNotExist()
            onNodeWithTag("stats-choice").assertTextContains("Leg press")

            onNodeWithTag("stats-choice").performClick()
            waitForIdle()
            onNodeWithTag("stats-search").assertTextEquals("")
            onNodeWithTag("stats-choice-overall").assertExists()
        }

    @Test
    fun back_ends_the_search_instead_of_leaving() {
        var left = 0
        runScreenTest(gym, screen = { StatisticsScreen(press.id, { left++ }, {}) }) {
            onNodeWithTag("stats-choice").performClick()
            onNodeWithTag("stats-search").performTextInput("row")
            waitForIdle()

            onNodeWithTag("top-bar-back").performClick()
            waitForIdle()

            onNodeWithTag("stats-choice-list").assertDoesNotExist()
            onNodeWithTag("stats-choice").assertTextContains("Leg press")
        }
        assertEquals(0, left)
    }

    @Test
    fun system_back_ends_the_search_instead_of_leaving() {
        var left = 0
        val dispatcher = NavigationEventDispatcher()
        val systemBack = DirectNavigationEventInput().also(dispatcher::addInput)
        val owner =
            object : NavigationEventDispatcherOwner {
                override val navigationEventDispatcher = dispatcher
            }
        runScreenTest(gym, screen = {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                StatisticsScreen(press.id, { left++ }, {})
            }
        }) {
            onNodeWithTag("stats-choice").performClick()
            waitForIdle()
            onNodeWithTag("stats-choice-list").assertExists()

            runOnIdle { systemBack.backCompleted() }
            waitForIdle()

            onNodeWithTag("stats-choice-list").assertDoesNotExist()
        }
        assertEquals(0, left)
    }

    @Test
    fun the_machine_choice_and_the_period_chips_change_what_is_shown() =
        runScreenTest(gym, screen = { StatisticsScreen(press.id, {}, {}) }) {
            onNodeWithTag("stats-choice").performClick()
            onNodeWithTag("stats-search").performTextInput("row")
            onNodeWithTag("stats-choice-${row.id.value}").performClick()
            waitForIdle()

            onNodeWithTag("stats-choice-list").assertDoesNotExist()
            onNodeWithTag("stats-choice").assertTextContains("Row")
            onNodeWithTag("stats-empty").assertTextEquals("No sets in this period")

            onNodeWithTag("period-threemonths").performClick()
            onNodeWithTag("stats-choice").performClick()
            onNodeWithTag("stats-choice-overall").performClick()
            waitForIdle()

            onNodeWithTag(
                "stats-overall-title",
            ).assertTextEquals("MACHINES IN THE PERIOD · SINCE 14 AUGUST")
        }

    @Test
    fun grouping_by_tags_heads_the_cards_and_the_dropdown_and_growth_can_be_chosen() {
        runBlocking { gym.machines.upsert(press.copy(tags = setOf("Legs"))) }
        runScreenTest(gym, screen = { StatisticsScreen(null, {}, {}) }) {
            onNodeWithTag("stats-section-LEGS").assertDoesNotExist()

            onNodeWithTag("stats-group-by-tag").performClick()
            waitForIdle()

            onNodeWithTag("stats-section-LEGS").assertTextEquals("LEGS")
            onNodeWithTag("stats-choice").performClick()
            onNodeWithTag("stats-choice-section-LEGS").assertTextEquals("LEGS")
            onNodeWithTag("stats-choice-overall").performClick()
            onNodeWithTag("stats-sort-growth").assertTextEquals("Growth").performClick()
            onNodeWithTag("stats-sort-growth").assertIsOn()
        }
    }

    @Test
    fun statistics_speak_russian() =
        runScreenTestInRussian(gym, screen = { StatisticsScreen(null, {}, {}) }) {
            val since = RuStrings.sinceDay(RuStrings.dayMonth(14, 10))
            onNodeWithTag("stats-choice").assertTextContains(RuStrings.overallStats)
            onNodeWithTag("period-year").assertTextEquals(RuStrings.statsYear)
            onNodeWithTag("stats-overall-title")
                .assertTextEquals("${RuStrings.machinesInPeriod} · $since".uppercase())
        }
}
