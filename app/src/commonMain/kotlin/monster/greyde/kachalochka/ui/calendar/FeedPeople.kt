package monster.greyde.kachalochka.ui.calendar

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import monster.greyde.kachalochka.core.domain.identity.UserId

/** The only [person] whose cards [viewer]'s feed shows. */
data class SelectedPerson(
    val viewer: UserId,
    val person: UserId,
)

fun SelectedPerson.stored(): String = "${viewer.value}/${person.value}"

fun selectedPeople(stored: Iterable<String>): Set<SelectedPerson> =
    stored
        .mapNotNull { pair ->
            runCatching {
                val (viewer, person) = pair.split('/')
                SelectedPerson(UserId(viewer), UserId(person))
            }.getOrNull()
        }.toSet()

/** Whom the feed shows alone, at most one person for each viewer, a device setting. */
interface FeedPeoplePreference {
    val selected: StateFlow<Set<SelectedPerson>>

    suspend fun set(selected: Set<SelectedPerson>)
}

class InMemoryFeedPeoplePreference : FeedPeoplePreference {
    private val state = MutableStateFlow(emptySet<SelectedPerson>())

    override val selected: StateFlow<Set<SelectedPerson>> = state

    override suspend fun set(selected: Set<SelectedPerson>) {
        state.value = selected
    }
}
