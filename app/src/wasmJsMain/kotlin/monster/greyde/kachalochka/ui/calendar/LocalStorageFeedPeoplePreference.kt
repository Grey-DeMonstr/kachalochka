package monster.greyde.kachalochka.ui.calendar

import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LocalStorageFeedPeoplePreference : FeedPeoplePreference {
    private val key = "feed_selected_person"
    private val state = MutableStateFlow(read())

    override val selected: StateFlow<Set<SelectedPerson>> = state

    override suspend fun set(selected: Set<SelectedPerson>) {
        runCatching { localStorage.setItem(key, selected.joinToString(" ") { it.stored() }) }
        state.value = selected
    }

    private fun read(): Set<SelectedPerson> =
        runCatching { selectedPeople(localStorage.getItem(key).orEmpty().split(' ')) }
            .getOrDefault(emptySet())
}
