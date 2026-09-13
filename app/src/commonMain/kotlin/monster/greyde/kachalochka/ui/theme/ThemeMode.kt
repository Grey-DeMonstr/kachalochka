package monster.greyde.kachalochka.ui.theme

enum class ThemeMode { System, Light, Dark }

fun ThemeMode.resolvesToDark(systemInDark: Boolean): Boolean =
    when (this) {
        ThemeMode.System -> systemInDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
