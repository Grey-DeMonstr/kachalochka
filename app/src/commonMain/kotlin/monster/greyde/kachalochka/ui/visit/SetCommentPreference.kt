package monster.greyde.kachalochka.ui.visit

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Whether the set form has a comment field, a device setting. */
interface SetCommentPreference {
    val shown: StateFlow<Boolean>

    suspend fun set(shown: Boolean)
}

class InMemorySetCommentPreference : SetCommentPreference {
    private val state = MutableStateFlow(false)

    override val shown: StateFlow<Boolean> = state

    override suspend fun set(shown: Boolean) {
        state.value = shown
    }
}

/** Whether the set form lists every account; kept in memory until the app restarts. */
class ShowEveryone {
    var checked = false
}
