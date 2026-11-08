package monster.greyde.kachalochka.ui.network

import kotlinx.coroutines.flow.StateFlow

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

interface ProxyPreference {
    val setting: StateFlow<ProxySetting>

    suspend fun set(setting: ProxySetting)
}
