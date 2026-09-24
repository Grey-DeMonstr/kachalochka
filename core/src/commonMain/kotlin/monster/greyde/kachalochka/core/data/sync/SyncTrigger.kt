package monster.greyde.kachalochka.core.data.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Asks for a pass; whether one runs now, later or never is the platform's business. */
fun interface SyncTrigger {
    /** Emits after each pass, so a screen can show what it pulled. */
    val completed: Flow<Unit> get() = emptyFlow()

    fun request()
}
