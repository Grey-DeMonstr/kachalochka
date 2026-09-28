package monster.greyde.kachalochka.core.domain.gym

import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

class PhotoTest {
    private val ivan = UserId("11111111-1111-4111-8111-111111111111")
    private val oleg = UserId("33333333-3333-4333-8333-333333333333")

    @Test
    fun a_photo_id_is_a_lower_case_uuid_v4() {
        assertFailsWith<IllegalArgumentException> { PhotoId("not-a-uuid") }
        assertEquals(36, PhotoId.random().value.length)
    }

    @Test
    fun a_new_photo_is_live_and_taken_now() {
        val photo = Photo.new(PRESS, ivan, T0)

        assertEquals(PRESS, photo.machineId)
        assertEquals(ivan, photo.userId)
        assertEquals(T0, photo.takenAt)
        assertEquals(T0, photo.updatedAt)
        assertEquals(false, photo.deleted)
    }

    @Test
    fun photos_run_oldest_first_and_by_id_on_a_tie() {
        val first = Photo.new(PRESS, ivan, T0).copy(id = PhotoId(VISIT_A.value))
        val tied = first.copy(id = PhotoId(VISIT_B.value))
        val later = Photo.new(PRESS, ivan, T0 + 1.minutes)

        assertEquals(listOf(first, tied, later), listOf(later, tied, first).sortedWith(photoOrder))
    }

    @Test
    fun a_machine_s_cover_is_its_own_first_photo() {
        val later = Photo.new(PRESS, ivan, T0 + 1.minutes)
        val first = Photo.new(PRESS, ivan, T0)
        val friends = Photo.new(ROW, oleg, T0 - 1.minutes)
        val clusters = MachineClusters(listOf(link(PRESS, ROW)))

        assertEquals(first, coverPhoto(PRESS, listOf(later, friends, first), clusters))
    }

    @Test
    fun without_its_own_photo_a_machine_shows_a_linked_machine_s_first_photo() {
        val friends = Photo.new(ROW, oleg, T0)
        val stranger = Photo.new(MachineId.random(), oleg, T0 - 1.minutes)

        assertEquals(
            friends,
            coverPhoto(
                PRESS,
                listOf(stranger, friends),
                MachineClusters(listOf(link(ROW, PRESS))),
            ),
        )
        assertNull(coverPhoto(PRESS, listOf(stranger, friends), MachineClusters(emptyList())))
    }

    private fun link(
        from: MachineId,
        to: MachineId,
    ) = MachineLink(MachineLinkId.random(), ivan, from, to, T0, false)
}
