package monster.greyde.kachalochka.ui.calendar

import kotlinx.coroutines.flow.StateFlow
import monster.greyde.kachalochka.ui.settings.DeviceKey
import monster.greyde.kachalochka.ui.settings.DeviceSetting
import monster.greyde.kachalochka.ui.settings.DeviceStore

enum class VisitsMode {
    Calendar,
    Feed,
}

fun visitsModeOrCalendar(name: String?): VisitsMode =
    VisitsMode.entries.firstOrNull { it.name == name } ?: VisitsMode.Calendar

/** How the visits screen shows the visits, a device setting. */
class VisitsModePreference(
    private val store: DeviceStore,
) : DeviceSetting<VisitsMode>(visitsModeOrCalendar(store.read(KEY))) {
    val mode: StateFlow<VisitsMode> = state

    override suspend fun save(value: VisitsMode) = store.write(KEY, value.name)

    private companion object {
        val KEY = DeviceKey.Text("visits_mode")
    }
}
