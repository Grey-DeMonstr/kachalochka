package monster.greyde.kachalochka.sync

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ServerSyncTriggerTest {
    @Test
    fun a_request_tells_the_screens_to_read_again() =
        runTest {
            val trigger = ServerSyncTrigger()

            trigger.completed.test {
                trigger.request()

                awaitItem()
            }
        }
}
