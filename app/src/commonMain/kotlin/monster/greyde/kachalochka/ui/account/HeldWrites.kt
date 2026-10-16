package monster.greyde.kachalochka.ui.account

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Writes an open screen holds back, such as a plan name still being typed. An account switch
 * writes them first, so they reach the account they were made in.
 */
class HeldWrites {
    private val held = MutableStateFlow<Set<Held>>(emptySet())

    private class Held(
        val write: suspend () -> Unit,
    )

    /** Holds [write] until the returned function releases it. */
    fun hold(write: suspend () -> Unit): () -> Unit {
        val entry = Held(write)
        held.update { it + entry }
        return { held.update { it - entry } }
    }

    suspend fun writeAll() = held.value.forEach { it.write() }
}
