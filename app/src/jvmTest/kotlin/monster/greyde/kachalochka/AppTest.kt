package monster.greyde.kachalochka

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import monster.greyde.kachalochka.di.appModule
import monster.greyde.kachalochka.ui.theme.InMemoryThemePreference
import monster.greyde.kachalochka.ui.theme.ThemePreference
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AppTest {
    private val host = UiTestHost()

    private val testModule =
        module {
            single<ThemePreference> { InMemoryThemePreference() }
        }

    @BeforeTest
    fun installHost() = host.install()

    @AfterTest
    fun uninstallHost() = host.uninstall()

    @Test
    fun the_app_opens_on_the_home_screen() =
        runComposeUiTest {
            setContent {
                host.Content {
                    KoinApplication(application = { modules(appModule, testModule) }) { App() }
                }
            }

            onNodeWithTag("home-title").assertIsDisplayed()
        }

    @Test
    fun home_navigates_to_settings() =
        runComposeUiTest {
            setContent {
                host.Content {
                    KoinApplication(application = { modules(appModule, testModule) }) { App() }
                }
            }

            onNodeWithTag("open-settings").performClick()
            waitForIdle()

            onNodeWithTag("settings-title").assertIsDisplayed()
        }
}
