package monster.greyde.kachalochka

import monster.greyde.kachalochka.fakes.MutableClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class LastFailureTest {
    @Test
    fun the_newest_failure_is_kept_with_its_time_and_still_logged() {
        val clock = MutableClock(Instant.fromEpochSeconds(1_700_000_000))
        val logged = mutableListOf<Throwable>()
        val failures = LastFailure(clock) { logged += it }
        val first = IllegalStateException("first")
        val second = IllegalStateException("second")

        assertNull(failures.last)
        failures.record(first)
        clock.current = Instant.fromEpochSeconds(1_700_000_060)
        failures.record(second)

        assertEquals(
            Instant.fromEpochSeconds(1_700_000_060) to "java.lang.IllegalStateException: second",
            failures.last,
        )
        assertEquals(listOf<Throwable>(first, second), logged)
    }

    @Test
    fun only_the_first_line_is_kept_so_request_headers_stay_out() {
        val failures = LastFailure(MutableClock(Instant.fromEpochSeconds(0))) {}

        failures.record(IllegalStateException("JWT expired\nHeaders: [Authorization=Bearer x]"))

        assertEquals("java.lang.IllegalStateException: JWT expired", failures.last?.second)
    }
}
