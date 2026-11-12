package monster.greyde.kachalochka.ui.network

import monster.greyde.kachalochka.core.data.supabase.ProxyLogin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProxySettingTest {
    @Test
    fun a_used_address_gives_its_host_and_port() {
        assertEquals("192.0.2.7" to 3128, ProxySetting(" 192.0.2.7:3128 ", true).endpoint)
        assertEquals(
            "pc.example.test" to 8080,
            ProxySetting("pc.example.test:8080", true).endpoint,
        )
    }

    @Test
    fun a_filled_login_goes_with_a_used_proxy() {
        val setting = ProxySetting("192.0.2.7:3128", true, "ivan", "secret")

        assertEquals(ProxyLogin("ivan", "secret"), setting.proxyLogin)
        assertNull(setting.copy(login = " ").proxyLogin)
        assertNull(setting.copy(used = false).proxyLogin)
    }

    @Test
    fun an_unused_address_goes_direct() {
        assertNull(ProxySetting("192.0.2.7:3128", false).endpoint)
    }

    @Test
    fun an_address_without_a_host_and_a_valid_port_goes_direct() {
        listOf("192.0.2.7", "192.0.2.7:", ":3128", "192.0.2.7:port", "192.0.2.7:70000", "")
            .forEach { assertNull(ProxySetting(it, true).endpoint, it) }
    }
}
