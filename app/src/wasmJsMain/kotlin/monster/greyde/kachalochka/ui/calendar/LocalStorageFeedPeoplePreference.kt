package monster.greyde.kachalochka.ui.calendar

import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LocalStorageFeedPeoplePreference : FeedPeoplePreference {
    private val key = "feed_hidden_people"
    private val state = MutableStateFlow(read())

    override val hidden: StateFlow<Set<HiddenPerson>> = state

    override suspend fun set(hidden: Set<HiddenPerson>) {
        runCatching { localStorage.setItem(key, hidden.joinToString(" ") { it.stored() }) }
        state.value = hidden
    }

    private fun read(): Set<HiddenPerson> =
        runCatching { hiddenPeople(localStorage.getItem(key).orEmpty().split(' ')) }
            .getOrDefault(emptySet())
}
