package monster.greyde.kachalochka

/** Where a background failure no screen shows is written down for whoever debugs it. */
fun interface FailureLog {
    fun record(failure: Throwable)
}
