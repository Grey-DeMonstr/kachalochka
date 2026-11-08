package monster.greyde.kachalochka.ui.network

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        runScreenTest(gym, screen = { NetworkScreen(onBack = {}) }) {
            waitUntil { gym.serverAnswers.requestHistory.size == 5 }
            waitForIdle()

            onNodeWithTag("top-bar-title").assertTextEquals("Сеть")
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
        runScreenTest(gym, screen = { NetworkScreen(onBack = {}) }) {
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
        runScreenTest(gym, screen = { NetworkScreen(onBack = {}) }) {
            onNodeWithTag("proxy-address").performTextInput("192.0.2.7:3128")
            onNodeWithTag("use-proxy").performClick()
            waitForIdle()
        }

        assertEquals(ProxySetting("192.0.2.7:3128", true), gym.proxies.setting.value)
    }
}
