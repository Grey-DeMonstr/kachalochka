package monster.greyde.kachalochka.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import monster.greyde.kachalochka.ui.icons.PhosphorIcons

/** Where the item dragged from [from] lands: the item its centre is over, clamped to the list. */
fun dropIndex(
    heights: List<Int>,
    from: Int,
    dragY: Float,
): Int {
    val tops = heights.runningFold(0) { top, h -> top + h }
    val centre = tops[from] + heights[from] / 2f + dragY
    val over = tops.dropLast(1).indexOfLast { it <= centre }
    return over.coerceIn(0, heights.lastIndex)
}

/** Heights are kept by index, so the list must compose its items by position, without keys. */
@Stable
class ReorderState {
    internal val heights = mutableStateMapOf<Int, Int>()
    var dragging: Int? by mutableStateOf(null)
        internal set
    internal var dragY: Float by mutableFloatStateOf(0f)

    private fun ordered(): List<Int> = (0 until heights.size).map { heights[it] ?: 0 }

    /** Drops heights of items that no longer exist, so a shorter list never drops past its end. */
    fun retain(count: Int) {
        heights.keys.retainAll { it < count }
    }

    internal fun target(): Int? {
        val from = dragging ?: return null
        val measured = ordered()
        if (from !in measured.indices) return null
        return dropIndex(measured, from, dragY)
    }

    /** How far a resting item slides to open the gap the dragged one will fill. */
    internal fun shift(index: Int): Float {
        val from = dragging ?: return 0f
        val to = target() ?: return 0f
        val gap = (heights[from] ?: 0).toFloat()
        return when {
            index == from -> dragY
            index in (from + 1)..to -> -gap
            index in to until from -> gap
            else -> 0f
        }
    }
}

@Composable
fun rememberReorderState(): ReorderState = remember { ReorderState() }

/** The dragged item floats above the rest; the item itself draws its outline. */
fun Modifier.reorderItem(
    state: ReorderState,
    index: Int,
): Modifier =
    onSizeChanged { state.heights[index] = it.height }
        .zIndex(if (state.dragging == index) 1f else 0f)
        .graphicsLayer { translationY = state.shift(index) }

@Composable
fun DragHandle(
    state: ReorderState,
    index: Int,
    tag: String,
    onDrop: (from: Int, to: Int) -> Unit,
) {
    // The gesture outlives recompositions, so it must drop into the list as it is now.
    val drop by rememberUpdatedState(onDrop)
    Box(
        Modifier
            .size(40.dp)
            .pointerInput(state, index) {
                detectDragGestures(
                    onDragStart = {
                        state.dragging = index
                        state.dragY = 0f
                    },
                    onDragEnd = {
                        val to = state.target()
                        state.dragging = null
                        state.dragY = 0f
                        if (to != null && to != index) drop(index, to)
                    },
                    onDragCancel = {
                        state.dragging = null
                        state.dragY = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        state.dragY += amount.y
                    },
                )
            }.testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            PhosphorIcons.DotsSixVertical,
            "Перетащить",
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
        )
    }
}
