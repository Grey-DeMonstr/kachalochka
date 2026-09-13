package monster.greyde.kachalochka.ui.theme

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InMemoryThemePreferenceTest {
    @Test
    fun the_default_mode_is_system() =
        runTest {
            InMemoryThemePreference().mode.test {
                assertEquals(ThemeMode.System, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun setting_a_mode_emits_it() =
        runTest {
            val preference = InMemoryThemePreference()

            preference.mode.test {
                assertEquals(ThemeMode.System, awaitItem())
                preference.set(ThemeMode.Dark)
                assertEquals(ThemeMode.Dark, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
