package monster.greyde.kachalochka

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** Shows [content] under the main dispatcher a `NavHost` needs; see technical spec §7. */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
fun runNavigationUiTest(
    content: @Composable () -> Unit,
    assertions: ComposeUiTest.() -> Unit,
) {
    Dispatchers.setMain(Dispatchers.Unconfined)
    try {
        runComposeUiTest {
            setContent(content)
            assertions()
        }
    } finally {
        Dispatchers.resetMain()
    }
}
