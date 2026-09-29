package monster.greyde.kachalochka.ui.strings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface LanguagePreference {
    val language: StateFlow<AppLanguage>

    suspend fun set(language: AppLanguage)
}

class InMemoryLanguagePreference(
    initial: AppLanguage = AppLanguage.System,
) : LanguagePreference {
    private val state = MutableStateFlow(initial)

    override val language: StateFlow<AppLanguage> = state

    override suspend fun set(language: AppLanguage) {
        state.value = language
    }
}

/** The device's language tag, such as "ru-RU". */
fun interface SystemLanguage {
    fun tag(): String
}
