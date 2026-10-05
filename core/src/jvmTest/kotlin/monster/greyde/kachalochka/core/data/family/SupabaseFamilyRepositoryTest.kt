package monster.greyde.kachalochka.core.data.family

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.domain.family.Acceptance
import monster.greyde.kachalochka.core.domain.family.Family
import monster.greyde.kachalochka.core.domain.family.FamilyMember
import monster.greyde.kachalochka.core.domain.gym.PhotoId
import monster.greyde.kachalochka.core.domain.identity.Avatar
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

private val IVAN = UserId("11111111-1111-4111-8111-111111111111")
private val SASHA = UserId("66666666-6666-4666-8666-666666666666")
private val MASHA = UserId("77777777-7777-4777-8777-777777777777")

private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

private fun MockEngine.Queue.answer(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
) = enqueue { respond(body, status, jsonHeaders()) }

private fun repositoryOn(engine: HttpClientEngine): SupabaseFamilyRepository {
    val client =
        createSupabaseClient("https://example.test", "anon-key") {
            httpEngine = engine
            install(Postgrest)
        }
    return SupabaseFamilyRepository(lazyOf(client))
}

private suspend fun HttpRequestData.bodyText() = body.toByteArray().decodeToString()

private fun HttpRequestData.function() = url.encodedPath.substringAfterLast('/')

class SupabaseFamilyRepositoryTest {
    @Test
    fun the_family_splits_children_from_guardians_each_by_name() =
        runTest {
            val photo = PhotoId.random()
            val picture = "https://example.test/m.png"
            val engine = MockEngine.Queue()
            engine.answer(
                """[{"user_id":"${SASHA.value}","relation":"child","display_name":"Саша",""" +
                    """"avatar_photo":"${photo.value}","picture_url":null},""" +
                    """{"user_id":"${MASHA.value}","relation":"child","display_name":"Маша",""" +
                    """"avatar_photo":null,"picture_url":"$picture"},""" +
                    """{"user_id":"${IVAN.value}","relation":"guardian",""" +
                    """"display_name":"Иван","avatar_photo":null,"picture_url":null}]""",
            )

            val family = repositoryOn(engine).family()

            assertEquals(
                Family(
                    children =
                        listOf(
                            FamilyMember(MASHA, "Маша", Avatar(picture = picture)),
                            FamilyMember(SASHA, "Саша", Avatar(photo = photo)),
                        ),
                    guardians = listOf(FamilyMember(IVAN, "Иван")),
                ),
                family,
            )
            assertEquals("my_family", engine.requestHistory.single().function())
        }

    @Test
    fun offering_answers_the_new_code() =
        runTest {
            val engine = MockEngine.Queue()
            engine.answer("\"PAPA2345\"")

            assertEquals("PAPA2345", repositoryOn(engine).offer())
            assertEquals("offer_guardianship", engine.requestHistory.single().function())
        }

    @Test
    fun a_parent_s_code_links_and_answers_the_parent() =
        runTest {
            val engine = MockEngine.Queue()
            engine.answer("\"${IVAN.value}\"")

            assertEquals(Acceptance.Linked(IVAN), repositoryOn(engine).accept("PAPA2345"))

            val request = engine.requestHistory.single()
            assertEquals("accept_guardian", request.function())
            val body = request.bodyText()
            assertTrue("\"code\":\"PAPA2345\"" in body, body)
        }

    @Test
    fun an_unknown_or_expired_code_is_unknown_whatever_the_status() =
        runTest {
            val statuses = listOf(HttpStatusCode.BadRequest, HttpStatusCode.NotFound)
            for (status in statuses) {
                val engine = MockEngine.Queue()
                engine.answer(
                    """{"code":"PT404","details":null,"hint":null,""" +
                        """"message":"unknown guardian code"}""",
                    status,
                )

                assertEquals(
                    Acceptance.UnknownCode,
                    repositoryOn(engine).accept("ZZZZ2345"),
                    status.toString(),
                )
            }
        }

    @Test
    fun a_not_found_answer_is_an_unknown_code_whatever_the_body() =
        runTest {
            val engine = MockEngine.Queue()
            engine.answer("""{"message":"unknown guardian code"}""", HttpStatusCode.NotFound)

            assertEquals(Acceptance.UnknownCode, repositoryOn(engine).accept("ZZZZ2345"))
        }

    @Test
    fun the_account_s_own_code_is_refused_as_its_own() =
        runTest {
            val engine = MockEngine.Queue()
            engine.answer(
                """{"code":"P0001","details":null,"hint":null,""" +
                    """"message":"your own guardian code"}""",
                HttpStatusCode.BadRequest,
            )

            assertEquals(Acceptance.OwnCode, repositoryOn(engine).accept("PAPA2345"))
        }

    @Test
    fun any_other_refusal_to_accept_is_a_failure() =
        runTest {
            val engine = MockEngine.Queue()
            engine.answer(
                """{"code":"XX000","details":null,"hint":null,"message":"internal"}""",
                HttpStatusCode.InternalServerError,
            )

            assertFails { repositoryOn(engine).accept("PAPA2345") }
        }

    @Test
    fun ending_a_link_names_both_sides() =
        runTest {
            val engine = MockEngine.Queue()
            engine.answer("")

            repositoryOn(engine).end(SASHA, IVAN)

            val request = engine.requestHistory.single()
            assertEquals("end_guardianship", request.function())
            val body = request.bodyText()
            assertTrue("\"child\":\"${SASHA.value}\"" in body, body)
            assertTrue("\"guardian\":\"${IVAN.value}\"" in body, body)
        }
}
