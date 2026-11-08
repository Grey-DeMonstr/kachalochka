package monster.greyde.kachalochka

import kotlin.time.Clock
import kotlin.time.Instant

/** Keeps the newest failure in memory, with its time, for the network check's report. */
class LastFailure(
    private val clock: Clock,
    private val log: FailureLog,
) : FailureLog {
    /** The first line of the failure only: supabase-kt adds the request headers, with tokens. */
    var last: Pair<Instant, String>? = null
        private set

    override fun record(failure: Throwable) {
        last = clock.now() to failure.toString().lines().first()
        log.record(failure)
    }
}
