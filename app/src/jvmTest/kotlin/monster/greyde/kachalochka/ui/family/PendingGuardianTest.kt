package monster.greyde.kachalochka.ui.family

import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.domain.family.Acceptance
import monster.greyde.kachalochka.ui.friends.signedInGym
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PendingGuardianTest {
    private val gym = signedInGym()
    private val pending = PendingGuardian(gym.parentCodes, gym.family)

    @Test
    fun nothing_kept_asks_nothing() =
        runTest {
            assertFalse(pending.waiting)
            assertNull(pending.consume())
        }

    @Test
    fun a_kept_code_links_the_account_once() =
        runTest {
            gym.family.offered("PAPA2345", PAPA)
            gym.parentCodes.save("PAPA2345")

            assertEquals(Acceptance.Linked(PAPA.userId), pending.consume())
            assertFalse(pending.waiting)
            assertEquals(listOf(IVAN_MEMBER.userId to PAPA.userId), gym.family.links)
        }

    @Test
    fun a_code_that_cannot_reach_the_server_stays_for_the_next_start() =
        runTest {
            gym.parentCodes.save("PAPA2345")
            gym.family.offline = true

            assertNull(pending.consume())
            assertTrue(pending.waiting)
        }

    @Test
    fun an_unknown_code_is_reported_and_forgotten() =
        runTest {
            gym.parentCodes.save("ZZZZ2345")

            assertEquals(Acceptance.UnknownCode, pending.consume())
            assertFalse(pending.waiting)
        }

    @Test
    fun a_declined_code_is_forgotten_without_asking_the_server() =
        runTest {
            gym.parentCodes.save("PAPA2345")
            gym.family.offline = true

            pending.decline()

            assertFalse(pending.waiting)
        }
}
