package monster.greyde.kachalochka.ui.calendar

import kotlinx.coroutines.flow.StateFlow
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.settings.DeviceKey
import monster.greyde.kachalochka.ui.settings.DeviceSetting
import monster.greyde.kachalochka.ui.settings.DeviceStore

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
class FeedPeoplePreference(
    private val store: DeviceStore,
) : DeviceSetting<Set<SelectedPerson>>(selectedPeople(store.read(KEY).orEmpty())) {
    val selected: StateFlow<Set<SelectedPerson>> = state

    override suspend fun save(value: Set<SelectedPerson>) =
        store.write(KEY, value.map { it.stored() }.toSet())

    private companion object {
        val KEY = DeviceKey.TextSet("feed_selected_person")
    }
}
