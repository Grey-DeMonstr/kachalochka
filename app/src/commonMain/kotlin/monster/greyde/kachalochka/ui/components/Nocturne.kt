package monster.greyde.kachalochka.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp

const val DISABLED_ALPHA = 0.45f

internal val ControlShape = RoundedCornerShape(8.dp)

@Composable
fun SquareIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier =
            modifier
                .size(50.dp)
                .clip(ControlShape)
                .border(1.dp, colors.onBackground.copy(alpha = 0.18f), ControlShape)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription,
            tint = colors.onBackground.copy(alpha = 0.65f),
            modifier = Modifier.size(22.dp),
        )
    }
}

/** [SquareIconButton] that stays lit while [checked]. */
@Composable
fun SquareToggleButton(
    icon: ImageVector,
    contentDescription: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier =
            modifier
                .size(50.dp)
                .clip(ControlShape)
                .background(if (checked) colors.primary.copy(alpha = 0.14f) else Color.Transparent)
                .border(
                    1.dp,
                    if (checked) colors.primary else colors.onBackground.copy(alpha = 0.18f),
                    ControlShape,
                ).toggleable(value = checked, onValueChange = onCheckedChange),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription,
            tint = if (checked) colors.tertiary else colors.onBackground.copy(alpha = 0.65f),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun AccentButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                // Busy is not unavailable, so a loading button keeps its full colour.
                .alpha(if (enabled || loading) 1f else DISABLED_ALPHA)
                .clip(shape)
                .border(1.dp, colors.primary, shape)
                .background(colors.primary.copy(alpha = 0.12f))
                .clickable(enabled = enabled && !loading, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                Modifier.size(22.dp),
                color = colors.onPrimaryContainer,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(icon, null, tint = colors.onPrimaryContainer, modifier = Modifier.size(22.dp))
        }
        Text(
            text,
            fontSize = if (height > 64.dp) 20.sp else 19.sp,
            fontWeight = FontWeight.Medium,
            color = colors.onPrimaryContainer,
        )
    }
}

@Composable
fun OutlineButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier =
            modifier
                .height(56.dp)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clip(ControlShape)
                .border(1.dp, colors.onBackground.copy(alpha = 0.18f), ControlShape)
                .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = colors.onBackground, modifier = Modifier.size(20.dp))
        Text(text, fontSize = 15.sp, color = colors.onBackground)
    }
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text.uppercase(),
        modifier = modifier,
        fontSize = 11.sp,
        letterSpacing = 0.09.em,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
    )
}

@Composable
fun Rule() {
    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f))
}

data class Choice(
    val label: String,
    val tag: String,
    val enabled: Boolean = true,
    val weight: Float = 1f,
)

@Composable
fun ChoiceRow(
    choices: List<Choice>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEachIndexed { index, choice ->
            val on = index == selected
            Box(
                modifier =
                    Modifier
                        .weight(choice.weight)
                        .height(50.dp)
                        .alpha(if (choice.enabled) 1f else DISABLED_ALPHA)
                        .clip(ControlShape)
                        .border(
                            1.dp,
                            if (on) colors.primary else colors.onBackground.copy(alpha = 0.16f),
                            ControlShape,
                        ).then(
                            if (on) {
                                Modifier.background(
                                    colors.primary.copy(alpha = 0.14f),
                                )
                            } else {
                                Modifier
                            },
                        ).selectable(selected = on, enabled = choice.enabled) { onSelect(index) }
                        .testTag(choice.tag),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    choice.label,
                    fontSize = 15.sp,
                    color = if (on) colors.onPrimaryContainer else colors.onBackground,
                )
            }
        }
    }
}

/** A dashed outline of rounded [radius], marking something offered rather than chosen. */
fun Modifier.dashedBorder(
    color: Color,
    radius: Dp,
): Modifier =
    drawBehind {
        val stroke =
            Stroke(
                width = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
        drawRoundRect(color = color, cornerRadius = CornerRadius(radius.toPx()), style = stroke)
    }

/** A pill switched on and off, as a tag chosen to filter by. */
@Composable
fun ChoiceChip(
    label: String,
    chosen: Boolean,
    tag: String,
    onToggle: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .height(38.dp)
            .clip(shape)
            .background(if (chosen) colors.primary.copy(alpha = 0.16f) else Color.Transparent)
            .border(
                1.dp,
                if (chosen) colors.primary else colors.onBackground.copy(alpha = 0.18f),
                shape,
            ).toggleable(value = chosen, onValueChange = { onToggle() })
            .padding(horizontal = 14.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 15.sp,
            color = if (chosen) colors.onPrimaryContainer else colors.onBackground,
        )
    }
}

/** [options] as one scrolling row of [ChoiceChip]s, [edge] the room before the first and last. */
@Composable
fun <T> ChipRow(
    options: List<Pair<T, String>>,
    chosen: T,
    onChoose: (T) -> Unit,
    modifier: Modifier = Modifier,
    edge: Dp = 0.dp,
    tag: (T) -> String,
) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = edge),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (option, label) ->
            ChoiceChip(label, option == chosen, tag(option)) { onChoose(option) }
        }
    }
}

/** Keeps the cursor after the last digit inside the field. */
private val CursorRoom = 4.dp

/**
 * A typed value between − and +, with [prefix] and [suffix] beside it, such as a gravitron's "−"
 * and the unit. [note] is a second value under it, as strong as the design asks; [caption] a
 * quieter line.
 */
@Composable
fun Stepper(
    value: String,
    caption: String?,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    tag: String,
    onValueChange: (String) -> Unit,
    accent: Boolean = false,
    prefix: String = "",
    suffix: String = "",
    note: String? = null,
    keyboardType: KeyboardType = KeyboardType.Decimal,
) {
    val colors = MaterialTheme.colorScheme
    val valueColor = if (accent) colors.onPrimaryContainer else colors.onBackground
    val muted = colors.onBackground.copy(alpha = 0.55f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StepButton("−", onMinus, Modifier.testTag("$tag-minus"))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                if (prefix.isNotEmpty()) {
                    Text(
                        prefix,
                        modifier = Modifier.testTag("$tag-prefix"),
                        fontSize = 44.sp,
                        lineHeight = 52.sp,
                        color = valueColor,
                    )
                }
                val style =
                    TextStyle(
                        fontSize = 44.sp,
                        lineHeight = 52.sp,
                        fontWeight = FontWeight.Medium,
                        color = valueColor,
                        textAlign = TextAlign.Center,
                    )
                // Sized to its text: Android keeps a field's intrinsic width from an earlier value,
                // which scrolls a longer one out of sight.
                val measurer = rememberTextMeasurer()
                val textWidth =
                    with(LocalDensity.current) {
                        measurer
                            .measure(value, style, maxLines = 1)
                            .size.width
                            .toDp()
                    }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = style,
                    cursorBrush = SolidColor(colors.secondary),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    modifier =
                        Modifier
                            .width(max(textWidth + CursorRoom, 28.dp))
                            .testTag("$tag-value"),
                )
                if (suffix.isNotEmpty()) {
                    Text(
                        suffix,
                        modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
                        fontSize = 17.sp,
                        color = muted,
                    )
                }
            }
            note?.let {
                Text(
                    it,
                    modifier = Modifier.testTag("$tag-note"),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onBackground,
                )
            }
            caption?.let { Text(it, fontSize = 13.sp, color = muted) }
        }
        StepButton("+", onPlus, Modifier.testTag("$tag-plus"))
    }
}

@Composable
private fun StepButton(
    symbol: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier =
            modifier
                .size(64.dp)
                .clip(shape)
                .border(1.dp, colors.onBackground.copy(alpha = 0.20f), shape)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontSize = 34.sp, color = colors.onBackground)
    }
}

@Composable
fun Thumbnail(
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier =
            modifier
                .size(56.dp)
                .clip(ControlShape)
                .background(colors.surfaceContainerHighest)
                .border(1.dp, colors.onBackground.copy(alpha = 0.10f), ControlShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            null,
            tint = colors.onBackground.copy(alpha = 0.45f),
            modifier = Modifier.size(24.dp),
        )
    }
}
