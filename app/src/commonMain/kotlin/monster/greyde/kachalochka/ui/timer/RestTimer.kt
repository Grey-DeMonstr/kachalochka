package monster.greyde.kachalochka.ui.timer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import monster.greyde.kachalochka.core.domain.gym.REST_DURATION
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** One timer for the whole app, so every screen's top bar shows the same rest. */
class RestTimer(
    private val clock: Clock,
    val duration: Duration = REST_DURATION,
) {
    private val started = MutableStateFlow<Instant?>(null)
    val startedAt: StateFlow<Instant?> = started

    fun start() {
        started.value = clock.now()
    }
}
