package monster.greyde.kachalochka.ui.strings

/** Runs [block] with the app speaking English, then back in Russian, as every other test expects. */
fun <T> inEnglish(block: () -> T): T {
    AppStrings.set(EnStrings)
    try {
        return block()
    } finally {
        AppStrings.set(RuStrings)
    }
}
