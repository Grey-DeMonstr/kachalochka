package monster.greyde.kachalochka

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.savedstate.SavedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.core.context.stopKoin
import java.io.Serializable

/**
 * Refuses what an Android Bundle cannot hold, as Android does. A lazy list or pager keeps each
 * item's state under its key, so a key of another type crashes the app on Android only.
 */
private val bundleLikeRegistry = SaveableStateRegistry(null, ::fitsBundle)

private fun fitsBundle(value: Any?): Boolean =
    when (value) {
        null, is SavedState -> true
        is MutableState<*> -> fitsBundle(value.value)
        is Function<*> -> false
        else -> value is Serializable
    }

/** Shows [content] under the main dispatcher a `NavHost` needs; see technical spec §7. */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
fun runNavigationUiTest(
    content: @Composable () -> Unit,
    assertions: ComposeUiTest.() -> Unit,
) {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    // Cleared at the end, so no view model outlives its test and reacts to the next one.
    val owner =
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    try {
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(
                    LocalViewModelStoreOwner provides owner,
                    LocalSaveableStateRegistry provides bundleLikeRegistry,
                ) { content() }
            }
            assertions()
        }
    } finally {
        owner.viewModelStore.clear()
        Dispatchers.resetMain()
        // Koin's compose loader never stops the global context it starts.
        stopKoin()
    }
}
