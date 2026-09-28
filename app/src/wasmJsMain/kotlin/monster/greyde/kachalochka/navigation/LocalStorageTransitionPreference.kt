package monster.greyde.kachalochka.navigation

import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LocalStorageTransitionPreference : TransitionPreference {
    private val key = "transition_millis"
    private val state = MutableStateFlow(read())

    override val millis: StateFlow<Int> = state

    override suspend fun set(millis: Int) {
        runCatching { localStorage.setItem(key, millis.toString()) }
        state.value = millis
    }

    private fun read(): Int =
        runCatching { localStorage.getItem(key)?.let(::transitionMillisOrNull) }
            .getOrNull() ?: DEFAULT_TRANSITION_MILLIS
}
