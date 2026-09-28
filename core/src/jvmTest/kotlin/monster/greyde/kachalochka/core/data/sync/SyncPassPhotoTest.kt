package monster.greyde.kachalochka.core.data.sync

import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.gym.MACHINE_LINK_TABLE
import monster.greyde.kachalochka.core.data.gym.MACHINE_TABLE
import monster.greyde.kachalochka.core.data.gym.PHOTO_TABLE
import monster.greyde.kachalochka.core.data.gym.VISIT_TABLE
import monster.greyde.kachalochka.core.data.gym.WORKOUT_SET_TABLE
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.T0
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class SyncPassPhotoTest {
    private val h = SyncHarness()
    private val jpeg = byteArrayOf(4, 5, 6)

    @Test
    fun a_photo_is_pushed_after_its_machine_and_links_and_before_sets() =
        runTest {
            val visit = ownedVisit(IVAN)
            val press = ownedPress(IVAN)
            h.sets.upsert(ownedSet(IVAN, visit, press))
            h.photos.add(Photo.new(press.id, IVAN, T0), jpeg)
            h.links.upsert(ownedLink(IVAN))
            h.visits.upsert(visit)
            h.machines.upsert(press)

            h.pass.run(listOf(IVAN))

            assertEquals(
                listOf(
                    MACHINE_TABLE,
                    VISIT_TABLE,
                    MACHINE_LINK_TABLE,
                    PHOTO_TABLE,
                    WORKOUT_SET_TABLE,
                ),
                h.gateway.pushed.map { it.substringBefore(':') },
            )
        }

    @Test
    fun a_live_photo_takes_its_bytes_along() =
        runTest {
            val photo = Photo.new(ownedPress(IVAN).id, IVAN, T0)
            h.photos.add(photo, jpeg)

            assertTrue(h.pass.run(listOf(IVAN)))

            assertContentEquals(jpeg, h.gateway.uploaded[photo.id])
            assertTrue(h.outbox.pending().isEmpty())
        }

    @Test
    fun a_deleted_photo_is_pushed_without_bytes_and_leaves_storage() =
        runTest {
            val photo = Photo.new(ownedPress(IVAN).id, IVAN, T0)
            h.photos.add(photo, jpeg)
            h.photos.upsert(photo.copy(deleted = true, updatedAt = T0 + 1.hours))

            h.pass.run(listOf(IVAN))

            assertFalse(photo.id in h.gateway.uploaded)
            assertEquals(listOf(photo.id), h.gateway.removedFromStorage)
        }

    @Test
    fun a_pulled_photo_is_written_locally_and_counts_for_the_watermark() =
        runTest {
            val photo = Photo.new(ownedPress(IVAN).id, IVAN, T0 + 3.hours)
            h.gateway.photosToPull = listOf(photo)

            h.pass.run(listOf(IVAN))

            assertEquals(listOf(photo), h.photos.forMachine(photo.machineId))
            assertEquals(T0 + 3.hours, h.watermarks.lastPullAt(IVAN))
        }

    @Test
    fun a_pulled_deletion_removes_the_photo_s_bytes_from_the_device() =
        runTest {
            val photo = Photo.new(ownedPress(IVAN).id, IVAN, T0)
            h.photos.add(photo, jpeg)
            h.pass.run(listOf(IVAN))
            h.gateway.photosToPull = listOf(photo.copy(deleted = true, updatedAt = T0 + 1.hours))

            h.pass.run(listOf(IVAN))

            assertEquals(emptyList(), h.photos.forMachine(photo.machineId))
            assertNull(h.files.read(photo.id))
        }
}
