package monster.greyde.kachalochka.ui.visit

import kotlinx.coroutines.flow.StateFlow
import monster.greyde.kachalochka.ui.settings.DeviceKey
import monster.greyde.kachalochka.ui.settings.DeviceSetting
import monster.greyde.kachalochka.ui.settings.DeviceStore

/** Whether the set form has a comment field, a device setting. */
class SetCommentPreference(
    private val store: DeviceStore,
) : DeviceSetting<Boolean>(store.read(KEY) ?: false) {
    val shown: StateFlow<Boolean> = state

    override suspend fun save(value: Boolean) = store.write(KEY, value)

    private companion object {
        val KEY = DeviceKey.Flag("set_comment_shown")
    }
}

/** Whether the set form lists every account; kept in memory until the app restarts. */
class ShowEveryone {
    var checked = false
}
