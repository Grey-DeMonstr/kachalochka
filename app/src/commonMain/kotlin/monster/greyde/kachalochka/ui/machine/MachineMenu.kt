package monster.greyde.kachalochka.ui.machine

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import monster.greyde.kachalochka.ui.components.SquareIconButton
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.strings.strings

/** The ⋮ menu of a machine screen, shown with each item whose action is given. */
@Composable
internal fun MachineMenu(
    onToPlan: (() -> Unit)? = null,
    onToday: (() -> Unit)? = null,
    onReorder: (() -> Unit)? = null,
    onUnlink: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    if (listOfNotNull(onToPlan, onToday, onReorder, onUnlink, onDelete).isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Box {
        SquareIconButton(
            PhosphorIcons.DotsThreeVertical,
            strings().more,
            { expanded = true },
            Modifier.testTag("machine-menu"),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            onToPlan?.let {
                MenuItem(
                    strings().toPlan,
                    "machine-to-plan",
                    it,
                ) { expanded = false }
            }
            onToday?.let {
                MenuItem(
                    strings().repeatToday,
                    "machine-to-today",
                    it,
                ) { expanded = false }
            }
            onReorder?.let {
                MenuItem(strings().reorderSets, "machine-reorder", it) { expanded = false }
            }
            onUnlink?.let {
                MenuItem(strings().unlinkFromFriends, "unlink-machine", it) { expanded = false }
            }
            onDelete?.let {
                MenuItem(strings().deleteMachine, "delete-machine", it) { expanded = false }
            }
        }
    }
}

@Composable
private fun MenuItem(
    text: String,
    tag: String,
    onClick: () -> Unit,
    close: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = {
            close()
            onClick()
        },
        modifier = Modifier.testTag(tag),
    )
}
