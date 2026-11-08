package monster.greyde.kachalochka.ui.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.supabase.SupabaseCredentials
import monster.greyde.kachalochka.fakes.FakeDeviceNetwork
import java.net.ConnectException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkChecksTest {
    private val credentials = SupabaseCredentials("https://example.test", "anon-key")

    private fun checks(engine: MockEngine) =
        NetworkChecks(
            HttpClient(engine),
            FakeDeviceNetwork(addresses = listOf("192.0.2.1", "192.0.2.2")),
            credentials,
        )

    @Test
    fun the_checks_find_the_server_then_ask_its_services_then_the_internet() {
        val names = checks(MockEngine { respond("") }).all.map { it.name }

        assertEquals(
            listOf(
                "DNS example.test",
                "Supabase auth",
                "Supabase database",
                "Supabase storage",
                "ya.ru",
                "google.com",
            ),
            names,
        )
    }

    @Test
    fun the_dns_check_lists_the_addresses_the_device_finds() =
        runTest {
            val result = checks(MockEngine { respond("") }).all.first().result()

            assertEquals(CheckResult(true, result.millis, "192.0.2.1, 192.0.2.2"), result)
        }

    @Test
    fun the_server_checks_send_the_anon_key_and_the_internet_checks_do_not() =
        runTest {
            val engine = MockEngine { respond("") }

            checks(engine).all.drop(1).forEach { it.result() }

            val sent = engine.requestHistory.map { it.url.toString() to it.headers["apikey"] }
            assertEquals(
                listOf(
                    "https://example.test/auth/v1/health" to "anon-key",
                    "https://example.test/rest/v1/profile?select=id&limit=1" to "anon-key",
                    "https://example.test/storage/v1/bucket" to "anon-key",
                    "https://ya.ru" to null,
                    "https://www.google.com/generate_204" to null,
                ),
                sent,
            )
        }

    @Test
    fun any_answer_passes_and_names_its_status_and_server() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        "",
                        HttpStatusCode.Unauthorized,
                        headersOf(HttpHeaders.Server, "nginx"),
                    )
                }

            val result = checks(engine).all[1].result()

            assertTrue(result.ok)
            assertEquals("HTTP 401, server nginx", result.detail)
        }

    @Test
    fun a_failed_request_names_the_error_and_its_cause() =
        runTest {
            val refused = ConnectException("refused")
            refused.initCause(UnknownHostException("no"))

            val result = checks(MockEngine { throw refused }).all[1].result()

            assertFalse(result.ok)
            assertEquals(
                "java.net.ConnectException: refused / java.net.UnknownHostException: no",
                result.detail,
            )
        }

    @Test
    fun the_report_lists_the_facts_then_each_check() {
        val report =
            networkReport(
                listOf("App" to "1.8.3", "Network" to "wifi"),
                listOf(
                    CheckRow("ya.ru", CheckResult(true, 120, "HTTP 200")),
                    CheckRow("google.com", CheckResult(false, 10_000, "timeout")),
                    CheckRow("Supabase auth"),
                ),
            )

        assertEquals(
            """
            App: 1.8.3
            Network: wifi

            ya.ru: OK, 120 ms, HTTP 200
            google.com: FAILED, 10000 ms, timeout
            Supabase auth: running
            """.trimIndent(),
            report,
        )
    }
}
