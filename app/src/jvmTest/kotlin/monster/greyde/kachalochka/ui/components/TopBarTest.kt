package monster.greyde.kachalochka.ui.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
class TopBarTest {
    @Test
    fun the_bar_shows_title_back_and_settings_and_an_idle_timer() {
        var backs = 0
        var settings = 0
        runScreenTest(
            FakeGym(),
            screen = {
                Screen(
                    "Тренажёр",
                    onBack = { backs++ },
                    onOpenSettings = { settings++ },
                ) {}
            },
        ) {
            onNodeWithTag("top-bar-title").assertTextEquals("Тренажёр")
            onNodeWithTag("rest-timer").assertTextEquals("1:30")
            onNodeWithTag("top-bar-back").performClick()
            onNodeWithTag("account-avatar").performClick()
            onNodeWithTag("account-settings").performClick()
            waitForIdle()
            assertEquals(1, backs)
            assertEquals(1, settings)
        }
    }

    @Test
    fun the_home_bar_has_no_back_arrow() =
        runScreenTest(FakeGym(), screen = { Screen("Качалочка", null, {}) {} }) {
            onNodeWithTag("top-bar-back").assertDoesNotExist()
        }

    @Test
    fun tapping_the_timer_starts_a_countdown_that_follows_the_clock() {
        val gym = FakeGym()
        runScreenTest(gym, screen = { Screen("Визит", {}, {}) {} }) {
            onNodeWithTag("rest-timer").performClick()
            gym.clock.current += 20.seconds
            gym.ticker.tick()
            waitForIdle()

            onNodeWithTag("rest-timer").assertTextEquals("1:10")
        }
    }
}
