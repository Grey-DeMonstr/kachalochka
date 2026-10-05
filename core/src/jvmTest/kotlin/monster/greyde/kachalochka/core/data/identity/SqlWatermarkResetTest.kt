package monster.greyde.kachalochka.core.data.identity

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.db.inMemoryDatabase
import monster.greyde.kachalochka.core.data.sync.SyncWatermarks
import monster.greyde.kachalochka.core.domain.gym.T0
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SqlWatermarkResetTest {
    private val watermarks = SyncWatermarks(inMemoryDatabase())
    private val reset = SqlWatermarkReset(watermarks, Dispatchers.Unconfined)
    private val ivan = UserId("11111111-1111-4111-8111-111111111111")
    private val misha = UserId("22222222-2222-4222-8222-222222222222")

    @Test
    fun the_next_pull_of_a_forgotten_owner_starts_from_scratch() =
        runTest {
            watermarks.advance(ivan, T0)
            watermarks.advance(misha, T0)

            reset.forget(ivan)

            assertNull(watermarks.lastPullAt(ivan))
            assertEquals(T0, watermarks.lastPullAt(misha))
        }
}
