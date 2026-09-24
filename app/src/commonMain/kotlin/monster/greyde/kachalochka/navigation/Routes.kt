package monster.greyde.kachalochka.navigation

import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
object SettingsRoute

@Serializable
object CalendarRoute

@Serializable
data class VisitRoute(
    val visitId: String,
)

@Serializable
data class MachinePickerRoute(
    val visitId: String,
    val selectedMachineId: String? = null,
)

@Serializable
data class MachineFormRoute(
    val machineId: String? = null,
    val copyOfId: String? = null,
    val name: String = "",
)
