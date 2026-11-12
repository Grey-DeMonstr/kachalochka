package monster.greyde.kachalochka.ui.network

import kotlinx.coroutines.flow.StateFlow
import monster.greyde.kachalochka.ui.settings.DeviceKey
import monster.greyde.kachalochka.ui.settings.DeviceSetting
import monster.greyde.kachalochka.ui.settings.DeviceStore

/** The HTTP proxy for all requests of the app, typed as "host:port". */
data class ProxySetting(
    val address: String = "",
    val used: Boolean = false,
) {
    /** Null while the proxy is not used or [address] is not a host and a port. */
    val endpoint: Pair<String, Int>?
        get() {
            val host = address.substringBeforeLast(':', "").trim()
            val port = address.substringAfterLast(':', "").trim().toIntOrNull()
            if (!used || host.isEmpty() || port == null || port !in 1..65535) return null
            return host to port
        }
}

class ProxyPreference(
    private val store: DeviceStore,
) : DeviceSetting<ProxySetting>(
        ProxySetting(
            store.read(ADDRESS).orEmpty(),
            store.read(USED) ?: false,
        ),
    ) {
    val setting: StateFlow<ProxySetting> = state

    override suspend fun save(value: ProxySetting) {
        store.write(ADDRESS, value.address)
        store.write(USED, value.used)
    }

    private companion object {
        val ADDRESS = DeviceKey.Text("proxy_address")
        val USED = DeviceKey.Flag("proxy_used")
    }
}
