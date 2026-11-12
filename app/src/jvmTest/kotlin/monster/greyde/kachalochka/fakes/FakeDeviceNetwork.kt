package monster.greyde.kachalochka.fakes

import monster.greyde.kachalochka.ui.network.DeviceNetwork

class FakeDeviceNetwork(
    private val facts: List<Pair<String, String>> = listOf("Network" to "wifi, validated"),
    private val addresses: List<String> = listOf("192.0.2.1"),
) : DeviceNetwork {
    override fun facts() = facts

    override suspend fun addressesOf(host: String) = addresses
}
