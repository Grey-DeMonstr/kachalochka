package monster.greyde.kachalochka.ui.strings

import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LocalStorageLanguagePreference : LanguagePreference {
    private val key = "language"
    private val state = MutableStateFlow(read())

    override val language: StateFlow<AppLanguage> = state

    override suspend fun set(language: AppLanguage) {
        runCatching { localStorage.setItem(key, language.name) }
        state.value = language
    }

    // A browser that refuses site storage throws on every access, so the choice lives as long as
    // the tab and no longer.
    private fun read(): AppLanguage =
        runCatching {
            languageOrSystem(
                localStorage.getItem(key),
            )
        }.getOrDefault(AppLanguage.System)
}
