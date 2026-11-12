package monster.greyde.kachalochka.ui.network

import kotlinx.coroutines.flow.StateFlow
import monster.greyde.kachalochka.core.data.supabase.ProxyLogin
import monster.greyde.kachalochka.ui.settings.DeviceKey
import monster.greyde.kachalochka.ui.settings.DeviceSetting
import monster.greyde.kachalochka.ui.settings.DeviceStore

/** The HTTP proxy for all requests of the app: [address] is typed as "host:port". */
data class ProxySetting(
    val address: String = "",
    val used: Boolean = false,
    val login: String = "",
    val password: String = "",
) {
    /** Null while the proxy is not used or [login] is blank. */
    val proxyLogin: ProxyLogin?
        get() = ProxyLogin(login, password).takeIf { used && login.isNotBlank() }

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
            store.read(LOGIN).orEmpty(),
            store.read(PASSWORD).orEmpty(),
        ),
    ) {
    val setting: StateFlow<ProxySetting> = state

    override suspend fun save(value: ProxySetting) {
        store.write(ADDRESS, value.address)
        store.write(USED, value.used)
        store.write(LOGIN, value.login)
        store.write(PASSWORD, value.password)
    }

    private companion object {
        val ADDRESS = DeviceKey.Text("proxy_address")
        val USED = DeviceKey.Flag("proxy_used")
        val LOGIN = DeviceKey.Text("proxy_login")
        val PASSWORD = DeviceKey.Text("proxy_password")
    }
}
