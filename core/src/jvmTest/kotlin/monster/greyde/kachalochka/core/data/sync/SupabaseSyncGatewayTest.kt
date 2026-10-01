package monster.greyde.kachalochka.core.data.sync

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import monster.greyde.kachalochka.core.data.gym.VisitRow
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkId
import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.measures.Measure
import monster.greyde.kachalochka.core.domain.measures.MeasureId
import monster.greyde.kachalochka.core.domain.measures.MeasureKind
import monster.greyde.kachalochka.core.domain.measures.Measurement
import monster.greyde.kachalochka.core.domain.measures.MeasurementId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val OWNER = UserId("11111111-1111-4111-8111-111111111111")

private fun visitRow(
    id: String,
    updatedAt: String,
) = VisitRow(
    id = id,
    userId = OWNER.value,
    day = null,
    recordedAt = updatedAt,
    endedAt = null,
    updatedAt = updatedAt,
    deleted = false,
    planned = "[]",
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

    @Test
    fun a_pulled_machine_keeps_its_unit_name_and_ignores_columns_it_does_not_know() =
        runTest {
            val row =
                """[{"id":"33333333-3333-4333-8333-333333333333","user_id":"${OWNER.value}",""" +
                    """"name":"Гравитрон","setup_note":"","weight_mode":"hydraulic",""" +
                    """"platform_weight":0.0,"platform_included":false,"unit":"custom",""" +
                    """"unit_label":"плитка","weight_step":1.0,""" +
                    """"updated_at":"2024-01-01T00:00:00+00:00","deleted":false,""" +
                    """"link_id":null,"photo_id":null,"tags":"[]","cover_photo":null}]"""
            val engine = MockEngine.Queue()
            engine.enqueue { respond(row, HttpStatusCode.OK, jsonHeaders()) }
            engine.enqueue { respond("[]", HttpStatusCode.OK, jsonHeaders()) }

            val pulled = gatewayOn(engine, pageSize = 10).pullMachines(OWNER, since = null)

            val machine = pulled.single()

            assertEquals(WeightMode.Total, machine.weightMode)
            assertEquals(WeightUnit.Custom to "плитка", machine.unit to machine.unitLabel)
        }

    @Test
    fun a_pushed_machine_always_sends_its_unit_name() =
        runTest {
            val engine = MockEngine.Queue()
            engine.enqueue { respond("", HttpStatusCode.Created, jsonHeaders()) }
            val press = Machine.new("Жим ногами", OWNER, Instant.parse("2024-01-01T00:00:00Z"))

            gatewayOn(engine, pageSize = 10).pushMachine(press)

            val body =
                engine.requestHistory
                    .single()
                    .body
                    .toByteArray()
                    .decodeToString()
            assertTrue("\"unit_label\":\"\"" in body, body)
        }

    @Test
    fun a_pushed_machine_leaves_the_legacy_link_key_to_the_server() =
        runTest {
            val engine = MockEngine.Queue()
            engine.enqueue { respond("", HttpStatusCode.Created, jsonHeaders()) }
            val press = Machine.new("Жим ногами", OWNER, Instant.parse("2024-01-01T00:00:00Z"))

            gatewayOn(engine, pageSize = 10).pushMachine(press)

            val body =
                engine.requestHistory
                    .single()
                    .body
                    .toByteArray()
                    .decodeToString()
            assertFalse("link_id" in body, body)
        }

    @Test
    fun a_link_is_pushed_to_and_pulled_from_its_own_table() =
        runTest {
            val link =
                MachineLink(
                    MachineLinkId("55555555-5555-4555-8555-555555555555"),
                    OWNER,
                    MachineId("33333333-3333-4333-8333-333333333333"),
                    MachineId("44444444-4444-4444-8444-444444444444"),
                    Instant.parse("2024-01-01T00:00:00Z"),
                    false,
                )
            val row =
                """[{"id":"${link.id.value}","user_id":"${OWNER.value}",""" +
                    """"machine_id":"${link.machineId.value}",""" +
                    """"linked_machine_id":"${link.linkedMachineId.value}",""" +
                    """"updated_at":"2024-01-01T00:00:00+00:00","deleted":false}]"""
            val engine = MockEngine.Queue()
            engine.enqueue { respond("", HttpStatusCode.Created, jsonHeaders()) }
            engine.enqueue { respond(row, HttpStatusCode.OK, jsonHeaders()) }
            engine.enqueue { respond("[]", HttpStatusCode.OK, jsonHeaders()) }
            val gateway = gatewayOn(engine, pageSize = 10)

            gateway.pushMachineLink(link)
            val pulled = gateway.pullMachineLinks(OWNER, since = null)

            assertTrue(
                engine.requestHistory.all { it.url.encodedPath.endsWith("/machine_link") },
            )
            assertEquals(listOf(link), pulled)
        }

    @Test
    fun a_measure_and_its_value_are_pushed_to_and_pulled_from_their_own_tables() =
        runTest {
            val neck =
                Measure(
                    MeasureId("55555555-5555-4555-8555-555555555555"),
                    OWNER,
                    "Шея",
                    "см",
                    MeasureKind.Neck,
                    6,
                    Instant.parse("2024-01-01T00:00:00Z"),
                    false,
                )
            val monday =
                Measurement(
                    MeasurementId("66666666-6666-4666-8666-666666666666"),
                    OWNER,
                    neck.id,
                    CalendarDay(2024, 1, 1),
                    38.5,
                    Instant.parse("2024-01-01T00:00:00Z"),
                    false,
                )
            val measureRow =
                """[{"id":"${neck.id.value}","user_id":"${OWNER.value}","name":"Шея",""" +
                    """"unit":"см","kind":"neck","position":6,""" +
                    """"updated_at":"2024-01-01T00:00:00+00:00","deleted":false}]"""
            val measurementRow =
                """[{"id":"${monday.id.value}","user_id":"${OWNER.value}",""" +
                    """"measure_id":"${neck.id.value}","day":"2024-01-01","value":38.5,""" +
                    """"updated_at":"2024-01-01T00:00:00+00:00","deleted":false}]"""
            val engine = MockEngine.Queue()
            engine.enqueue { respond("", HttpStatusCode.Created, jsonHeaders()) }
            engine.enqueue { respond("", HttpStatusCode.Created, jsonHeaders()) }
            engine.enqueue { respond(measureRow, HttpStatusCode.OK, jsonHeaders()) }
            engine.enqueue { respond("[]", HttpStatusCode.OK, jsonHeaders()) }
            engine.enqueue { respond(measurementRow, HttpStatusCode.OK, jsonHeaders()) }
            engine.enqueue { respond("[]", HttpStatusCode.OK, jsonHeaders()) }
            val gateway = gatewayOn(engine, pageSize = 10)

            gateway.pushMeasure(neck)
            gateway.pushMeasurement(monday)
            val measures = gateway.pullMeasures(OWNER, since = null)
            val measurements = gateway.pullMeasurements(OWNER, since = null)

            assertEquals(
                listOf(
                    "measure",
                    "measurement",
                    "measure",
                    "measure",
                    "measurement",
                    "measurement",
                ),
                engine.requestHistory.map { it.url.encodedPath.substringAfterLast('/') },
            )
            assertEquals(listOf(neck), measures)
            assertEquals(listOf(monday), measurements)
        }

    @Test
    fun a_pushed_visit_carries_its_day_and_its_end_and_a_pushed_set_its_position() =
        runTest {
            val engine = MockEngine.Queue()
            repeat(2) { engine.enqueue { respond("", HttpStatusCode.Created, jsonHeaders()) } }
            val visit = ownedVisit(OWNER).copy(day = CalendarDay(2023, 11, 14))
            val gateway = gatewayOn(engine, pageSize = 10)

            gateway.pushVisit(visit)
            gateway.pushSet(ownedSet(OWNER, visit, ownedPress(OWNER)))

            val (visitBody, setBody) =
                engine.requestHistory.map { it.body.toByteArray().decodeToString() }
            assertTrue("\"day\":\"2023-11-14\"" in visitBody, visitBody)
            assertTrue("\"ended_at\":\"2023-11-14T22:13:20Z\"" in visitBody, visitBody)
            assertTrue("\"position\":0" in setBody, setBody)
        }

    @Test
    fun a_visit_an_old_client_pushed_arrives_without_a_day() =
        runTest {
            val old = visitRow("11111111-0000-4000-8000-000000000001", "2024-01-01T00:00:00+00:00")
            val engine = MockEngine.Queue()
            engine.enqueue { respond(page(listOf(old)), HttpStatusCode.OK, jsonHeaders()) }
            engine.enqueue { respond(page(emptyList()), HttpStatusCode.OK, jsonHeaders()) }

            val pulled = gatewayOn(engine, pageSize = 10).pullVisits(OWNER, since = null)

            assertNull(pulled.single().day)
        }

    @Test
    fun a_pulled_set_keeps_its_position() =
        runTest {
            val row =
                """[{"id":"44444444-4444-4444-8444-444444444444",""" +
                    """"user_id":"${OWNER.value}",""" +
                    """"visit_id":"11111111-0000-4000-8000-000000000001",""" +
                    """"machine_id":"33333333-3333-4333-8333-333333333333",""" +
                    """"weight":70.0,"reps":10,"position":3,""" +
                    """"recorded_at":"2024-01-01T00:00:00+00:00",""" +
                    """"updated_at":"2024-01-01T00:00:00+00:00","deleted":false,""" +
                    """"comment":""}]"""
            val engine = MockEngine.Queue()
            engine.enqueue { respond(row, HttpStatusCode.OK, jsonHeaders()) }
            engine.enqueue { respond("[]", HttpStatusCode.OK, jsonHeaders()) }

            val pulled = gatewayOn(engine, pageSize = 10).pullSets(OWNER, since = null)

            assertEquals(3, pulled.single().position)
        }
}
