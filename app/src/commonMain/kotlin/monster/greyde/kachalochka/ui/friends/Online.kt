package monster.greyde.kachalochka.ui.friends

import kotlinx.coroutines.CancellationException

/** Like runCatching, but a cancelled coroutine stays cancelled. */
internal suspend fun <T> reading(read: suspend () -> T): Result<T> =
    try {
        Result.success(read())
    } catch (stopped: CancellationException) {
        throw stopped
    } catch (failed: Exception) {
        Result.failure(failed)
    }
