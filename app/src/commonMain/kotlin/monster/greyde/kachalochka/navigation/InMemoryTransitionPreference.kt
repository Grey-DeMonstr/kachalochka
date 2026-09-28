package monster.greyde.kachalochka.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class InMemoryTransitionPreference : TransitionPreference {
    private val state = MutableStateFlow(DEFAULT_TRANSITION_MILLIS)

    override val millis: StateFlow<Int> = state

    override suspend fun set(millis: Int) {
        state.value = millis
    }
}
