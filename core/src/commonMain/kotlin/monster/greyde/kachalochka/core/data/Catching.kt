package monster.greyde.kachalochka.core.data

import kotlinx.coroutines.CancellationException

/** Like runCatching, but a cancelled coroutine stays cancelled. */
suspend fun <T> catching(work: suspend () -> T): Result<T> =
    try {
        Result.success(work())
    } catch (stopped: CancellationException) {
        throw stopped
    } catch (failed: Exception) {
        Result.failure(failed)
    }
