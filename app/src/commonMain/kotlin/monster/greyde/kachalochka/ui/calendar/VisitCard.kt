package monster.greyde.kachalochka.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CompletableDeferred
import monster.greyde.kachalochka.ui.account.PersonAvatar
import monster.greyde.kachalochka.ui.components.ControlShape
import monster.greyde.kachalochka.ui.components.TagChip
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.share.CardSharing
import monster.greyde.kachalochka.ui.strings.strings
import monster.greyde.kachalochka.ui.theme.friendColor
import monster.greyde.kachalochka.ui.theme.gainColor
import org.koin.compose.koinInject

private const val SHOWN_MACHINES = 3
private val PICTURE_WIDTH = 400.dp
private const val PICTURE_DENSITY = 2.7f

/** What the own card's menu does with its visit; an action left out has no item. */
internal class VisitMenuActions(
    val onMove: (() -> Unit)?,
    val onToPlan: () -> Unit,
    val onRepeatToday: (() -> Unit)?,
    val onRemove: (() -> Unit)?,
)

/** A visit as the calendar and the feed draw it; [menu] is given on an own card that is editable. */
@Composable
internal fun VisitCard(
    card: VisitCardUi,
    onOpen: () -> Unit,
    menu: VisitMenuActions? = null,
) {
    val exercises = card.exercises.orEmpty()
    val hidden = exercises.size - SHOWN_MACHINES
    var expanded by rememberSaveable(card.tag) { mutableStateOf(false) }
    val cardSharing: CardSharing = koinInject()
    var sharing by remember { mutableStateOf(false) }
    Box {
        CardFrame(
            card.tag,
            Modifier.clickable(onClick = onOpen),
            bottom = if (hidden > 0) 4.dp else 14.dp,
        ) {
            CardHead(card) {
                val own = card.own
                if (own != null && menu != null) {
                    VisitMenu(
                        own,
                        menu,
                        onShare = { sharing = true }.takeIf { cardSharing.available },
                    )
                }
            }
            CardBody(card, if (expanded) exercises else exercises.take(SHOWN_MACHINES))
            if (hidden > 0) {
                MoreToggle(
                    if (expanded) strings().showLess else strings().moreMachines(hidden),
                    expanded,
                    Modifier.testTag("${card.tag}-more"),
                ) { expanded = !expanded }
            }
        }
        if (sharing) {
            CardPicture(card) {
                cardSharing.share(it)
                sharing = false
            }
        }
    }
}

/** An own card as a shared picture shows it: every machine, and the date in place of the menu. */
@Composable
internal fun SharedVisitCard(
    card: VisitCardUi,
    onAvatarSettled: () -> Unit = {},
) {
    CardFrame(card.tag, Modifier, bottom = 14.dp) {
        CardHead(card, onAvatarSettled) {
            card.own?.let {
                Text(
                    it.date,
                    modifier = Modifier.padding(end = 6.dp).testTag("${card.tag}-date"),
                    fontSize = 13.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }
        }
        CardBody(card, card.exercises.orEmpty())
    }
}

/**
 * Draws [card] off screen at a fixed width and density, so the picture is the same on every
 * phone, and gives that picture to [onDrawn] once the avatar is in it.
 */
@Composable
private fun CardPicture(
    card: VisitCardUi,
    onDrawn: suspend (ImageBitmap) -> Unit,
) {
    val layer = rememberGraphicsLayer()
    var avatarSettled by remember { mutableStateOf(false) }
    val recorded = remember { CompletableDeferred<Unit>() }
    val screen = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(PICTURE_DENSITY, screen.fontScale)) {
        Box(
            Modifier
                .clearAndSetSemantics {}
                .layout { measurable, _ ->
                    val width = PICTURE_WIDTH.roundToPx()
                    val placeable = measurable.measure(Constraints.fixedWidth(width))
                    layout(0, 0) { placeable.place(0, 0) }
                }.drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    if (avatarSettled) recorded.complete(Unit)
                }.background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
        ) {
            SharedVisitCard(card, onAvatarSettled = { avatarSettled = true })
        }
    }
    LaunchedEffect(Unit) {
        recorded.await()
        onDrawn(layer.toImageBitmap())
    }
}

@Composable
private fun CardFrame(
    tag: String,
    modifier: Modifier,
    bottom: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, colors.onBackground.copy(alpha = 0.13f), shape)
            .background(colors.onBackground.copy(alpha = 0.03f))
            .then(modifier)
            .padding(start = 14.dp, top = 12.dp, end = 8.dp, bottom = bottom)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun CardHead(
    card: VisitCardUi,
    onAvatarSettled: () -> Unit = {},
    trailing: @Composable () -> Unit,
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
            onSettled = onAvatarSettled,
        )
        Text(
            card.person.name,
            modifier = Modifier.weight(1f).testTag("${card.tag}-name"),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
        )
        trailing()
    }
}

@Composable
private fun CardBody(
    card: VisitCardUi,
    shown: List<ExerciseUi>,
) {
    if (card.tags.isNotEmpty()) {
        FlowRow(
            Modifier.padding(end = 6.dp).testTag("${card.tag}-tags"),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            card.tags.forEach { TagChip(it, Modifier.testTag("${card.tag}-tag-$it")) }
        }
    }
    if (card.exercises?.isEmpty() == true) {
        Text(
            strings().noSetsYet,
            fontSize = 14.5.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
        )
    }
    if (shown.isNotEmpty()) {
        Column(
            Modifier.padding(end = 6.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            shown.forEachIndexed { i, exercise ->
                ExerciseRow(exercise, "${card.tag}-machine-$i")
            }
        }
    }
}

@Composable
private fun ExerciseRow(
    exercise: ExerciseUi,
    tag: String,
) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.testTag(tag), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
            if (exercise.isNew) {
                Text(
                    strings().newBadge,
                    modifier =
                        Modifier
                            .alignByBaseline()
                            .border(1.dp, gainColor(), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                            .testTag("$tag-new"),
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
    onShare: (() -> Unit)?,
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
            actions.onMove?.let { move ->
                MenuItem(strings().move, PhosphorIcons.CalendarBlank, "move-visit-$id") {
                    open = false
                    move()
                }
            }
            MenuItem(strings().toPlan, PhosphorIcons.ListChecks, "save-as-plan-$id") {
                open = false
                actions.onToPlan()
            }
            onShare?.let { share ->
                MenuItem(strings().share, PhosphorIcons.ShareNetwork, "share-visit-$id") {
                    open = false
                    share()
                }
            }
            actions.onRepeatToday?.takeIf { visit.canRepeatToday }?.let { repeat ->
                MenuItem(strings().repeatToday, PhosphorIcons.ArrowClockwise, "repeat-today-$id") {
                    open = false
                    repeat()
                }
            }
            actions.onRemove?.let { remove ->
                MenuItem(strings().delete, PhosphorIcons.Trash, "remove-visit-$id") {
                    open = false
                    remove()
                }
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
