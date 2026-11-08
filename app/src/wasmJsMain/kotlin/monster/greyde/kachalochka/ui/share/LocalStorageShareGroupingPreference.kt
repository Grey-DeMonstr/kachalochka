package monster.greyde.kachalochka.ui.share

import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LocalStorageShareGroupingPreference : ShareGroupingPreference {
    private val key = "share_group_by_tag"
    private val state = MutableStateFlow(read())

    override val groupByTag: StateFlow<Boolean> = state

    override suspend fun set(groupByTag: Boolean) {
        runCatching { localStorage.setItem(key, groupByTag.toString()) }
        state.value = groupByTag
    }

    private fun read(): Boolean =
        runCatching { localStorage.getItem(key) == "true" }.getOrDefault(false)
}
