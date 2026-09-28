package monster.greyde.kachalochka.core.data.gym

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.db.inMemoryDatabase
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class LocalPhotoRepositoryTest {
    private val database = inMemoryDatabase()
    private val outbox = OutboxDao(database)
    private val files = InMemoryPhotoFiles()
    private val repository = LocalPhotoRepository(database, outbox, files, Dispatchers.Unconfined)
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_123)
    private val ivan = UserId("11111111-1111-4111-8111-111111111111")
    private val press = MachineId.random()
    private val jpeg = byteArrayOf(1, 2, 3)

    @Test
    fun an_added_photo_keeps_its_bytes_and_reads_back_field_for_field() =
        runTest {
            val photo = Photo.new(press, ivan, now)

            repository.add(photo, jpeg)

            assertEquals(listOf(photo), repository.forMachine(press))
            assertContentEquals(jpeg, files.read(photo.id))
        }

    @Test
    fun an_owned_photo_is_enqueued_and_an_unowned_one_is_not() =
        runTest {
            val owned = Photo.new(press, ivan, now)

            repository.add(Photo.new(press, null, now), jpeg)
            repository.add(owned, jpeg)

            assertEquals(
                listOf(PHOTO_TABLE to owned.id.value),
                outbox.pending().map { it.tableName to it.rowId },
            )
        }

    @Test
    fun a_deleted_photo_leaves_the_machine_and_its_bytes_leave_the_device() =
        runTest {
            val photo = Photo.new(press, ivan, now)
            repository.add(photo, jpeg)

            repository.upsert(photo.copy(deleted = true, updatedAt = now + 1.minutes))

            assertEquals(emptyList(), repository.forMachine(press))
            assertNull(files.read(photo.id))
            assertEquals(1, outbox.pending().size)
        }

    @Test
    fun a_machine_s_photos_run_oldest_first_without_other_machines_photos() =
        runTest {
            val later = Photo.new(press, ivan, now + 1.minutes)
            val first = Photo.new(press, ivan, now)
            repository.add(later, jpeg)
            repository.add(first, jpeg)
            repository.add(Photo.new(MachineId.random(), ivan, now), jpeg)

            assertEquals(listOf(first, later), repository.forMachine(press))
        }

    @Test
    fun all_holds_the_owner_s_live_photos_of_every_machine() =
        runTest {
            val mine = Photo.new(press, ivan, now)
            val other = Photo.new(MachineId.random(), ivan, now)
            repository.add(mine, jpeg)
            repository.add(other, jpeg)
            repository.add(Photo.new(press, null, now), jpeg)
            val gone = Photo.new(press, ivan, now)
            repository.add(gone, jpeg)
            repository.upsert(gone.copy(deleted = true))

            assertEquals(setOf(mine, other), repository.all(ivan).toSet())
        }
}
