package monster.greyde.kachalochka.ui.calendar

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import monster.greyde.kachalochka.core.domain.identity.UserId

/** [person] whose cards [viewer]'s feed leaves out. */
data class HiddenPerson(
    val viewer: UserId,
    val person: UserId,
)

fun HiddenPerson.stored(): String = "${viewer.value}/${person.value}"

fun hiddenPeople(stored: Iterable<String>): Set<HiddenPerson> =
    stored
        .mapNotNull { pair ->
            runCatching {
                val (viewer, person) = pair.split('/')
                HiddenPerson(UserId(viewer), UserId(person))
            }.getOrNull()
        }.toSet()

/** Whom the feed hides, a device setting. */
interface FeedPeoplePreference {
    val hidden: StateFlow<Set<HiddenPerson>>

    suspend fun set(hidden: Set<HiddenPerson>)
}

class InMemoryFeedPeoplePreference : FeedPeoplePreference {
    private val state = MutableStateFlow(emptySet<HiddenPerson>())

    override val hidden: StateFlow<Set<HiddenPerson>> = state

    override suspend fun set(hidden: Set<HiddenPerson>) {
        state.value = hidden
    }
}
