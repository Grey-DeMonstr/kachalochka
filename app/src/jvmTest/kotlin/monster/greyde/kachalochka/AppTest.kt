package monster.greyde.kachalochka

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import monster.greyde.kachalochka.di.appModule
import monster.greyde.kachalochka.ui.theme.InMemoryThemePreference
import monster.greyde.kachalochka.ui.theme.ThemePreference
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AppTest {
    private val testModule =
        module {
            single<ThemePreference> { InMemoryThemePreference() }
        }

    @Composable
    private fun TestApp() {
        KoinApplication(application = { modules(appModule, testModule) }) { App() }
    }

    @Test
    fun the_app_opens_on_the_home_screen() =
        runNavigationUiTest(content = { TestApp() }) {
            onNodeWithTag("home-title").assertIsDisplayed()
        }

    @Test
    fun home_navigates_to_settings() =
        runNavigationUiTest(content = { TestApp() }) {
            onNodeWithTag("open-settings").performClick()
            waitForIdle()

            onNodeWithTag("settings-title").assertIsDisplayed()
        }
}
