package monster.greyde.kachalochka.ui.timer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.fakes.MutableClock
import monster.greyde.kachalochka.fakes.RecordingRestAlarm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class RestSoundSchedulerTest {
    private val clock = MutableClock(Instant.fromEpochSeconds(1_000))
    private val timer = RestTimer(clock)
    private val sounds = InMemoryRestSoundPreference()
    private val alarm = RecordingRestAlarm()

    private fun TestScope.running() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            RestSoundScheduler(timer, sounds, alarm, clock).run()
        }
    }

    @Test
    fun a_rest_with_the_sound_on_schedules_its_end() =
        runTest {
            sounds.set(RestSound(enabled = true, uri = "content://sound/1"))
            running()

            timer.start()

            assertEquals(
                listOf<Pair<Instant, String?>>(
                    Instant.fromEpochSeconds(1_090) to "content://sound/1",
                ),
                alarm.scheduled,
            )
        }

    @Test
    fun the_sound_off_by_default_touches_no_alarm() =
        runTest {
            running()

            timer.start()

            assertEquals(emptyList(), alarm.scheduled)
            assertEquals(0, alarm.cancels)
        }

    @Test
    fun a_restart_schedules_the_new_end() =
        runTest {
            sounds.set(RestSound(enabled = true))
            running()

            timer.start()
            clock.current += 30.seconds
            timer.start()

            assertEquals(
                listOf(Instant.fromEpochSeconds(1_090), Instant.fromEpochSeconds(1_120)),
                alarm.scheduled.map { it.first },
            )
        }

    @Test
    fun switching_the_sound_off_cancels_the_rest_s_sound() =
        runTest {
            sounds.set(RestSound(enabled = true))
            running()
            timer.start()

            sounds.set(RestSound(enabled = false))

            assertEquals(1, alarm.cancels)
        }

    @Test
    fun switching_the_sound_on_during_a_rest_schedules_its_end() =
        runTest {
            running()
            timer.start()
            clock.current += 40.seconds

            sounds.set(RestSound(enabled = true))

            assertEquals(listOf(Instant.fromEpochSeconds(1_090)), alarm.scheduled.map { it.first })
        }

    @Test
    fun switching_the_sound_on_after_the_rest_schedules_nothing() =
        runTest {
            running()
            timer.start()
            clock.current += 90.seconds

            sounds.set(RestSound(enabled = true))

            assertEquals(emptyList(), alarm.scheduled)
        }

    @Test
    fun another_sound_during_a_rest_takes_over_its_end() =
        runTest {
            sounds.set(RestSound(enabled = true))
            running()
            timer.start()

            sounds.set(RestSound(enabled = true, uri = "content://sound/2"))

            assertEquals(
                listOf(null, "content://sound/2"),
                alarm.scheduled.map { it.second },
            )
        }
}
