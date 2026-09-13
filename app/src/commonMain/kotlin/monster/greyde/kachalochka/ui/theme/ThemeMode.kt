package monster.greyde.kachalochka.ui.theme

enum class ThemeMode { System, Light, Dark }

internal fun themeModeOrSystem(name: String?): ThemeMode =
    ThemeMode.entries.firstOrNull { it.name == name } ?: ThemeMode.System

fun ThemeMode.resolvesToDark(systemInDark: Boolean): Boolean =
    when (this) {
        ThemeMode.System -> systemInDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
