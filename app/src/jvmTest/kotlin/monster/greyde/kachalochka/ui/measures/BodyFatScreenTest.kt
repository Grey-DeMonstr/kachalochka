package monster.greyde.kachalochka.ui.measures

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.measures.BodyFatMethod
import monster.greyde.kachalochka.core.domain.measures.BodyInputs
import monster.greyde.kachalochka.core.domain.measures.MeasureKind
import monster.greyde.kachalochka.core.domain.measures.Measurement
import monster.greyde.kachalochka.core.domain.measures.MeasurementId
import monster.greyde.kachalochka.core.domain.measures.bodyFat
import monster.greyde.kachalochka.core.domain.measures.missingDefaults
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.Sex
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class BodyFatScreenTest {
    private val gym = FakeGym()
    private val seeded = missingDefaults(null, emptySet())
    private val lastWeek = CalendarDay(2023, 11, 7)
    private val today = CalendarDay(2023, 11, 14)

    init {
        runBlocking {
            seeded.forEach { gym.measures.upsert(it) }
            gym.profiles.upsert(
                Profile.new(null, gym.clock.current).copy(
                    sex = Sex.Male,
                    birthDate = CalendarDay(1987, 1, 1),
                    heightCm = 180.0,
                ),
            )
            val weight = seeded.first { it.kind == MeasureKind.Weight }
            listOf(lastWeek to 86.0, today to 85.0).forEach { (day, value) ->
                gym.measurements.upsert(
                    Measurement(
                        MeasurementId.random(),
                        null,
                        weight.id,
                        day,
                        value,
                        gym.clock.current,
                        false,
                    ),
                )
            }
        }
    }

    private fun percent(weight: Double) =
        fatPercent(
            bodyFat(
                BodyFatMethod.Deurenberg,
                BodyInputs(Sex.Male, 36, 180.0, weight, null, null, null),
            )!!,
        )

    @Test
    fun the_formula_shows_its_chart_latest_value_and_all_results() {
        runScreenTest(gym, screen = { BodyFatScreen(BodyFatMethod.Deurenberg, {}, {}) }) {
            onNodeWithTag("top-bar-title").assertTextEquals("Процент жира · Дойренберг")
            onNodeWithTag("period-Quarter").assertIsSelected()
            onNodeWithTag("measure-chart").assertIsDisplayed()
            onNodeWithTag("measure-latest", useUnmergedTree = true)
                .assertTextEquals(percent(85.0))
            onNodeWithTag("measure-last", useUnmergedTree = true)
                .assertTextEquals("Последний замер: сегодня")
            onNodeWithTag("fat-history-date-1", useUnmergedTree = true)
                .performScrollTo()
                .assertTextEquals("7 ноября")
            onNodeWithTag("fat-history-results-1", useUnmergedTree = true)
                .assertTextEquals(percent(86.0))
            onNodeWithTag("entry-value").assertDoesNotExist()
            onNodeWithTag("edit-history").assertDoesNotExist()
        }
    }

    @Test
    fun a_formula_without_a_measure_names_it() {
        runScreenTest(gym, screen = { BodyFatScreen(BodyFatMethod.Ymca, {}, {}) }) {
            onNodeWithTag("top-bar-title").assertTextEquals("Процент жира · YMCA")
            onNodeWithTag("measure-latest", useUnmergedTree = true)
                .assertTextEquals("Нужно: талия")
            onNodeWithTag("fat-profile-link").assertDoesNotExist()
            onNodeWithTag("measure-chart").assertDoesNotExist()
            onNodeWithTag("fat-history-0").assertDoesNotExist()
        }
    }

    @Test
    fun a_formula_without_the_sex_names_it_and_leads_to_the_profile() {
        runBlocking {
            val profile = gym.profiles.forOwner(null)!!
            gym.profiles.upsert(profile.copy(sex = null))
        }
        var settings = 0
        runScreenTest(
            gym,
            screen = { BodyFatScreen(BodyFatMethod.Deurenberg, {}, { settings++ }) },
        ) {
            onNodeWithTag("measure-latest", useUnmergedTree = true)
                .assertTextEquals("Нужно: пол")
            onNodeWithTag("fat-profile-link").performClick()
            waitForIdle()
        }
        assertEquals(1, settings)
    }
}
