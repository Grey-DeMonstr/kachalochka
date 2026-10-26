package monster.greyde.kachalochka.ui.calendar

import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LocalStorageVisitsModePreference : VisitsModePreference {
    private val key = "visits_mode"
    private val state = MutableStateFlow(read())

    override val mode: StateFlow<VisitsMode> = state

    override suspend fun set(mode: VisitsMode) {
        runCatching { localStorage.setItem(key, mode.name) }
        state.value = mode
    }

    private fun read(): VisitsMode =
        runCatching { visitsModeOrCalendar(localStorage.getItem(key)) }
            .getOrDefault(VisitsMode.Calendar)
}
