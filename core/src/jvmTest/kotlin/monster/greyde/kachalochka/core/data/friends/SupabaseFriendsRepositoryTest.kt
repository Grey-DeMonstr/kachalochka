package monster.greyde.kachalochka.core.data.friends

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
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
import monster.greyde.kachalochka.core.data.gym.MachineRow
import monster.greyde.kachalochka.core.data.gym.VisitRow
import monster.greyde.kachalochka.core.data.gym.WorkoutSetRow
import monster.greyde.kachalochka.core.domain.friends.Friend
import monster.greyde.kachalochka.core.domain.friends.FriendGroup
import monster.greyde.kachalochka.core.domain.friends.FriendMachine
import monster.greyde.kachalochka.core.domain.friends.FriendResult
import monster.greyde.kachalochka.core.domain.friends.GroupId
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val IVAN = UserId("11111111-1111-4111-8111-111111111111")
private val OLEG = UserId("33333333-3333-4333-8333-333333333333")
private const val GROUP = "55555555-5555-4555-8555-555555555555"
private val NOW = Instant.parse("2024-01-01T00:00:00Z")

private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

private fun MockEngine.Queue.answer(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
) = enqueue { respond(body, status, jsonHeaders()) }

private fun repositoryOn(engine: MockEngine.Queue): SupabaseFriendsRepository {
    val client =
        createSupabaseClient("https://example.test", "anon-key") {
            httpEngine = engine
            install(Postgrest)
        }
    val clock =
        object : Clock {
            override fun now(): Instant = NOW
        }
    return SupabaseFriendsRepository(lazyOf(client), clock)
}

private val MEMBERSHIPS =
    """[{"group_id":"$GROUP","user_id":"${IVAN.value}","display_name":"Иван",""" +
        """"deleted":false},{"group_id":"$GROUP","user_id":"${OLEG.value}",""" +
        """"display_name":"Олег","deleted":false}]"""

class SupabaseFriendsRepositoryTest {
    private suspend fun HttpRequestData.bodyText() = body.toByteArray().decodeToString()

    @Test
    fun groups_are_counted_by_their_live_members() =
        runTest {
            val engine = MockEngine.Queue()
            engine.answer(
                """[{"id":"$GROUP","name":"Зал на Лесной","owner_id":"${OLEG.value}",""" +
                    """"invite_code":"ABCD2345","deleted":false}]""",
            )
            engine.answer(MEMBERSHIPS)

            val groups = repositoryOn(engine).groups()

            assertEquals(
                listOf(FriendGroup(GroupId(GROUP), "Зал на Лесной", OLEG, "ABCD2345", 2)),
                groups,
            )
            val (groupRequest, memberRequest) = engine.requestHistory
            assertEquals("eq.false", groupRequest.url.parameters["deleted"])
            assertEquals("eq.false", memberRequest.url.parameters["deleted"])
        }

    @Test
    fun a_known_invite_code_answers_its_group() =
        runTest {
            val engine = MockEngine.Queue()
            engine.answer("\"$GROUP\"")

            assertEquals(GroupId(GROUP), repositoryOn(engine).join("ABCD2345"))

            val request = engine.requestHistory.single()
            val path = request.url.encodedPath
            assertTrue(path.endsWith("/rpc/join_group"), path)
            val body = request.bodyText()
            assertTrue("\"code\":\"ABCD2345\"" in body, body)
        }

    @Test
    fun an_unknown_invite_code_answers_null_whatever_the_status() =
        runTest {
            for (status in listOf(HttpStatusCode.BadRequest, HttpStatusCode.NotFound)) {
                val engine = MockEngine.Queue()
                engine.answer(
                    """{"code":"P0002","details":null,"hint":null,""" +
                        """"message":"unknown invite code"}""",
                    status,
                )

                assertNull(repositoryOn(engine).join("ZZZZ2345"), status.toString())
            }
        }

    @Test
    fun any_other_refusal_to_join_is_a_failure() =
        runTest {
            val engine = MockEngine.Queue()
            engine.answer(
                """{"code":"XX000","details":null,"hint":null,"message":"internal"}""",
                HttpStatusCode.InternalServerError,
            )

            assertFails { repositoryOn(engine).join("ABCD2345") }
        }

    @Test
    fun a_member_s_visits_are_asked_for_by_their_owner_without_deleted_ones() =
        runTest {
            val visit = Visit(VisitId.random(), OLEG, CalendarDay(2023, 11, 14), NOW, NOW, false)
            val engine = MockEngine.Queue()
            engine.answer(Json.encodeToString(listOf(VisitRow.of(visit))))

            assertEquals(listOf(visit), repositoryOn(engine).visits(OLEG))

            val params =
                engine.requestHistory
                    .single()
                    .url.parameters
            assertEquals("eq.${OLEG.value}", params["user_id"])
            assertEquals("eq.false", params["deleted"])
        }

    @Test
    fun group_machines_leave_the_viewer_out() =
        runTest {
            val press = Machine.new("Жим ногами", OLEG, NOW)
            val engine = MockEngine.Queue()
            engine.answer(MEMBERSHIPS)
            engine.answer(Json.encodeToString(listOf(MachineRow.of(press))))

            val machines = repositoryOn(engine).groupMachines(IVAN)

            assertEquals(listOf(FriendMachine(press, Friend(OLEG, "Олег"))), machines)
            assertEquals(
                "in.(${OLEG.value})",
                engine.requestHistory[1].url.parameters["user_id"],
            )
        }

    @Test
    fun the_latest_results_on_a_link_key_come_from_each_friend_s_latest_visit() =
        runTest {
            val key = MachineId.random()
            val press = Machine.new("Жим ногами", OLEG, NOW).copy(linkId = key)
            val older = VisitId.random()
            val latest = VisitId.random()

            fun set(
                visit: VisitId,
                weight: Double,
                minutes: Int,
            ) = WorkoutSet(
                id = WorkoutSetId.random(),
                userId = OLEG,
                visitId = visit,
                machineId = press.id,
                weight = weight,
                reps = 8,
                position = 0,
                recordedAt = NOW + minutes.minutes,
                updatedAt = NOW,
                deleted = false,
            )
            val first = set(latest, 80.0, 60)
            val second = set(latest, 85.0, 61)
            val engine = MockEngine.Queue()
            engine.answer(MEMBERSHIPS)
            engine.answer(Json.encodeToString(listOf(MachineRow.of(press))))
            engine.answer(
                Json.encodeToString(
                    listOf(second, first, set(older, 70.0, 0)).map(WorkoutSetRow::of),
                ),
            )
            engine.answer(Json.encodeToString(listOf(second, first).map(WorkoutSetRow::of)))

            val results = repositoryOn(engine).latestOn(IVAN, key)

            assertEquals(
                listOf(FriendResult(Friend(OLEG, "Олег"), listOf(first, second))),
                results,
            )
            val (_, machineRequest, recentRequest, visitRequest) = engine.requestHistory
            assertEquals(
                "(id.eq.${key.value},link_id.eq.${key.value})",
                machineRequest.url.parameters["or"],
            )
            val order = recentRequest.url.parameters["order"].orEmpty()
            assertTrue(order.startsWith("recorded_at.desc"), order)
            assertEquals("50", recentRequest.url.parameters["limit"])
            assertEquals("in.(${latest.value})", visitRequest.url.parameters["visit_id"])
        }

    @Test
    fun deleting_a_group_marks_it_deleted() =
        runTest {
            val engine = MockEngine.Queue()
            engine.answer("", HttpStatusCode.NoContent)

            repositoryOn(engine).delete(GroupId(GROUP))

            val request = engine.requestHistory.single()
            assertEquals(HttpMethod.Patch, request.method)
            assertEquals("eq.$GROUP", request.url.parameters["id"])
            val body = request.bodyText()
            assertTrue("\"deleted\":true" in body, body)
        }
}
