package monster.greyde.kachalochka.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.ui.components.SectionLabel
import monster.greyde.kachalochka.ui.icons.PhosphorIcons

internal val GroupShape = RoundedCornerShape(12.dp)

/** A quiet capital [label] over [content]; [labelTag] reaches the label in tests. */
@Composable
internal fun SettingsGroup(
    label: String,
    labelTag: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(
            label,
            Modifier
                .padding(start = 2.dp)
                .then(if (labelTag != null) Modifier.testTag(labelTag) else Modifier),
        )
        content()
    }
}

/** Rows drawn as one outlined card, as the design groups them. */
@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(GroupShape)
            .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f), GroupShape),
        content = content,
    )
}

@Composable
internal fun RowDivider() {
    Box(
        Modifier
            .padding(start = 46.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)),
    )
}

/**
 * One line of a [SettingsCard] that opens something: [value] is what is chosen now, [subtitle] a
 * second line under [title]. The value and the subtitle answer to [tag] with "-value" and
 * "-counts".
 */
@Composable
internal fun SettingsRow(
    icon: ImageVector,
    title: String,
    tag: String,
    onClick: () -> Unit,
    value: String? = null,
    subtitle: String? = null,
    titleColor: Color = Color.Unspecified,
) {
    val colors = MaterialTheme.colorScheme
    val muted = colors.onBackground.copy(alpha = 0.55f)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (subtitle == null) 54.dp else 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp)
            .testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = colors.tertiary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                Modifier.testTag("$tag-label"),
                fontSize = 16.sp,
                color = if (titleColor == Color.Unspecified) colors.onBackground else titleColor,
            )
            subtitle?.let {
                Text(it, Modifier.testTag("$tag-counts"), fontSize = 13.5.sp, color = muted)
            }
        }
        value?.let {
            Text(
                it,
                Modifier.testTag("$tag-value"),
                fontSize = 15.sp,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            PhosphorIcons.CaretRight,
            null,
            tint = colors.onBackground.copy(alpha = 0.45f),
            modifier = Modifier.size(16.dp),
        )
    }
}

/** The design's bottom panel: a handle, [title] and [content] under it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BottomSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            Modifier.padding(start = 17.dp, end = 17.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                title,
                Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            content()
        }
    }
}

data class SheetOption(
    val label: String,
    val tag: String,
    val note: String? = null,
)

/** A [BottomSheet] of [options]; a choice is passed on and closes it. */
@Composable
internal fun ChoiceSheet(
    title: String,
    options: List<SheetOption>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    extra: @Composable ColumnScope.() -> Unit = {},
) = BottomSheet(title, onDismiss) {
    options.forEachIndexed { index, option ->
        RadioRow(option, index == selected) {
            onSelect(index)
            onDismiss()
        }
    }
    extra()
}

@Composable
private fun RadioRow(
    option: SheetOption,
    on: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(shape)
            .background(if (on) colors.primary.copy(alpha = 0.16f) else Color.Transparent)
            .selectable(selected = on, onClick = onClick)
            .padding(horizontal = 12.dp)
            .testTag(option.tag),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .border(
                    1.5.dp,
                    if (on) colors.primary else colors.onSurface.copy(alpha = 0.35f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (on) Box(Modifier.size(10.dp).clip(CircleShape).background(colors.primary))
        }
        Text(option.label, Modifier.weight(1f), fontSize = 16.sp, color = colors.onSurface)
        option.note?.let {
            Text(it, fontSize = 14.sp, color = colors.onSurface.copy(alpha = 0.55f))
        }
    }
}
