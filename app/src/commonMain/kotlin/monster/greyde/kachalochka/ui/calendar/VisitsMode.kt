package monster.greyde.kachalochka.ui.calendar

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class VisitsMode {
    Calendar,
    Feed,
}

fun visitsModeOrCalendar(name: String?): VisitsMode =
    VisitsMode.entries.firstOrNull { it.name == name } ?: VisitsMode.Calendar

/** How the visits screen shows the visits, a device setting. */
interface VisitsModePreference {
    val mode: StateFlow<VisitsMode>

    suspend fun set(mode: VisitsMode)
}

class InMemoryVisitsModePreference : VisitsModePreference {
    private val state = MutableStateFlow(VisitsMode.Calendar)

    override val mode: StateFlow<VisitsMode> = state

    override suspend fun set(mode: VisitsMode) {
        state.value = mode
    }
}
