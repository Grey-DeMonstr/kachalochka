package monster.greyde.kachalochka.ui.machine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.friends.Friend
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.sameName
import monster.greyde.kachalochka.ui.account.PersonAvatar
import monster.greyde.kachalochka.ui.icons.PhosphorIcons

/** A machine linked with the one shown, drawn after its owner's avatar. */
data class LinkedMachineUi(
    val machineId: MachineId,
    val owner: Friend,
    val name: String,
    /** The account's own machine, which opens in its form. */
    val own: Boolean = false,
)

/** The machines of one [name], or of the shown machine's own name when [name] is null. */
data class LinkedNameGroup(
    val name: String?,
    val machines: List<LinkedMachineUi>,
)

/** Groups [linked] by name, in order of first appearance; [ownName] is written as null. */
fun groupLinkedNames(
    linked: List<LinkedMachineUi>,
    ownName: String?,
): List<LinkedNameGroup> {
    val groups = mutableListOf<Pair<String, MutableList<LinkedMachineUi>>>()
    linked.forEach { machine ->
        val group = groups.firstOrNull { sameName(it.first, machine.name) }
        when (group) {
            null -> groups += machine.name to mutableListOf(machine)
            else -> group.second += machine
        }
    }
    return groups.map { (name, machines) ->
        LinkedNameGroup(name.takeUnless { ownName != null && sameName(name, ownName) }, machines)
    }
}

/**
 * A link icon, then [linked] as owners' avatars and names. Without [onOpen], machines of one name
 * share it and the shown machine's [ownName] is not written. With [onOpen], each machine has its
 * own row, and tapping it opens the machine.
 */
@Composable
fun LinkedMachines(
    linked: List<LinkedMachineUi>,
    tag: String,
    ownName: String? = null,
    onOpen: ((LinkedMachineUi) -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val muted = colors.onBackground.copy(alpha = 0.52f)
    val nameColor = if (onOpen != null) colors.tertiary else muted
    val groups =
        if (onOpen == null) {
            groupLinkedNames(linked, ownName)
        } else {
            linked.map { LinkedNameGroup(it.name, listOf(it)) }
        }
    FlowRow(
        Modifier.testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PhosphorIcons.LinkSimple, null, tint = muted, modifier = Modifier.size(14.dp))
        groups.forEach { group ->
            val machine = group.machines.first()
            val opened = onOpen?.let { open -> Modifier.clickable { open(machine) } } ?: Modifier
            Row(
                opened.testTag("$tag-${machine.machineId.value}"),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                group.machines.forEach {
                    PersonAvatar(
                        it.owner.userId,
                        it.owner.displayName,
                        it.owner.avatar,
                        size = 18.dp,
                    )
                }
                group.name?.let {
                    Text(it, fontSize = 13.sp, color = nameColor)
                }
            }
        }
    }
}
