package monster.greyde.kachalochka

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * Host environment a Compose UI test needs before it may drive navigation.
 *
 * Navigation raises a back stack entry only as far as the host lifecycle allows, so the host must
 * be RESUMED for entries to reach a state the closing scene can destroy them from. Lifecycle
 * resolves "the main thread" from `Dispatchers.Main`; an unconfined one lets its registries be
 * driven from whichever thread the test harness uses.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UiTestHost {
    private val owner = ResumedLifecycleOwner()

    fun install() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        owner.resume()
    }

    fun uninstall() {
        Dispatchers.resetMain()
    }

    @Composable
    fun Content(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalLifecycleOwner provides owner, content = content)
    }
}

private class ResumedLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle = registry

    fun resume() {
        registry.currentState = Lifecycle.State.RESUMED
    }
}
