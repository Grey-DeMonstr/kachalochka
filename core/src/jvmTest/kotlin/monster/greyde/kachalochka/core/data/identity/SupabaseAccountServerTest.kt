package monster.greyde.kachalochka.core.data.identity

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals

class SupabaseAccountServerTest {
    private val ivan = UserId("11111111-1111-4111-8111-111111111111")

    private fun item(name: String) =
        """{"name":"$name","id":"${ivan.value}","updated_at":"2026-09-29T00:00:00Z",""" +
            """"created_at":"2026-09-29T00:00:00Z","last_accessed_at":"2026-09-29T00:00:00Z",""" +
            """"metadata":{}}"""

    @Test
    fun the_account_goes_first_and_its_photos_after_it() =
        runTest {
            val calls = mutableListOf<String>()
            var listed = 0
            val engine =
                MockEngine { request ->
                    val path = request.url.encodedPath
                    calls += "${request.method.value} ${path.substringAfter("/v1/")}"
                    val body =
                        when {
                            path.endsWith("/object/list/photos") ->
                                if (listed++ == 0) "[${item("a")}]" else "[]"
                            else -> "[]"
                        }
                    respond(
                        body,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client =
                createSupabaseClient("https://example.test", "anon-key") {
                    httpEngine = engine
                    install(Postgrest)
                    install(Storage)
                }

            SupabaseAccountServer { client }.deleteEverything(ivan)

            assertEquals(
                listOf(
                    "POST rpc/delete_my_account",
                    "POST object/list/photos",
                    "DELETE object/photos",
                    "POST object/list/photos",
                ),
                calls,
            )
        }
}
