package monster.greyde.kachalochka.ui.strings

enum class AppLanguage { System, English, Russian }

/** [systemTag] is the device's language, such as "ru-RU"; only Russian keeps the app Russian. */
fun AppLanguage.strings(systemTag: String): Strings =
    when (this) {
        AppLanguage.English -> EnStrings
        AppLanguage.Russian -> RuStrings
        AppLanguage.System -> if (systemTag.lowercase().startsWith("ru")) RuStrings else EnStrings
    }

fun languageOrSystem(name: String?): AppLanguage =
    AppLanguage.entries.firstOrNull { it.name == name } ?: AppLanguage.System
