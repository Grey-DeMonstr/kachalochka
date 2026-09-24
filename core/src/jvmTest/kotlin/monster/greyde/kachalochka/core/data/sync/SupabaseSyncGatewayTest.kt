package monster.greyde.kachalochka.core.data.sync

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import monster.greyde.kachalochka.core.data.gym.VisitRow
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

private val OWNER = UserId("11111111-1111-4111-8111-111111111111")

private fun visitRow(
    id: String,
    updatedAt: String,
) = VisitRow(
    id = id,
    userId = OWNER.value,
    recordedAt = updatedAt,
    endedAt = null,
    updatedAt = updatedAt,
    deleted = false,
)

private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

private fun page(rows: List<VisitRow>) = Json.encodeToString(rows)

private fun gatewayOn(
    engine: MockEngine.Queue,
    pageSize: Long,
): SupabaseSyncGateway {
    val client =
        createSupabaseClient("https://example.test", "anon-key") {
            httpEngine = engine
            install(Postgrest)
        }
    return SupabaseSyncGateway(lazyOf(client), pageSize)
}

class SupabaseSyncGatewayTest {
    @Test
    fun a_pull_walks_every_page_and_stops_on_an_empty_one() =
        runTest {
            val v1 = visitRow("11111111-0000-4000-8000-000000000001", "2024-01-01T00:00:00+00:00")
            val v2 = visitRow("11111111-0000-4000-8000-000000000002", "2024-01-01T00:00:01+00:00")
            val v3 = visitRow("11111111-0000-4000-8000-000000000003", "2024-01-01T00:00:02+00:00")
            val engine = MockEngine.Queue()
            engine.enqueue { respond(page(listOf(v1, v2)), HttpStatusCode.OK, jsonHeaders()) }
            engine.enqueue { respond(page(listOf(v3)), HttpStatusCode.OK, jsonHeaders()) }
            engine.enqueue { respond(page(emptyList()), HttpStatusCode.OK, jsonHeaders()) }
            val gateway = gatewayOn(engine, pageSize = 2)

            val pulled = gateway.pullVisits(OWNER, since = null)

            assertEquals(listOf(v1.id, v2.id, v3.id), pulled.map { it.id.value })
            assertEquals(3, engine.requestHistory.size)
        }

    @Test
    fun a_page_shorter_than_the_page_size_does_not_stop_the_pull() =
        runTest {
            val v1 = visitRow("11111111-0000-4000-8000-000000000001", "2024-01-01T00:00:00+00:00")
            val engine = MockEngine.Queue()
            // A single row on a page sized for ten proves the pull can't stop on a short page.
            engine.enqueue { respond(page(listOf(v1)), HttpStatusCode.OK, jsonHeaders()) }
            engine.enqueue { respond(page(emptyList()), HttpStatusCode.OK, jsonHeaders()) }
            val gateway = gatewayOn(engine, pageSize = 10)

            val pulled = gateway.pullVisits(OWNER, since = null)

            assertEquals(listOf(v1.id), pulled.map { it.id.value })
            assertEquals(2, engine.requestHistory.size)
        }

    @Test
    fun the_first_request_carries_the_owner_and_since_filters_and_no_keyset_filter() =
        runTest {
            val engine = MockEngine.Queue()
            engine.enqueue { respond(page(emptyList()), HttpStatusCode.OK, jsonHeaders()) }
            val since = Instant.parse("2023-12-31T00:00:00Z")
            val gateway = gatewayOn(engine, pageSize = 1)

            gateway.pullVisits(OWNER, since)

            val params =
                engine.requestHistory
                    .single()
                    .url.parameters
            assertEquals("eq.${OWNER.value}", params["user_id"])
            assertEquals("gt.$since", params["updated_at"])
            assertNull(params["or"])
        }

    @Test
    fun a_later_page_is_filtered_to_rows_strictly_after_the_previous_page_s_last_row() =
        runTest {
            val v1 = visitRow("11111111-0000-4000-8000-000000000001", "2024-01-01T00:00:00+00:00")
            val v2 = visitRow("11111111-0000-4000-8000-000000000002", "2024-01-01T00:00:01+00:00")
            val engine = MockEngine.Queue()
            engine.enqueue { respond(page(listOf(v1)), HttpStatusCode.OK, jsonHeaders()) }
            engine.enqueue { respond(page(listOf(v2)), HttpStatusCode.OK, jsonHeaders()) }
            engine.enqueue { respond(page(emptyList()), HttpStatusCode.OK, jsonHeaders()) }
            val gateway = gatewayOn(engine, pageSize = 1)

            gateway.pullVisits(OWNER, since = null)

            val second = engine.requestHistory[1].url.parameters
            assertEquals(
                "(updated_at.gt.\"${v1.updatedAt}\",and(updated_at.eq.\"${v1.updatedAt}\"," +
                    "id.gt.${v1.id}))",
                second["or"],
            )
        }
}
