package monster.greyde.kachalochka.ui.timer

/** Paces the clocks on screen; tests replace it so nothing ticks unless they ask. */
fun interface Ticker {
    suspend fun awaitTick()
}
