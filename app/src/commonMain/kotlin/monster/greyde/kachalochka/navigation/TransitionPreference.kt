package monster.greyde.kachalochka.navigation

import kotlinx.coroutines.flow.StateFlow

interface TransitionPreference {
    val millis: StateFlow<Int>

    suspend fun set(millis: Int)
}
