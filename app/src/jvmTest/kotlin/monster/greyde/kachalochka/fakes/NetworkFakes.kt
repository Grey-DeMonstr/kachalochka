package monster.greyde.kachalochka.fakes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import monster.greyde.kachalochka.ui.network.DeviceNetwork
import monster.greyde.kachalochka.ui.network.ProxyPreference
import monster.greyde.kachalochka.ui.network.ProxySetting

class FakeDeviceNetwork(
    private val facts: List<Pair<String, String>> = listOf("Network" to "wifi, validated"),
    private val addresses: List<String> = listOf("192.0.2.1"),
) : DeviceNetwork {
    override fun facts() = facts

    override suspend fun addressesOf(host: String) = addresses
}

class InMemoryProxyPreference : ProxyPreference {
    private val state = MutableStateFlow(ProxySetting())

    override val setting: StateFlow<ProxySetting> = state

    override suspend fun set(setting: ProxySetting) {
        state.value = setting
    }
}
