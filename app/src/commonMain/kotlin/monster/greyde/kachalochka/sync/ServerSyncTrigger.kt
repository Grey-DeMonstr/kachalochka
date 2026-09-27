package monster.greyde.kachalochka.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import monster.greyde.kachalochka.core.data.sync.SyncTrigger

/** Every write already went to the server, so a pass only has the screens read it again. */
class ServerSyncTrigger : SyncTrigger {
    private val passes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override val completed: Flow<Unit> = passes

    override fun request() {
        passes.tryEmit(Unit)
    }
}
