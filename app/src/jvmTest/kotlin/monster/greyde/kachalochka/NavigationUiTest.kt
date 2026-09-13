package monster.greyde.kachalochka

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * Runs [assertions] against a Compose UI test showing [content] in a host that may drive
 * navigation.
 *
 * Navigation raises a back stack entry only as far as the host lifecycle allows, so the host must
 * be RESUMED for entries to reach a state the closing scene can destroy them from. Lifecycle
 * resolves "the main thread" from `Dispatchers.Main`; an unconfined one lets its registries be
 * driven from whichever thread the test harness uses. That costs the guard: lifecycle no longer
 * rejects a main-thread violation in the code under test, so these tests cannot catch one.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
fun runNavigationUiTest(
    content: @Composable () -> Unit,
    assertions: ComposeUiTest.() -> Unit,
) {
    Dispatchers.setMain(Dispatchers.Unconfined)
    try {
        val owner = ResumedLifecycleOwner()
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) { content() }
            }
            assertions()
        }
    } finally {
        Dispatchers.resetMain()
    }
}

private class ResumedLifecycleOwner : LifecycleOwner {
    override val lifecycle: Lifecycle =
        LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
}
