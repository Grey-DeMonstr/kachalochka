package monster.greyde.kachalochka.core.data.gym

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.time.Instant

class PostgrestTagRowsTest {
    private val ivan = UserId("11111111-1111-4111-8111-111111111111")
    private val t0 = Instant.parse("2026-10-16T10:00:00Z")
    private val press = Machine.new("Жим ногами", ivan, t0)
    private val machineA = Machine.new("Тяга", ivan, t0)
    private val machineB = Machine.new("Гребля", ivan, t0)

    private fun link(
        machine: Machine,
        name: String,
    ) = MachineTag(
        machineTagId(machine.id, name),
        machine.userId,
        machine.id,
        tagId(machine.userId, name),
        t0,
        false,
    )

    private fun tag(name: String) = Tag(tagId(ivan, name), ivan, name, t0, false)

    private fun HttpRequestData.table() = url.encodedPath.substringAfterLast('/')

    private fun linksJson(links: List<MachineTag>) =
        Json.encodeToString(links.map(MachineTagRow::of))

    private fun machinesJson(machines: List<Machine>) =
        Json.encodeToString(machines.map(MachineRow::of))

    private fun engineAnswering(vararg bodies: Pair<String, String>): MockEngine {
        val byTable = bodies.toMap()
        return MockEngine { request ->
            respond(
                byTable[request.table()] ?: "[]",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
    }

    private fun rowsOn(engine: MockEngine): PostgrestTagRows {
        val client =
            createSupabaseClient("https://example.test", "anon-key") {
                httpEngine = engine
                install(Postgrest)
            }
        return PostgrestTagRows(client.postgrest)
    }

    @Test
    fun links_are_read_by_machine_and_owner() =
        runTest {
            val held = link(press, "ноги")
            val engine = engineAnswering(MACHINE_TAG_TABLE to linksJson(listOf(held)))

            val links = rowsOn(engine).linksOf(press)

            val request = engine.requestHistory.single()
            assertEquals(MACHINE_TAG_TABLE, request.table())
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("eq.${press.id.value}", request.url.parameters["machine_id"])
            assertEquals("eq.${ivan.value}", request.url.parameters["user_id"])
            assertEquals(listOf(held), links)
            assertEquals(
                setOf("machine_id", "user_id", "order", "offset", "limit", "select"),
                request.url.parameters.names(),
            )
        }

    @Test
    fun an_ownerless_machine_s_links_are_read_with_no_owner() =
        runTest {
            val loose = Machine.new("Жим", null, t0)
            val engine = engineAnswering()

            rowsOn(engine).linksOf(loose)

            val request = engine.requestHistory.single()
            assertEquals("eq.${loose.id.value}", request.url.parameters["machine_id"])
            assertEquals("is.null", request.url.parameters["user_id"])
        }

    @Test
    fun held_elsewhere_counts_only_links_from_live_machines_of_the_owner() =
        runTest {
            val onA = link(machineA, "ноги")
            val onB = link(machineB, "спина")
            val engine =
                engineAnswering(
                    MACHINE_TAG_TABLE to linksJson(listOf(onA, onB)),
                    MACHINE_TABLE to machinesJson(listOf(machineA)),
                )

            val held = rowsOn(engine).heldElsewhere(press, listOf(onA.tagId, onB.tagId))

            assertEquals(setOf(onA.tagId), held)
            val requests = engine.requestHistory.associateBy { it.table() }
            val links = requests.getValue(MACHINE_TAG_TABLE).url.parameters
            assertEquals("eq.false", links["deleted"])
            assertEquals("eq.${ivan.value}", links["user_id"])
            assertEquals("neq.${press.id.value}", links["machine_id"])
            assertEquals("in.(${onA.tagId},${onB.tagId})", links["tag_id"])
            val machines = requests.getValue(MACHINE_TABLE).url.parameters
            assertEquals("eq.false", machines["deleted"])
            assertEquals("eq.${ivan.value}", machines["user_id"])
            assertEquals("in.(${machineA.id.value},${machineB.id.value})", machines["id"])
        }

    @Test
    fun an_ownerless_machine_s_held_elsewhere_reads_match_no_owner() =
        runTest {
            val loose = Machine.new("Жим", null, t0)
            val other = Machine.new("Тяга", null, t0)
            val onOther = link(other, "ноги")
            val engine =
                engineAnswering(
                    MACHINE_TAG_TABLE to linksJson(listOf(onOther)),
                    MACHINE_TABLE to machinesJson(listOf(other)),
                )

            val held = rowsOn(engine).heldElsewhere(loose, listOf(onOther.tagId))

            assertEquals(setOf(onOther.tagId), held)
            val requests = engine.requestHistory.associateBy { it.table() }
            assertEquals("is.null", requests.getValue(MACHINE_TAG_TABLE).url.parameters["user_id"])
            assertEquals("is.null", requests.getValue(MACHINE_TABLE).url.parameters["user_id"])
        }

    @Test
    fun tags_are_read_by_id() =
        runTest {
            val legs = tag("ноги")
            val back = tag("спина")
            val engine =
                engineAnswering(
                    TAG_TABLE to Json.encodeToString(listOf(legs, back).map(TagRow::of)),
                )

            val tags = rowsOn(engine).tags(listOf(legs.id, back.id))

            val request = engine.requestHistory.single()
            assertEquals(TAG_TABLE, request.table())
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("in.(${legs.id},${back.id})", request.url.parameters["id"])
            assertEquals(listOf(legs, back), tags)
        }

    @Test
    fun held_elsewhere_reads_no_machine_when_no_link_holds_the_tags() =
        runTest {
            val engine = engineAnswering(MACHINE_TAG_TABLE to "[]")

            val held = rowsOn(engine).heldElsewhere(press, listOf(tagId(ivan, "ноги")))

            assertEquals(emptySet(), held)
            assertEquals(listOf(MACHINE_TAG_TABLE), engine.requestHistory.map { it.table() })
        }

    @Test
    fun rows_are_upserted_in_the_wire_format() =
        runTest {
            val engine = engineAnswering()
            val rows = rowsOn(engine)
            val tag = tag("ноги")
            val link = link(press, "ноги")

            rows.upsertTags(listOf(tag))
            rows.upsertLinks(listOf(link))

            val (tagRequest, linkRequest) = engine.requestHistory
            assertEquals(TAG_TABLE, tagRequest.table())
            assertEquals(HttpMethod.Post, tagRequest.method)
            assertEquals(
                listOf(TagRow.of(tag)),
                Json.decodeFromString<List<TagRow>>(
                    tagRequest.body.toByteArray().decodeToString(),
                ),
            )
            assertEquals(MACHINE_TAG_TABLE, linkRequest.table())
            assertEquals(HttpMethod.Post, linkRequest.method)
            listOf(tagRequest, linkRequest).forEach {
                assertContains(
                    it.headers[HttpHeaders.Prefer].orEmpty(),
                    "resolution=merge-duplicates",
                )
            }
            assertEquals(
                listOf(MachineTagRow.of(link)),
                Json.decodeFromString<List<MachineTagRow>>(
                    linkRequest.body.toByteArray().decodeToString(),
                ),
            )
        }
}
