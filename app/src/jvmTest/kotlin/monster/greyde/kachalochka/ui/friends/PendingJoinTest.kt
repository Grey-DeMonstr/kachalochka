package monster.greyde.kachalochka.ui.friends

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PendingJoinTest {
    private val gym = signedInGym()
    private val pending = PendingJoin(gym.joinCodes, gym.friends)

    @Test
    fun nothing_stored_asks_nothing() =
        runTest {
            assertNull(pending.consume())
            assertEquals(0, gym.friends.reads)
        }

    @Test
    fun a_stored_code_joins_its_group_once() =
        runTest {
            val group = gym.friends.group("Зал на Лесной", owner = OLEG, code = "ABCD2345")
            gym.joinCodes.save("ABCD2345")
            assertTrue(pending.waiting)

            assertEquals(JoinOutcome.Joined(group.id), pending.consume())
            assertNull(pending.consume())
            assertEquals(listOf(OLEG, ME), gym.friends.members.getValue(group.id))
            assertFalse(pending.waiting)
        }

    @Test
    fun a_declined_code_is_forgotten_without_asking_the_server() =
        runTest {
            gym.joinCodes.save("ABCD2345")

            pending.decline()

            assertFalse(pending.waiting)
            assertNull(pending.consume())
            assertEquals(0, gym.friends.reads)
        }

    @Test
    fun an_unknown_code_is_reported_and_forgotten() =
        runTest {
            gym.joinCodes.save("ZZZZ2345")

            assertEquals(JoinOutcome.NotFound, pending.consume())
            assertNull(gym.joinCodes.code())
        }

    @Test
    fun a_code_that_could_not_reach_the_server_waits_for_the_next_start() =
        runTest {
            gym.joinCodes.save("ABCD2345")
            gym.friends.offline = true

            assertNull(pending.consume())
            assertEquals("ABCD2345", gym.joinCodes.code())
        }
}
