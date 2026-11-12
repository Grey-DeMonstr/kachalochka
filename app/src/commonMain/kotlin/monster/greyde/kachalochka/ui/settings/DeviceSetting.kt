package monster.greyde.kachalochka.ui.settings

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A setting kept in the [DeviceStore]. It starts from the stored value, a StateFlow, so the first
 * frame is painted from it instead of a default that changes a moment later.
 */
abstract class DeviceSetting<T : Any>(
    initial: T,
) {
    protected val state = MutableStateFlow(initial)

    protected abstract suspend fun save(value: T)

    /** Stores [value] first, so the flow never shows a value that is not saved. */
    suspend fun set(value: T) {
        save(value)
        state.value = value
    }
}
