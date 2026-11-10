package monster.greyde.kachalochka.ui.strings

/** Runs [block] with the app speaking Russian, then back in the English other tests expect. */
fun <T> inRussian(block: () -> T): T {
    val before = AppStrings.current
    AppStrings.set(RuStrings)
    try {
        return block()
    } finally {
        AppStrings.set(before)
    }
}
