package monster.greyde.kachalochka.core.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class CatchingTest {
    @Test
    fun a_value_is_a_success() =
        runTest {
            assertEquals(Result.success(7), catching { 7 })
        }

    @Test
    fun a_failure_is_kept() =
        runTest {
            val result = catching { error("offline") }

            assertIs<IllegalStateException>(result.exceptionOrNull())
        }

    @Test
    fun a_cancellation_is_thrown_on() =
        runTest {
            assertFailsWith<CancellationException> {
                catching { throw CancellationException("left") }
            }
        }
}
