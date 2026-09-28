package monster.greyde.kachalochka.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TransitionsTest {
    @Test
    fun zero_switches_screens_instantly() {
        assertEquals(EnterTransition.None, screenEnter(0))
        assertEquals(ExitTransition.None, screenExit(0))
    }

    @Test
    fun a_length_fades_for_that_many_milliseconds() {
        assertEquals(fadeIn(tween(250)), screenEnter(250))
        assertEquals(fadeOut(tween(250)), screenExit(250))
    }

    @Test
    fun typed_digits_within_the_range_are_a_length() {
        assertEquals(0, transitionMillisOrNull("0"))
        assertEquals(1000, transitionMillisOrNull("1000"))
    }

    @Test
    fun a_cleared_field_means_no_animation() {
        assertEquals(0, transitionMillisOrNull(""))
    }

    @Test
    fun anything_else_is_rejected() {
        assertNull(transitionMillisOrNull("1001"))
        assertNull(transitionMillisOrNull("-5"))
        assertNull(transitionMillisOrNull("1.5"))
        assertNull(transitionMillisOrNull("99999999999"))
    }
}
