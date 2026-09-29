package monster.greyde.kachalochka

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.core.context.stopKoin

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
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { content() }
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
