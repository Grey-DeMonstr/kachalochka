package monster.greyde.kachalochka.core.domain.gym

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class RestTest {
    @Test
    fun an_idle_timer_shows_the_full_duration() {
        assertEquals(90.seconds, restRemaining(null, REST_DURATION, T0))
    }

    @Test
    fun a_running_timer_counts_down() {
        assertEquals(70.seconds, restRemaining(T0, REST_DURATION, T0 + 20.seconds))
    }

    @Test
    fun an_elapsed_timer_returns_to_the_full_duration() {
        assertEquals(90.seconds, restRemaining(T0, REST_DURATION, T0 + 91.seconds))
    }

    @Test
    fun a_clock_behind_the_start_never_shows_more_than_the_duration() {
        assertEquals(90.seconds, restRemaining(T0, REST_DURATION, T0 - 5.seconds))
    }
}
