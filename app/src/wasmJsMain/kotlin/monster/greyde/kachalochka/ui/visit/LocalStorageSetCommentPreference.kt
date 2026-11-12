package monster.greyde.kachalochka.ui.visit

import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LocalStorageSetCommentPreference : SetCommentPreference {
    private val key = "set_comment_shown"
    private val state = MutableStateFlow(read())

    override val shown: StateFlow<Boolean> = state

    override suspend fun set(shown: Boolean) {
        runCatching { localStorage.setItem(key, shown.toString()) }
        state.value = shown
    }

    private fun read(): Boolean =
        runCatching {
            localStorage.getItem(key) == "true"
        }.getOrDefault(false)
}
