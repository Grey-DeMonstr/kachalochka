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

/** [fromList] is the form's own, handed on to the kept machine's form after a merge. */
@Serializable
data class LinkChooserRoute(
    val machineId: String,
    val fromList: Boolean,
)

@Serializable
data class FriendMachineRoute(
    val machineId: String,
    val ownerId: String,
)

@Serializable
data class FriendVisitRoute(
    val userId: String,
    val name: String,
    val day: String,
)

@Serializable
data class FriendCalendarRoute(
    val userId: String,
    val name: String,
)

@Serializable
data class GroupRoute(
    val groupId: String,
)

@Serializable
object FriendsRoute

@Serializable
object MeasuresRoute

/** [day] is ISO; null opens today. */
@Serializable
data class MeasurementFormRoute(
    val day: String? = null,
)

@Serializable
data class MeasureRoute(
    val measureId: String,
)

@Serializable
object PlansRoute

/** [planId] null opens a new plan. */
@Serializable
data class PlanRoute(
    val planId: String? = null,
)
