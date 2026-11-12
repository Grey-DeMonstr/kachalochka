package monster.greyde.kachalochka.ui.settings

import kotlinx.browser.localStorage

/**
 * A browser that refuses site storage throws on every access. A read then gives null and a write
 * does nothing, so a choice lives as long as the tab.
 */
class LocalStorageDeviceStore : DeviceStore {
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> read(key: DeviceKey<T>): T? =
        runCatching {
            localStorage.getItem(key.name)?.let { text ->
                when (key) {
                    is DeviceKey.Text -> text
                    is DeviceKey.Number -> text.toIntOrNull()
                    is DeviceKey.Flag -> text == "true"
                    is DeviceKey.TextSet -> text.split(' ').filter { it.isNotEmpty() }.toSet()
                } as T?
            }
        }.getOrNull()

    override suspend fun <T : Any> write(
        key: DeviceKey<T>,
        value: T?,
    ) {
        runCatching {
            when (value) {
                null -> localStorage.removeItem(key.name)
                is Set<*> -> localStorage.setItem(key.name, value.joinToString(" "))
                else -> localStorage.setItem(key.name, value.toString())
            }
        }
    }
}
