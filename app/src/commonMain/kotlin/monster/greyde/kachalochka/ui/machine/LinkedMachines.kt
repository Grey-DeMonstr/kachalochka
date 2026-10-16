package monster.greyde.kachalochka.ui.machine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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

/** One line of a linked name; the icon and the avatars centre on it. */
private val NAME_LINE = 18.sp

/** About a quarter of the 18 dp avatar. */
private val AVATAR_OVERLAP = 5.dp

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
    val line = with(LocalDensity.current) { NAME_LINE.toDp() }
    Row(Modifier.testTag(tag), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.height(line), contentAlignment = Alignment.Center) {
            Icon(
                PhosphorIcons.LinkSimple,
                null,
                tint = muted,
                modifier = Modifier.requiredSize(14.dp).testTag("$tag-icon"),
            )
        }
        // A separate column after the icon, so a long name wraps under itself.
        FlowRow(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            groups.forEach { group ->
                val machine = group.machines.first()
                val opened =
                    onOpen?.let { open -> Modifier.clickable { open(machine) } } ?: Modifier
                Row(
                    opened.testTag("$tag-${machine.machineId.value}"),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(Modifier.height(line), contentAlignment = Alignment.Center) {
                        Row(
                            Modifier.wrapContentHeight(unbounded = true),
                            horizontalArrangement = Arrangement.spacedBy(-AVATAR_OVERLAP),
                        ) {
                            group.machines.forEachIndexed { index, entry ->
                                PersonAvatar(
                                    entry.owner.userId,
                                    entry.owner.displayName,
                                    entry.owner.avatar,
                                    // The first owner stays whole; the disc hides the one below.
                                    Modifier
                                        .zIndex((group.machines.size - index).toFloat())
                                        .background(colors.background, CircleShape)
                                        .testTag("$tag-avatar-${entry.machineId.value}"),
                                    size = 18.dp,
                                )
                            }
                        }
                    }
                    group.name?.let {
                        Text(it, fontSize = 13.sp, lineHeight = NAME_LINE, color = nameColor)
                    }
                }
            }
        }
    }
}
