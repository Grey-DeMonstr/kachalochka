package monster.greyde.kachalochka.core.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SingleFlightTest {
    private val flight = SingleFlight<String, Int>()
    private val gate = CompletableDeferred<Int>()
    private var runs = 0

    private suspend fun work(): Int {
        runs++
        return gate.await()
    }

    @Test
    fun calls_for_one_key_at_once_share_one_run() =
        runTest {
            val first = async { flight.run("ivan", { -1 }) { work() } }
            val second = async { flight.run("ivan", { -1 }) { work() } }
            runCurrent()
            gate.complete(7)

            assertEquals(7 to 7, first.await() to second.await())
            assertEquals(1, runs)
        }

    @Test
    fun a_failure_reaches_every_caller() =
        runTest {
            supervisorScope {
                val first = async { flight.run("ivan", { -1 }) { work() } }
                val second = async { flight.run("ivan", { -1 }) { work() } }
                runCurrent()
                gate.completeExceptionally(IllegalStateException("offline"))

                assertFailsWith<IllegalStateException> { first.await() }
                assertFailsWith<IllegalStateException> { second.await() }
            }
        }

    @Test
    fun a_caller_waiting_on_a_cancelled_run_runs_the_retry() =
        runTest {
            val leader = async { flight.run("ivan", { -1 }) { work() } }
            val waiter = async { flight.run("ivan", { 9 }) { work() } }
            runCurrent()
            leader.cancel()

            assertEquals(9, waiter.await())
        }

    @Test
    fun a_finished_run_is_not_reused() =
        runTest {
            gate.complete(7)
            flight.run("ivan", { -1 }) { work() }
            flight.run("ivan", { -1 }) { work() }

            assertEquals(2, runs)
        }
}
