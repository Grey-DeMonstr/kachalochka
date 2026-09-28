package monster.greyde.kachalochka.ui.friends

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

            assertEquals(JoinOutcome.Joined(group.id), pending.consume())
            assertNull(gym.joinCodes.code())
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
