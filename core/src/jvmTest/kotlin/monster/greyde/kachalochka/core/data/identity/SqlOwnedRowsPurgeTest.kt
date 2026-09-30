package monster.greyde.kachalochka.core.data.identity

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.sync.IVAN
import monster.greyde.kachalochka.core.data.sync.MISHA
import monster.greyde.kachalochka.core.data.sync.SyncHarness
import monster.greyde.kachalochka.core.data.sync.ownedLink
import monster.greyde.kachalochka.core.data.sync.ownedMeasure
import monster.greyde.kachalochka.core.data.sync.ownedMeasurement
import monster.greyde.kachalochka.core.data.sync.ownedPlan
import monster.greyde.kachalochka.core.data.sync.ownedPress
import monster.greyde.kachalochka.core.data.sync.ownedProfile
import monster.greyde.kachalochka.core.data.sync.ownedSet
import monster.greyde.kachalochka.core.data.sync.ownedVisit
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.T0
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SqlOwnedRowsPurgeTest {
    private val h = SyncHarness()
    private val purge = SqlOwnedRowsPurge(h.database, h.files, Dispatchers.Unconfined)

    private suspend fun record(owner: UserId): Photo {
        val visit = ownedVisit(owner)
        val press = ownedPress(owner)
        val neck = ownedMeasure(owner)
        val photo = Photo.new(press.id, owner, T0)
        h.visits.upsert(visit)
        h.machines.upsert(press)
        h.sets.upsert(ownedSet(owner, visit, press))
        h.plans.upsert(ownedPlan(owner))
        h.profiles.upsert(ownedProfile(owner))
        h.links.upsert(ownedLink(owner))
        h.measures.upsert(neck)
        h.measurements.upsert(ownedMeasurement(owner, neck))
        h.photos.add(photo, byteArrayOf(1))
        h.watermarks.advance(owner, T0)
        return photo
    }

    @Test
    fun every_row_of_the_owner_leaves_the_device_and_nobody_else_s() =
        runTest {
            val ivans = record(IVAN)
            val mishas = record(MISHA)

            purge.purge(IVAN)

            assertEquals(emptyList(), h.visits.all(IVAN))
            assertEquals(emptyList(), h.machines.all(IVAN))
            assertNull(h.profiles.forOwner(IVAN))
            assertEquals(emptyList(), h.plans.all(IVAN))
            assertEquals(emptyList(), h.links.all(IVAN))
            assertEquals(emptyList(), h.measures.all(IVAN))
            assertEquals(emptyList(), h.measurements.all(IVAN))
            assertEquals(emptyList(), h.photos.all(IVAN))
            assertNull(h.files.read(ivans.id))
            assertNull(h.watermarks.lastPullAt(IVAN))

            assertEquals(1, h.visits.all(MISHA).size)
            assertEquals(1, h.plans.all(MISHA).size)
            assertNotNull(h.profiles.forOwner(MISHA))
            assertEquals(1, h.measurements.all(MISHA).size)
            assertNotNull(h.files.read(mishas.id))
            assertEquals(T0, h.watermarks.lastPullAt(MISHA))
        }
}
