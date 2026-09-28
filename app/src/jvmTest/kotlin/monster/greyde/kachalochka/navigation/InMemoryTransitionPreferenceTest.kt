package monster.greyde.kachalochka.navigation

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InMemoryTransitionPreferenceTest {
    @Test
    fun the_default_length_is_a_short_fade() {
        assertEquals(DEFAULT_TRANSITION_MILLIS, InMemoryTransitionPreference().millis.value)
        assertEquals(150, DEFAULT_TRANSITION_MILLIS)
    }

    @Test
    fun setting_a_length_emits_it() =
        runTest {
            val preference = InMemoryTransitionPreference()

            preference.millis.test {
                assertEquals(DEFAULT_TRANSITION_MILLIS, awaitItem())
                preference.set(0)
                assertEquals(0, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
