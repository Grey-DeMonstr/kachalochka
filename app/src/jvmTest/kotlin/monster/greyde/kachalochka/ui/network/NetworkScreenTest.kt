package monster.greyde.kachalochka.ui.network

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import monster.greyde.kachalochka.AppVersion
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NetworkScreenTest {
    @Test
    fun the_screen_shows_the_facts_and_the_result_of_every_check() {
        val gym = FakeGym()
        runScreenTest(gym, screen = { DeveloperPanelScreen(onBack = {}) }) {
            waitUntil { gym.serverAnswers.requestHistory.size == 5 }
            waitForIdle()

            onNodeWithTag("top-bar-title").assertTextEquals("Developer panel")
            listOf(
                "App: ${AppVersion.NAME}",
                "Time: 2023-11-14T22:13:20Z",
                "Server: example.test",
                "Proxy in use: none",
                "Signed in: no, 0 accounts",
                "Network: wifi, validated",
            ).forEach { onNodeWithText(it).assertExists() }
            onNodeWithText("DNS example.test: OK", substring = true).assertExists()
            onNodeWithText("google.com: OK", substring = true).assertExists()
            onNodeWithText("running", substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun share_sends_the_whole_report_as_text() {
        val gym = FakeGym()
        runScreenTest(gym, screen = { DeveloperPanelScreen(onBack = {}) }) {
            waitUntil { gym.serverAnswers.requestHistory.size == 5 }
            waitForIdle()

            onNodeWithTag("network-share").performClick()
            waitForIdle()
        }

        val report = gym.texts.shared.single()
        assertTrue(report.startsWith("App: ${AppVersion.NAME}\n"), report)
        assertTrue("\nSupabase storage: OK, " in report, report)
        assertTrue("anon-key" !in report, report)
    }

    @Test
    fun the_device_keeps_the_proxy_that_is_typed_and_checked() {
        val gym = FakeGym()
        runScreenTest(gym, screen = { DeveloperPanelScreen(onBack = {}) }) {
            onNodeWithTag("tab-proxy").performClick()
            onNodeWithTag("proxy-address").performTextInput("192.0.2.7:3128")
            onNodeWithTag("proxy-login").performTextInput("ivan")
            onNodeWithTag("proxy-password").performTextInput("secret")
            onNodeWithTag("use-proxy").performClick()
            waitForIdle()
        }

        assertEquals(
            ProxySetting("192.0.2.7:3128", true, "ivan", "secret"),
            gym.proxies.setting.value,
        )
    }

    @Test
    fun the_options_tab_keeps_a_whole_number_of_picture_rows() {
        val gym = FakeGym()
        runScreenTest(gym, screen = { DeveloperPanelScreen(onBack = {}) }) {
            onNodeWithTag("tab-options").assertTextEquals("Options")
            onNodeWithTag("tab-options").performClick()
            onNodeWithText("Rows in a statistics image").assertExists()
            onNodeWithTag("picture-rows").assertTextEquals("15")

            onNodeWithTag("picture-rows").performTextClearance()
            onNodeWithTag("picture-rows").performTextInput("8")
            waitForIdle()
            assertEquals(8, gym.pictureRows.rows.value)

            onNodeWithTag("picture-rows").performTextClearance()
            onNodeWithTag("picture-rows").performTextInput("0")
            waitForIdle()
            assertEquals(8, gym.pictureRows.rows.value)
        }
    }

    @Test
    fun the_tabs_split_the_facts_from_the_proxy_settings() {
        runScreenTest(FakeGym(), screen = { DeveloperPanelScreen(onBack = {}) }) {
            onNodeWithTag("tab-info").assertTextEquals("Info")
            onNodeWithTag("tab-proxy").assertTextEquals("Proxy settings")
            onNodeWithTag("proxy-address").assertDoesNotExist()
            onNodeWithText("Server: example.test").assertExists()

            onNodeWithTag("tab-proxy").performClick()
            onNodeWithTag("proxy-address").assertExists()
            onNodeWithText("Server: example.test").assertDoesNotExist()
        }
    }
}
