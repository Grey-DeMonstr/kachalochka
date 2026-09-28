package monster.greyde.kachalochka.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

const val DEFAULT_TRANSITION_MILLIS = 150
const val MAX_TRANSITION_MILLIS = 1000

fun screenEnter(millis: Int): EnterTransition =
    if (millis == 0) EnterTransition.None else fadeIn(tween(millis))

fun screenExit(millis: Int): ExitTransition =
    if (millis == 0) ExitTransition.None else fadeOut(tween(millis))

/** An empty field is 0, so clearing it to type a new length turns animation off meanwhile. */
fun transitionMillisOrNull(text: String): Int? {
    if (text.isEmpty()) return 0
    if (!text.all { it in '0'..'9' }) return null
    return text.toIntOrNull()?.takeIf { it <= MAX_TRANSITION_MILLIS }
}
