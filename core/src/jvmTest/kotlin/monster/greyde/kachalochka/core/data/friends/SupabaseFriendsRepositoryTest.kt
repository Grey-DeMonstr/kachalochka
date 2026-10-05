package monster.greyde.kachalochka.core.data.friends

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.HttpClientEngine
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
import monster.greyde.kachalochka.core.data.gym.MachineLinkRow
import monster.greyde.kachalochka.core.data.gym.MachineRow
import monster.greyde.kachalochka.core.data.gym.PhotoRow
import monster.greyde.kachalochka.core.data.gym.VisitRow
import monster.greyde.kachalochka.core.data.gym.WorkoutSetRow
import monster.greyde.kachalochka.core.domain.friends.Friend
import monster.greyde.kachalochka.core.domain.friends.FriendGroup
import monster.greyde.kachalochka.core.domain.friends.FriendMachine
import monster.greyde.kachalochka.core.domain.friends.FriendResult
import monster.greyde.kachalochka.core.domain.friends.FriendVisit
import monster.greyde.kachalochka.core.domain.friends.GroupId
import monster.greyde.kachalochka.core.domain.friends.GroupMember
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkId
import monster.greyde.kachalochka.core.domain.gym.MachinePeaks
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.PhotoId
import monster.greyde.kachalochka.core.domain.gym.SetPeak
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.identity.Avatar
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

private fun HttpRequestData.table() = url.encodedPath.substringAfterLast('/')

/** Answers each table's body however the requests interleave, for reads sent concurrently. */
private fun answeringByTable(vararg bodies: Pair<String, String>): MockEngine {
    val byTable = bodies.toMap()
    return MockEngine { request ->
        respond(byTable.getValue(request.table()), HttpStatusCode.OK, jsonHeaders())
    }
}

private fun repositoryOn(engine: HttpClientEngine): SupabaseFriendsRepository {
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

private fun olegSet(
    visit: VisitId,
    machine: MachineId,
    weight: Double,
    minutes: Int,
) = WorkoutSet(
    id = WorkoutSetId.random(),
    userId = OLEG,
    visitId = visit,
    machineId = machine,
    weight = weight,
    reps = 8,
    position = 0,
    recordedAt = NOW + minutes.minutes,
    updatedAt = NOW,
    deleted = false,
)

private val OLEG_S_GROUP =
    """[{"id":"$GROUP","name":"Зал на Лесной","owner_id":"${OLEG.value}",""" +
        """"invite_code":"ABCD2345","deleted":false}]"""

class SupabaseFriendsRepositoryTest {
    private suspend fun HttpRequestData.bodyText() = body.toByteArray().decodeToString()

    @Test
    fun group_photos_are_the_live_photos_of_every_group_mate_in_one_read() =
        runTest {
            val photo = Photo.new(MachineId.random(), OLEG, NOW)
            val requests = mutableListOf<HttpRequestData>()
            val engine =
                MockEngine { request ->
                    requests += request
                    val body =
                        when (request.table()) {
                            "group_member" -> MEMBERSHIPS
                            else -> Json.encodeToString(listOf(PhotoRow.of(photo)))
                        }
                    respond(body, HttpStatusCode.OK, jsonHeaders())
                }

            assertEquals(listOf(photo), repositoryOn(engine).groupPhotos(IVAN))

            val query = requests.single { it.table() == "photo" }.url.parameters
            assertEquals("in.(${OLEG.value})", query["user_id"])
            assertEquals("eq.false", query["deleted"])
        }

    @Test
    fun a_machine_s_photos_are_its_live_rows_oldest_first() =
        runTest {
            val press = MachineId.random()
            val later = Photo.new(press, OLEG, NOW + 1.minutes)
            val first = Photo.new(press, OLEG, NOW)
            val requests = mutableListOf<HttpRequestData>()
            val engine =
                MockEngine { request ->
                    requests += request
                    val rows = listOf(later, first).map(PhotoRow::of)
                    respond(Json.encodeToString(rows), HttpStatusCode.OK, jsonHeaders())
                }

            val photos = repositoryOn(engine).photos(press)

            assertEquals(listOf(first, later), photos)
            val query = requests.single().url.parameters
            assertEquals("photo", requests.single().table())
            assertEquals("eq.${press.value}", query["machine_id"])
            assertEquals("eq.false", query["deleted"])
        }

    @Test
    fun groups_are_counted_by_their_live_members() =
        runTest {
            val engine =
                answeringByTable(
                    "friend_group" to OLEG_S_GROUP,
                    "group_member" to MEMBERSHIPS,
                )

            val groups = repositoryOn(engine).groups()

            assertEquals(
                listOf(FriendGroup(GroupId(GROUP), "Зал на Лесной", OLEG, "ABCD2345", 2)),
                groups,
            )
            val requests = engine.requestHistory.associateBy { it.table() }
            assertEquals(setOf("friend_group", "group_member"), requests.keys)
            requests.values.forEach { assertEquals("eq.false", it.url.parameters["deleted"]) }
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
            val statuses =
                listOf(
                    HttpStatusCode.BadRequest,
                    HttpStatusCode.NotFound,
                    HttpStatusCode.InternalServerError,
                )
            for (status in statuses) {
                val engine = MockEngine.Queue()
                engine.answer(
                    """{"code":"PT404","details":null,"hint":null,""" +
                        """"message":"unknown invite code"}""",
                    status,
                )

                assertNull(repositoryOn(engine).join("ZZZZ2345"), status.toString())
            }
        }

    @Test
    fun a_not_found_answer_to_joining_is_an_unknown_code_whatever_the_body() =
        runTest {
            val engine = MockEngine.Queue()
            engine.answer("""{"message":"unknown invite code"}""", HttpStatusCode.NotFound)

            assertNull(repositoryOn(engine).join("ZZZZ2345"))
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
    fun group_visits_are_asked_for_by_mates_around_the_days_and_paired_with_their_friend() =
        runTest {
            val dated = Visit(VisitId.random(), OLEG, CalendarDay(2023, 11, 14), NOW, NOW, false)
            val undated = Visit(VisitId.random(), OLEG, null, NOW, NOW, false)
            val engine = MockEngine.Queue()
            engine.answer(MEMBERSHIPS)
            engine.answer(Json.encodeToString(listOf(dated, undated).map(VisitRow::of)))

            val visits =
                repositoryOn(engine).groupVisits(
                    IVAN,
                    CalendarDay(2023, 11, 1),
                    CalendarDay(2023, 11, 30),
                )

            val oleg = Friend(OLEG, "Олег")
            assertEquals(listOf(FriendVisit(oleg, dated), FriendVisit(oleg, undated)), visits)
            val params = engine.requestHistory[1].url.parameters
            assertEquals("in.(${OLEG.value})", params["user_id"])
            assertEquals("eq.false", params["deleted"])
            val either = params["or"].orEmpty()
            assertTrue("and(day.gte.2023-11-01,day.lte.2023-11-30)" in either, either)
            assertTrue(
                "and(day.is.null,recorded_at.gte.\"2023-10-31T00:00:00Z\"," +
                    "recorded_at.lt.\"2023-12-02T00:00:00Z\")" in either,
                either,
            )
        }

    @Test
    fun without_mates_group_visits_ask_for_nothing_more() =
        runTest {
            val engine = MockEngine.Queue()
            engine.answer(MEMBERSHIPS.substringBefore(",{") + "]")

            val visits =
                repositoryOn(engine).groupVisits(
                    IVAN,
                    CalendarDay(2023, 11, 1),
                    CalendarDay(2023, 11, 30),
                )

            assertEquals(emptyList(), visits)
            assertEquals(1, engine.requestHistory.size)
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
    fun group_links_are_the_mates_live_ones() =
        runTest {
            val link =
                MachineLink(
                    MachineLinkId.random(),
                    OLEG,
                    MachineId.random(),
                    MachineId.random(),
                    NOW,
                    deleted = false,
                )
            val engine = MockEngine.Queue()
            engine.answer(MEMBERSHIPS)
            engine.answer(Json.encodeToString(listOf(MachineLinkRow.of(link))))

            assertEquals(listOf(link), repositoryOn(engine).groupLinks(IVAN))

            val request = engine.requestHistory[1]
            assertTrue(request.url.encodedPath.endsWith("/machine_link"), request.url.encodedPath)
            assertEquals("in.(${OLEG.value})", request.url.parameters["user_id"])
            assertEquals("eq.false", request.url.parameters["deleted"])
        }

    @Test
    fun members_and_mates_carry_their_avatars() =
        runTest {
            val photo = PhotoId.random()
            val picture = "https://example.test/oleg.png"
            val rows =
                """[{"group_id":"$GROUP","user_id":"${IVAN.value}","display_name":"Иван",""" +
                    """"deleted":false,"avatar_photo":"${photo.value}","picture_url":null},""" +
                    """{"group_id":"$GROUP","user_id":"${OLEG.value}","display_name":"Олег",""" +
                    """"deleted":false,"avatar_photo":null,"picture_url":"$picture"}]"""
            val engine = MockEngine { respond(rows, HttpStatusCode.OK, jsonHeaders()) }
            val repository = repositoryOn(engine)
            val group = FriendGroup(GroupId(GROUP), "Зал на Лесной", OLEG, "ABCD2345", 2)

            assertEquals(
                listOf(
                    GroupMember(OLEG, "Олег", isOwner = true, Avatar(picture = picture)),
                    GroupMember(IVAN, "Иван", isOwner = false, Avatar(photo = photo)),
                ),
                repository.members(group),
            )
            assertEquals(
                listOf(Friend(OLEG, "Олег", Avatar(picture = picture))),
                repository.mates(IVAN),
            )
        }

    @Test
    fun group_peaks_ask_the_server_to_reduce_the_mates_sets() =
        runTest {
            val machine = MachineId.random()
            val engine = MockEngine.Queue()
            engine.answer(MEMBERSHIPS)
            engine.answer(
                """[{"machine_id":"${machine.value}","heaviest":80,"heaviest_reps":8,""" +
                    """"lightest":60,"lightest_reps":12,"last_at":"2024-01-01T00:00:00+00:00",""" +
                    """"visits":4}]""",
            )

            assertEquals(
                listOf(MachinePeaks(machine, SetPeak(80.0, 8), SetPeak(60.0, 12), NOW, 4)),
                repositoryOn(engine).groupPeaks(IVAN),
            )

            val request = engine.requestHistory[1]
            assertTrue(request.url.encodedPath.endsWith("/rpc/machine_peaks"))
            assertEquals("""{"owners":["${OLEG.value}"]}""", request.bodyText())
        }

    @Test
    fun the_latest_results_on_a_cluster_come_from_each_friend_s_latest_visit() =
        runTest {
            val press = Machine.new("Жим ногами", OLEG, NOW)
            val latest = VisitId.random()

            fun set(
                visit: VisitId,
                weight: Double,
                minutes: Int,
            ) = olegSet(visit, press.id, weight, minutes)
            val first = set(latest, 80.0, 60)
            val second = set(latest, 85.0, 61)
            val engine = MockEngine.Queue()
            engine.answer(MEMBERSHIPS)
            engine.answer(Json.encodeToString(listOf(MachineRow.of(press))))
            engine.answer(Json.encodeToString(listOf(WorkoutSetRow.of(second))))
            engine.answer(Json.encodeToString(listOf(second, first).map(WorkoutSetRow::of)))

            val results = repositoryOn(engine).latestOn(IVAN, setOf(press.id))

            assertEquals(
                listOf(FriendResult(Friend(OLEG, "Олег"), press, listOf(first, second))),
                results,
            )
            val (_, machineRequest, newestRequest, visitRequest) = engine.requestHistory
            assertEquals("in.(${press.id.value})", machineRequest.url.parameters["id"])
            assertEquals("in.(${OLEG.value})", machineRequest.url.parameters["user_id"])
            val order = newestRequest.url.parameters["order"].orEmpty()
            assertTrue(order.startsWith("recorded_at.desc"), order)
            assertEquals("1", newestRequest.url.parameters["limit"])
            assertEquals("eq.${OLEG.value}", newestRequest.url.parameters["user_id"])
            assertEquals("in.(${latest.value})", visitRequest.url.parameters["visit_id"])
            assertEquals("in.(${OLEG.value})", visitRequest.url.parameters["user_id"])
        }

    /** Answers as PostgREST would from these rows, filtering, ordering and limiting them. */
    private fun serverWith(
        memberships: String,
        machines: List<Machine>,
        sets: List<WorkoutSet>,
    ) = MockEngine { request ->
        val params = request.url.parameters

        fun matches(
            column: String,
            value: String,
        ): Boolean {
            val filter = params[column] ?: return true
            return filter == "eq.$value" ||
                value in filter.removePrefix("in.(").removeSuffix(")").split(",")
        }
        val body =
            when (request.url.encodedPath.substringAfterLast('/')) {
                "group_member" -> memberships
                "machine" -> Json.encodeToString(machines.map(MachineRow::of))
                else ->
                    sets
                        .filter {
                            matches("user_id", it.userId?.value.orEmpty()) &&
                                matches("visit_id", it.visitId.value) &&
                                matches("machine_id", it.machineId.value)
                        }.sortedByDescending { it.recordedAt }
                        .take(params["limit"]?.toInt() ?: Int.MAX_VALUE)
                        .let { rows -> Json.encodeToString(rows.map(WorkoutSetRow::of)) }
            }
        respond(body, HttpStatusCode.OK, jsonHeaders())
    }

    @Test
    fun a_friend_with_many_newer_sets_never_crowds_another_out() =
        runTest {
            val anna = UserId("44444444-4444-4444-8444-444444444444")
            val olegPress = Machine.new("Жим ногами", OLEG, NOW)
            val annaPress = Machine.new("Платформа", anna, NOW)
            val annaSet = olegSet(VisitId.random(), annaPress.id, 60.0, 0).copy(userId = anna)
            val olegSets = (1..60).map { olegSet(VisitId.random(), olegPress.id, 80.0, it) }
            val memberships =
                MEMBERSHIPS.dropLast(1) +
                    """,{"group_id":"$GROUP","user_id":"${anna.value}",""" +
                    """"display_name":"Анна","deleted":false}]"""
            val server = serverWith(memberships, listOf(olegPress, annaPress), olegSets + annaSet)

            val results = repositoryOn(server).latestOn(IVAN, setOf(olegPress.id, annaPress.id))

            assertEquals(
                listOf(
                    FriendResult(Friend(OLEG, "Олег"), olegPress, listOf(olegSets.last())),
                    FriendResult(Friend(anna, "Анна"), annaPress, listOf(annaSet)),
                ),
                results,
            )
        }

    @Test
    fun a_cluster_without_friends_machines_asks_for_nothing() =
        runTest {
            val engine = MockEngine.Queue()

            assertEquals(emptyList(), repositoryOn(engine).latestOn(IVAN, emptySet()))
            assertEquals(0, engine.requestHistory.size)
        }

    @Test
    fun a_visit_s_sets_are_asked_for_by_its_owner_in_visit_order() =
        runTest {
            val visit = Visit(VisitId.random(), OLEG, CalendarDay(2023, 11, 14), NOW, NOW, false)
            val machine = MachineId.random()
            val first = olegSet(visit.id, machine, 80.0, 0)
            val second = olegSet(visit.id, machine, 85.0, 1)
            val engine = MockEngine.Queue()
            engine.answer(Json.encodeToString(listOf(second, first).map(WorkoutSetRow::of)))

            assertEquals(listOf(first, second), repositoryOn(engine).sets(visit))

            val params =
                engine.requestHistory
                    .single()
                    .url.parameters
            assertEquals("eq.${visit.id.value}", params["visit_id"])
            assertEquals("eq.${OLEG.value}", params["user_id"])
            assertEquals("eq.false", params["deleted"])
        }

    @Test
    fun a_member_s_sets_on_a_machine_are_read_past_the_row_cap_oldest_first() =
        runTest {
            val machine = MachineId.random()
            val visit = VisitId.random()
            val firstPage = (0 until 1000).map { olegSet(visit, machine, 80.0, it) }
            val last = olegSet(visit, machine, 85.0, 1000)
            val engine = MockEngine.Queue()
            engine.answer(Json.encodeToString(firstPage.map(WorkoutSetRow::of)))
            engine.answer(Json.encodeToString(listOf(WorkoutSetRow.of(last))))

            assertEquals(firstPage + last, repositoryOn(engine).setsOn(OLEG, machine))

            val (first, second) = engine.requestHistory
            listOf(first, second).forEach {
                assertEquals("workout_set", it.table())
                assertEquals("eq.${OLEG.value}", it.url.parameters["user_id"])
                assertEquals("eq.${machine.value}", it.url.parameters["machine_id"])
                assertEquals("eq.false", it.url.parameters["deleted"])
                val order = it.url.parameters["order"].orEmpty()
                assertTrue(order.startsWith("recorded_at.asc") && ",id.asc" in order, order)
            }
            assertEquals(
                "0" to "1000",
                first.url.parameters["offset"] to first.url.parameters["limit"],
            )
            assertEquals(
                "1000" to "1000",
                second.url.parameters["offset"] to second.url.parameters["limit"],
            )
        }

    @Test
    fun a_short_page_of_a_member_s_sets_ends_the_read() =
        runTest {
            val machine = MachineId.random()
            val set = olegSet(VisitId.random(), machine, 80.0, 0)
            val engine = MockEngine.Queue()
            engine.answer(Json.encodeToString(listOf(WorkoutSetRow.of(set))))

            assertEquals(listOf(set), repositoryOn(engine).setsOn(OLEG, machine))
            assertEquals(1, engine.requestHistory.size)
        }

    @Test
    fun members_list_the_owner_first_then_everyone_by_name() =
        runTest {
            val anna = UserId("44444444-4444-4444-8444-444444444444")
            val engine = MockEngine.Queue()
            engine.answer(
                MEMBERSHIPS.dropLast(1) +
                    """,{"group_id":"$GROUP","user_id":"${anna.value}",""" +
                    """"display_name":"Анна","deleted":false}]""",
            )
            val group = FriendGroup(GroupId(GROUP), "Зал на Лесной", OLEG, "ABCD2345", 3)

            val members = repositoryOn(engine).members(group)

            assertEquals(
                listOf(
                    GroupMember(OLEG, "Олег", isOwner = true),
                    GroupMember(anna, "Анна", isOwner = false),
                    GroupMember(IVAN, "Иван", isOwner = false),
                ),
                members,
            )
            val params =
                engine.requestHistory
                    .single()
                    .url.parameters
            assertEquals("eq.$GROUP", params["group_id"])
            assertEquals("eq.false", params["deleted"])
        }

    @Test
    fun a_group_is_read_by_its_id_with_its_live_members_counted() =
        runTest {
            val engine = MockEngine.Queue()
            engine.answer(OLEG_S_GROUP)
            engine.answer(MEMBERSHIPS)

            assertEquals(
                FriendGroup(GroupId(GROUP), "Зал на Лесной", OLEG, "ABCD2345", 2),
                repositoryOn(engine).group(GroupId(GROUP)),
            )
            val (groupRequest, memberRequest) = engine.requestHistory
            assertEquals("eq.$GROUP", groupRequest.url.parameters["id"])
            assertEquals("eq.false", groupRequest.url.parameters["deleted"])
            assertEquals("eq.$GROUP", memberRequest.url.parameters["group_id"])
        }

    @Test
    fun a_group_the_account_no_longer_sees_is_null() =
        runTest {
            val engine = MockEngine.Queue()
            engine.answer("[]")

            assertNull(repositoryOn(engine).group(GroupId(GROUP)))
            assertEquals(1, engine.requestHistory.size)
        }

    @Test
    fun creating_a_group_sends_its_trimmed_name_and_answers_its_id() =
        runTest {
            val engine = MockEngine.Queue()
            engine.answer("\"$GROUP\"")

            assertEquals(GroupId(GROUP), repositoryOn(engine).create("  Зал на Лесной "))

            val request = engine.requestHistory.single()
            val path = request.url.encodedPath
            assertTrue(path.endsWith("/rpc/create_group"), path)
            val body = request.bodyText()
            assertTrue("\"group_name\":\"Зал на Лесной\"" in body, body)
        }

    @Test
    fun leaving_a_group_names_it() =
        runTest {
            val engine = MockEngine.Queue()
            engine.answer("", HttpStatusCode.NoContent)

            repositoryOn(engine).leave(GroupId(GROUP))

            val request = engine.requestHistory.single()
            val path = request.url.encodedPath
            assertTrue(path.endsWith("/rpc/leave_group"), path)
            val body = request.bodyText()
            assertTrue("\"target\":\"$GROUP\"" in body, body)
        }

    @Test
    fun breaking_links_names_the_machine() =
        runTest {
            val engine = MockEngine.Queue()
            engine.answer("", HttpStatusCode.NoContent)
            val machine = MachineId.random()

            repositoryOn(engine).breakLinks(machine)

            val request = engine.requestHistory.single()
            val path = request.url.encodedPath
            assertTrue(path.endsWith("/rpc/break_machine_links"), path)
            val body = request.bodyText()
            assertTrue("\"target\":\"${machine.value}\"" in body, body)
        }

    @Test
    fun repointing_links_names_both_machines() =
        runTest {
            val engine = MockEngine.Queue()
            engine.answer("", HttpStatusCode.NoContent)
            val removed = MachineId.random()
            val kept = MachineId.random()

            repositoryOn(engine).repointLinks(removed, kept)

            val request = engine.requestHistory.single()
            val path = request.url.encodedPath
            assertTrue(path.endsWith("/rpc/repoint_machine_links"), path)
            val body = request.bodyText()
            assertTrue("\"removed\":\"${removed.value}\"" in body, body)
            assertTrue("\"kept\":\"${kept.value}\"" in body, body)
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

    @Test
    fun mates_are_the_members_of_the_groups_holding_the_viewer() =
        runTest {
            val work = "88888888-8888-4888-8888-888888888888"
            val sasha = UserId("66666666-6666-4666-8666-666666666666")
            val pasha = UserId("44444444-4444-4444-8444-444444444444")

            fun member(
                group: String,
                user: UserId,
                name: String,
            ) = """{"group_id":"$group","user_id":"${user.value}","display_name":"$name",""" +
                """"deleted":false}"""
            val rows =
                listOf(
                    member(GROUP, IVAN, "Иван"),
                    member(GROUP, sasha, "Саша"),
                    member(GROUP, OLEG, "Олег"),
                    member(work, IVAN, "Иван"),
                    member(work, pasha, "Паша"),
                ).joinToString(",", "[", "]")
            val repository =
                repositoryOn(MockEngine { respond(rows, HttpStatusCode.OK, jsonHeaders()) })

            assertEquals(
                listOf(Friend(IVAN, "Иван"), Friend(OLEG, "Олег")),
                repository.mates(sasha),
            )
            assertEquals(
                setOf(sasha, OLEG, pasha),
                repository.mates(IVAN).map { it.userId }.toSet(),
            )
        }
}
