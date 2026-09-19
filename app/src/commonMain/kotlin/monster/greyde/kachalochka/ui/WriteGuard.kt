package monster.greyde.kachalochka.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Runs one write at a time and drops calls made meanwhile, so a double tap writes once. */
class WriteGuard(
    private val scope: CoroutineScope,
) {
    private var running = false

    fun launch(write: suspend () -> Unit) {
        if (running) return
        running = true
        scope.launch {
            try {
                write()
            } finally {
                running = false
            }
        }
    }
}
