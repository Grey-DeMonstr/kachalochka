package monster.greyde.kachalochka.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeModeTest {
    @Test
    fun a_stored_name_parses_back_to_its_mode() {
        assertEquals(ThemeMode.Dark, themeModeOrSystem("Dark"))
        assertEquals(ThemeMode.Light, themeModeOrSystem("Light"))
    }

    @Test
    fun an_absent_or_unknown_name_falls_back_to_system() {
        assertEquals(ThemeMode.System, themeModeOrSystem(null))
        assertEquals(ThemeMode.System, themeModeOrSystem("Sepia"))
        assertEquals(ThemeMode.System, themeModeOrSystem("dark"))
    }

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
