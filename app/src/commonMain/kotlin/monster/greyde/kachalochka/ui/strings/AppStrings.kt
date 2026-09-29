package monster.greyde.kachalochka.ui.strings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The strings of the language the app speaks now. The language is process-wide, like the system
 * locale, so view models and formatters read it here rather than having it passed in.
 */
object AppStrings {
    private val state = MutableStateFlow<Strings>(RuStrings)
    val flow: StateFlow<Strings> = state
    val current: Strings get() = state.value

    fun set(strings: Strings) {
        state.value = strings
    }
}

/** The current strings, recomposing the caller when the language changes. */
@Composable
fun strings(): Strings = AppStrings.flow.collectAsState().value
