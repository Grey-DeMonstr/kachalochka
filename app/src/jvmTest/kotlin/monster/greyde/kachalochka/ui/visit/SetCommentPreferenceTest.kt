package monster.greyde.kachalochka.ui.visit

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SetCommentPreferenceTest {
    @Test
    fun the_comment_kept_in_memory_starts_hidden_and_keeps_the_choice() =
        runTest {
            val preference = InMemorySetCommentPreference()
            assertFalse(preference.shown.value)

            preference.set(true)

            assertTrue(preference.shown.value)
        }

    @Test
    fun show_everyone_starts_clear() {
        assertFalse(ShowEveryone().checked)
    }
}
