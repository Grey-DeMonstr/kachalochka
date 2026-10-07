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

/**
 * A link icon, then each of [linked] as its owner's avatar and its name; with [onOpen], tapping
 * one opens it.
 */
@Composable
fun LinkedMachines(
    linked: List<LinkedMachineUi>,
    tag: String,
    onOpen: ((LinkedMachineUi) -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val muted = colors.onBackground.copy(alpha = 0.52f)
    FlowRow(
        Modifier.testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PhosphorIcons.LinkSimple, null, tint = muted, modifier = Modifier.size(14.dp))
        linked.forEach { machine ->
            val opened = onOpen?.let { open -> Modifier.clickable { open(machine) } } ?: Modifier
            Row(
                opened.testTag("$tag-${machine.machineId.value}"),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PersonAvatar(
                    machine.owner.userId,
                    machine.owner.displayName,
                    machine.owner.avatar,
                    size = 18.dp,
                )
                Text(
                    machine.name,
                    fontSize = 13.sp,
                    color = if (onOpen != null) colors.tertiary else muted,
                )
            }
        }
    }
}
