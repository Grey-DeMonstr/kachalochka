package monster.greyde.kachalochka.core.domain.gym

import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class MachineMergeTest {
    private val ivan = UserId("11111111-1111-4111-8111-111111111111")
    private val edited = Machine.new("Жим ногами", ivan, T0)
    private val other = Machine.new("Жим ногами (2)", ivan, T0)
    private val now = T0 + 1.days
    private val friendsMachine = MachineId.random()
    private val anotherFriendsMachine = MachineId.random()

    private fun firstSets(vararg pairs: Pair<Machine, Instant>): (MachineId) -> Instant? {
        val byId = pairs.associate { (machine, at) -> machine.id to at }
        return { byId[it] }
    }

    private fun set(
        machine: Machine,
        minutes: Int,
    ) = WorkoutSet(
        WorkoutSetId.random(),
        ivan,
        VisitId.random(),
        machine.id,
        70.0,
        10,
        0,
        T0 + minutes.minutes,
        T0,
        false,
    )

    private fun link(
        from: Machine,
        to: MachineId,
    ) = MachineLink(MachineLinkId.random(), ivan, from.id, to, T0, false)

    @Test
    fun the_removed_machine_s_photos_move_to_the_kept_one() {
        val photo = Photo.new(other.id, ivan, T0)

        val merged = mergedMachines(edited, other, emptyList(), emptyList(), now, listOf(photo))

        assertEquals(listOf(photo.copy(machineId = edited.id, updatedAt = now)), merged.photos)
    }

    @Test
    fun the_machine_used_first_is_kept() {
        val older = firstSets(edited to T0 + 5.minutes, other to T0)

        assertEquals(other, olderMachine(edited, other, older))
        assertEquals(other, olderMachine(other, edited, older))
    }

    @Test
    fun a_machine_never_used_counts_as_newest() {
        assertEquals(other, olderMachine(edited, other, firstSets(other to T0)))
        assertEquals(edited, olderMachine(edited, other, firstSets(edited to T0)))
    }

    @Test
    fun a_tie_keeps_the_edited_machine() {
        assertEquals(edited, olderMachine(edited, other, firstSets()))
        assertEquals(edited, olderMachine(edited, other, firstSets(edited to T0, other to T0)))
    }

    @Test
    fun a_merge_moves_the_sets_and_links_and_deletes_the_removed_machine() {
        val sets = listOf(set(other, 0), set(other, 1))
        val moved = link(other, friendsMachine)
        val untouched = link(edited, anotherFriendsMachine)

        val rows = mergedMachines(edited, other, sets, listOf(moved, untouched), now)

        assertEquals(sets.map { it.copy(machineId = edited.id, updatedAt = now) }, rows.sets)
        assertEquals(listOf(moved.copy(machineId = edited.id, updatedAt = now)), rows.links)
        assertEquals(other.copy(deleted = true, updatedAt = now), rows.removed)
    }

    @Test
    fun a_link_the_kept_machine_already_has_is_deleted_instead_of_moved() {
        val kept = link(edited, friendsMachine)
        val duplicate = link(other, friendsMachine)

        val rows = mergedMachines(edited, other, emptyList(), listOf(kept, duplicate), now)

        assertEquals(listOf(duplicate.copy(deleted = true, updatedAt = now)), rows.links)
    }

    @Test
    fun a_link_from_the_removed_machine_into_the_kept_one_is_deleted() {
        val intoKept = link(other, edited.id)

        val rows = mergedMachines(edited, other, emptyList(), listOf(intoKept), now)

        assertEquals(listOf(intoKept.copy(deleted = true, updatedAt = now)), rows.links)
    }
}
