package monster.greyde.kachalochka.core.data.gym

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class StoragePhotoImagesTest {
    private val ivan = UserId("11111111-1111-4111-8111-111111111111")
    private val oleg = UserId("33333333-3333-4333-8333-333333333333")
    private val now = Instant.fromEpochSeconds(1_700_000_000)
    private val files = InMemoryPhotoFiles()
    private val requests = mutableListOf<HttpRequestData>()
    private val served = byteArrayOf(9, 8, 7)

    private fun images(keeps: (Photo) -> Boolean = { false }): StoragePhotoImages {
        val engine =
            MockEngine { request ->
                requests += request
                respond(served, HttpStatusCode.OK)
            }
        val client =
            createSupabaseClient("https://example.test", "anon-key") {
                httpEngine = engine
                install(Storage)
            }
        return StoragePhotoImages(lazyOf(client), files, keeps, Dispatchers.Unconfined)
    }

    @Test
    fun a_photo_on_the_device_is_read_without_asking_the_server() =
        runTest {
            val photo = Photo.new(MachineId.random(), ivan, now)
            files.write(photo.id, byteArrayOf(1))

            assertContentEquals(byteArrayOf(1), images().bytes(photo))
            assertTrue(requests.isEmpty())
        }

    @Test
    fun another_photo_is_downloaded_from_its_owner_s_folder_and_kept_when_asked() =
        runTest {
            val photo = Photo.new(MachineId.random(), ivan, now)

            assertContentEquals(served, images(keeps = { it.userId == ivan }).bytes(photo))

            assertTrue(
                requests
                    .single()
                    .url.encodedPath
                    .endsWith("/photos/${ivan.value}/${photo.id.value}"),
            )
            assertContentEquals(served, files.read(photo.id))
        }

    @Test
    fun a_friend_s_photo_is_downloaded_but_never_kept() =
        runTest {
            val photo = Photo.new(MachineId.random(), oleg, now)

            assertContentEquals(served, images(keeps = { it.userId == ivan }).bytes(photo))

            assertNull(files.read(photo.id))
        }

    @Test
    fun an_unowned_photo_away_from_the_device_has_no_bytes() =
        runTest {
            assertNull(images().bytes(Photo.new(MachineId.random(), null, now)))
            assertEquals(0, requests.size)
        }
}
