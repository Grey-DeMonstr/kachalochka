package monster.greyde.kachalochka.core.data.identity

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import monster.greyde.kachalochka.core.data.sync.SyncWatermarks
import monster.greyde.kachalochka.core.domain.identity.UserId

class SqlWatermarkReset(
    private val watermarks: SyncWatermarks,
    private val dispatcher: CoroutineDispatcher,
) : WatermarkReset {
    override suspend fun forget(owner: UserId) =
        withContext(dispatcher) { watermarks.forget(owner) }
}
