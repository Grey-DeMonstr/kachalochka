package monster.greyde.kachalochka.ui.share

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Whether a visit shared as text is grouped by tag, a device setting. */
interface ShareGroupingPreference {
    val groupByTag: StateFlow<Boolean>

    suspend fun set(groupByTag: Boolean)
}

class InMemoryShareGroupingPreference : ShareGroupingPreference {
    private val state = MutableStateFlow(false)

    override val groupByTag: StateFlow<Boolean> = state

    override suspend fun set(groupByTag: Boolean) {
        state.value = groupByTag
    }
}
