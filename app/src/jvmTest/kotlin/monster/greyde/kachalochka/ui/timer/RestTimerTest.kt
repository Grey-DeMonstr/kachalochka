package monster.greyde.kachalochka.ui.timer

import monster.greyde.kachalochka.fakes.MutableClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RestTimerTest {
    @Test
    fun starting_records_the_clock_time_and_restarting_moves_it() {
        val clock = MutableClock(Instant.fromEpochSeconds(100))
        val timer = RestTimer(clock)
        assertNull(timer.startedAt.value)

        timer.start()
        clock.current += 30.seconds
        timer.start()

        assertEquals(Instant.fromEpochSeconds(130), timer.startedAt.value)
        assertEquals(90.seconds, timer.duration)
    }
}
