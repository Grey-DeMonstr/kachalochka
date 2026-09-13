package monster.greyde.kachalochka

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import monster.greyde.kachalochka.core.data.supabase.SupabaseCredentials
import monster.greyde.kachalochka.di.appModule
import monster.greyde.kachalochka.di.platformModule
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration
import org.koin.dsl.module
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AppTest {
    // `core` reads whatever credentials the build generated, and a checkout has none, so the
    // backend state the home screen shows is only worth asserting against a configured pair.
    private val configuredBackend =
        module {
            single { SupabaseCredentials("https://project.supabase.test", "anon-key") }
        }

    @Composable
    private fun TestApp() {
        KoinApplication(
            configuration =
                koinConfiguration {
                    allowOverride(true)
                    modules(appModule, platformModule(), configuredBackend)
                },
        ) { App() }
    }

    @Test
    fun the_app_opens_on_the_home_screen() =
        runNavigationUiTest(content = { TestApp() }) {
            onNodeWithTag("home-title").assertIsDisplayed()
        }

    @Test
    fun home_shows_the_backend_state_its_view_model_read_from_core() =
        runNavigationUiTest(content = { TestApp() }) {
            onNodeWithTag("backend-state").assertTextEquals("Backend configured")
        }

    @Test
    fun home_navigates_to_settings() =
        runNavigationUiTest(content = { TestApp() }) {
            onNodeWithTag("open-settings").performClick()
            waitForIdle()

            onNodeWithTag("settings-title").assertIsDisplayed()
        }
}
