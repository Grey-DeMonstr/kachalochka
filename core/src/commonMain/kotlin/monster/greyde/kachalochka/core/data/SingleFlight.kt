package monster.greyde.kachalochka.core.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Runs one work per key at a time: a call for a key already running waits for that run's result.
 * When the caller running it is cancelled, the waiting callers run their retry instead.
 */
class SingleFlight<K, V> {
    private val lock = Mutex()
    private val inFlight = mutableMapOf<K, CompletableDeferred<V>>()

    suspend fun run(
        key: K,
        retry: suspend () -> V,
        work: suspend () -> V,
    ): V {
        var leads = false
        val shared =
            lock.withLock {
                inFlight.getOrPut(key) { CompletableDeferred<V>().also { leads = true } }
            }
        if (!leads) {
            return try {
                shared.await()
            } catch (stopped: CancellationException) {
                // The run was cancelled with the caller that ran it, not with this one.
                currentCoroutineContext().ensureActive()
                retry()
            }
        }
        // A cancellation goes to the waiting callers too, which then retry.
        val result = runCatching { work() }
        withContext(NonCancellable) { lock.withLock { inFlight.remove(key) } }
        shared.completeWith(result)
        return result.getOrThrow()
    }
}
