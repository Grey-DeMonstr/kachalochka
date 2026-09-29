package monster.greyde.kachalochka.ui.strings

/** Runs [block] with the app speaking English, then back in the Russian other tests expect. */
fun <T> inEnglish(block: () -> T): T {
    AppStrings.set(EnStrings)
    try {
        return block()
    } finally {
        AppStrings.set(RuStrings)
    }
}
