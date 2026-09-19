package monster.greyde.kachalochka.navigation

import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
object SettingsRoute

@Serializable
data class VisitRoute(
    val visitId: String,
)

@Serializable
data class MachinePickerRoute(
    val visitId: String,
    val selectedMachineId: String? = null,
)
