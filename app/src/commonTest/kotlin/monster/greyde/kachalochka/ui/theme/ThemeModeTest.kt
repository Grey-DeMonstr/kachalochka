package monster.greyde.kachalochka.ui.theme

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeModeTest {
    @Test
    fun system_mode_follows_the_system() {
        assertTrue(ThemeMode.System.resolvesToDark(systemInDark = true))
        assertFalse(ThemeMode.System.resolvesToDark(systemInDark = false))
    }

    @Test
    fun explicit_modes_ignore_the_system() {
        assertTrue(ThemeMode.Dark.resolvesToDark(systemInDark = false))
        assertFalse(ThemeMode.Light.resolvesToDark(systemInDark = true))
    }
}
