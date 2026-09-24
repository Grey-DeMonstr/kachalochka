package monster.greyde.kachalochka.core.data.sync

/** Asks for a pass; whether one runs now, later or never is the platform's business. */
fun interface SyncTrigger {
    fun request()
}
