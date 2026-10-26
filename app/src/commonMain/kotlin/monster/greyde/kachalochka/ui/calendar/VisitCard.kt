package monster.greyde.kachalochka.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.ui.account.PersonAvatar
import monster.greyde.kachalochka.ui.components.ControlShape
import monster.greyde.kachalochka.ui.components.TagChip
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.strings.strings
import monster.greyde.kachalochka.ui.theme.friendColor
import monster.greyde.kachalochka.ui.theme.gainColor

private const val SHOWN_MACHINES = 3

/** What the own card's menu does with its visit. */
internal class VisitMenuActions(
    val onMove: () -> Unit,
    val onToPlan: () -> Unit,
    val onRepeatToday: () -> Unit,
    val onRemove: () -> Unit,
)

/** A visit as the calendar and the feed draw it; [menu] is given on an own card that is editable. */
@Composable
internal fun VisitCard(
    card: VisitCardUi,
    onOpen: () -> Unit,
    menu: VisitMenuActions? = null,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val exercises = card.exercises.orEmpty()
    val hidden = exercises.size - SHOWN_MACHINES
    var expanded by rememberSaveable(card.tag) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, colors.onBackground.copy(alpha = 0.13f), shape)
            .background(colors.onBackground.copy(alpha = 0.03f))
            .clickable(onClick = onOpen)
            .padding(
                start = 14.dp,
                top = 12.dp,
                end = 8.dp,
                bottom =
                    if (hidden >
                        0
                    ) {
                        4.dp
                    } else {
                        14.dp
                    },
            ).testTag(card.tag),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            PersonAvatar(
                card.person.userId,
                card.person.name,
                card.person.avatar,
                size = 36.dp,
                tint = card.color?.let { friendColor(it) },
            )
            Text(
                card.person.name,
                modifier = Modifier.weight(1f).testTag("${card.tag}-name"),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = colors.onBackground,
            )
            val own = card.own
            if (own != null && menu != null) VisitMenu(own, menu)
        }
        if (card.tags.isNotEmpty()) {
            FlowRow(
                Modifier.padding(end = 6.dp).testTag("${card.tag}-tags"),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                card.tags.forEach { TagChip(it, Modifier.testTag("${card.tag}-tag-$it")) }
            }
        }
        if (card.exercises != null && exercises.isEmpty()) {
            Text(
                strings().noSetsYet,
                fontSize = 14.5.sp,
                color = colors.onBackground.copy(alpha = 0.55f),
            )
        }
        if (exercises.isNotEmpty()) {
            Column(
                Modifier.padding(end = 6.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                val shown = if (expanded) exercises else exercises.take(SHOWN_MACHINES)
                shown.forEachIndexed { i, exercise ->
                    ExerciseRow(exercise, Modifier.testTag("${card.tag}-machine-$i"))
                }
            }
        }
        if (hidden > 0) {
            MoreToggle(
                if (expanded) strings().showLess else strings().moreMachines(hidden),
                expanded,
                Modifier.testTag("${card.tag}-more"),
            ) { expanded = !expanded }
        }
    }
}

@Composable
private fun ExerciseRow(
    exercise: ExerciseUi,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            exercise.name,
            modifier = Modifier.weight(1f).alignByBaseline(),
            fontSize = 14.5.sp,
            lineHeight = 19.sp,
            color = colors.onBackground,
        )
        Row(
            Modifier.alignByBaseline(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                exercise.best,
                modifier = Modifier.alignByBaseline(),
                fontSize = 14.sp,
                maxLines = 1,
                color = colors.onBackground.copy(alpha = 0.72f),
            )
            exercise.growth?.let {
                Text(
                    it,
                    modifier = Modifier.alignByBaseline(),
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    color = gainColor(),
                )
            }
        }
    }
}

@Composable
private fun MoreToggle(
    label: String,
    expanded: Boolean,
    modifier: Modifier,
    onToggle: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .offset(x = (-6).dp)
            .height(36.dp)
            .clip(ControlShape)
            .clickable(onClick = onToggle)
            .padding(start = 6.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            if (expanded) PhosphorIcons.CaretUp else PhosphorIcons.CaretDown,
            null,
            tint = colors.secondary,
            modifier = Modifier.size(16.dp),
        )
        Text(label, fontSize = 13.5.sp, color = colors.secondary)
    }
}

@Composable
private fun VisitMenu(
    visit: OwnVisitUi,
    actions: VisitMenuActions,
) {
    var open by remember { mutableStateOf(false) }
    val id = visit.id.value
    Box {
        Box(
            Modifier
                .size(40.dp)
                .clip(ControlShape)
                .clickable { open = true }
                .testTag("visit-menu-$id"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                PhosphorIcons.DotsThreeVertical,
                strings().more,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuItem(strings().move, PhosphorIcons.CalendarBlank, "move-visit-$id") {
                open = false
                actions.onMove()
            }
            MenuItem(strings().toPlan, PhosphorIcons.ListChecks, "save-as-plan-$id") {
                open = false
                actions.onToPlan()
            }
            if (visit.canRepeatToday) {
                MenuItem(strings().repeatToday, PhosphorIcons.ArrowClockwise, "repeat-today-$id") {
                    open = false
                    actions.onRepeatToday()
                }
            }
            MenuItem(strings().delete, PhosphorIcons.Trash, "remove-visit-$id") {
                open = false
                actions.onRemove()
            }
        }
    }
}

@Composable
private fun MenuItem(
    text: String,
    icon: ImageVector,
    tag: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(icon, null, Modifier.size(20.dp)) },
        onClick = onClick,
        modifier = Modifier.testTag(tag),
    )
}
