package monster.greyde.kachalochka.navigation

import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
object SettingsRoute

@Serializable
object CalendarRoute

@Serializable
object MachineListRoute

@Serializable
data class VisitRoute(
    val day: String,
)

@Serializable
data class MachinePickerRoute(
    val day: String,
    val selectedMachineId: String? = null,
)

@Serializable
data class MachineFormRoute(
    val machineId: String? = null,
    val copyOfId: String? = null,
    val name: String = "",
    val fromList: Boolean = false,
)

@Serializable
data class FriendVisitRoute(
    val userId: String,
    val name: String,
    val day: String,
)
