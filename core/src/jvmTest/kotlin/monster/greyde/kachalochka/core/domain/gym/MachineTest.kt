package monster.greyde.kachalochka.core.domain.gym

import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class MachineTest {
    private val ivan = UserId("11111111-1111-4111-8111-111111111111")
    private val oleg = UserId("33333333-3333-4333-8333-333333333333")

    @Test
    fun a_new_machine_carries_the_form_defaults() {
        val now = Instant.fromEpochSeconds(1_700_000_000)

        val machine = Machine.new("Жим ногами", userId = null, now = now)

        assertEquals("Жим ногами", machine.name)
        assertEquals("", machine.setupNote)
        assertEquals(WeightMode.Total, machine.weightMode)
        assertEquals(0.0, machine.platformWeight)
        assertEquals(false, machine.platformIncluded)
        assertEquals(WeightUnit.Kg, machine.unit)
        assertEquals("", machine.unitLabel)
        assertEquals(2.5, machine.weightStep)
        assertEquals(now, machine.updatedAt)
        assertEquals(false, machine.deleted)
    }

    @Test
    fun a_linked_copy_is_the_owner_s_own_row_with_the_friend_s_settings() {
        val theirs =
            Machine
                .new("Жим ногами", oleg, T0)
                .copy(weightStep = 5.0, unit = WeightUnit.Custom, unitLabel = "плитка")
        val later = T0 + 1.hours

        val (mine, _) = linkedCopy(theirs, ivan, later)

        assertNotEquals(theirs.id, mine.id)
        assertEquals(theirs.copy(id = mine.id, userId = ivan, updatedAt = later), mine)
    }

    @Test
    fun a_linked_copy_keeps_the_friend_s_tags() {
        val theirs = Machine.new("Жим ногами", oleg, T0).copy(tags = setOf("Ноги", "Спина"))

        val (mine, _) = linkedCopy(theirs, ivan, T0)

        assertEquals(setOf("Ноги", "Спина"), mine.tags)
    }

    @Test
    fun a_linked_copy_spells_a_tag_the_account_has_as_the_account_does() {
        val theirs = Machine.new("Жим ногами", oleg, T0).copy(tags = setOf("ноги", "Спина"))

        val (mine, _) = linkedCopy(theirs, ivan, T0, known = listOf("НОГИ", "Руки"))

        assertEquals(setOf("НОГИ", "Спина"), mine.tags)
    }

    @Test
    fun the_owner_s_machine_of_the_shown_one_s_cluster_stands_in_for_it_before_a_namesake() {
        val theirs = Machine.new("Жим ногами", oleg, T0)
        val namesake = Machine.new("Жим ногами", ivan, T0)
        val linked = Machine.new("Платформа", ivan, T0)
        val link = MachineLink(MachineLinkId.random(), oleg, theirs.id, linked.id, T0, false)

        val standIn = ownStandIn(theirs, listOf(namesake, linked), MachineClusters(listOf(link)))

        assertEquals(linked, standIn)
    }

    @Test
    fun without_a_linked_machine_one_of_the_same_name_in_any_letter_case_stands_in() {
        val theirs = Machine.new("Жим ногами", oleg, T0)
        val namesake = Machine.new(" жим НОГАМИ", ivan, T0)
        val other = Machine.new("Тяга", ivan, T0)

        val standIn = ownStandIn(theirs, listOf(other, namesake), MachineClusters(emptyList()))

        assertEquals(namesake, standIn)
    }

    @Test
    fun nothing_stands_in_for_a_machine_the_owner_has_not_got() {
        val theirs = Machine.new("Жим ногами", oleg, T0)

        val standIn =
            ownStandIn(theirs, listOf(Machine.new("Тяга", ivan, T0)), MachineClusters(emptyList()))

        assertEquals(null, standIn)
    }

    @Test
    fun a_linked_copy_keeps_the_friend_s_cover_which_stays_in_its_cluster() {
        val cover = PhotoId.random()
        val theirs = Machine.new("Жим ногами", oleg, T0).copy(coverPhoto = cover)

        val (mine, _) = linkedCopy(theirs, ivan, T0)

        assertEquals(cover, mine.coverPhoto)
    }

    @Test
    fun a_linked_copy_comes_with_the_owner_s_link_to_the_friend_s_machine() {
        val theirs = Machine.new("Жим ногами", oleg, T0)
        val later = T0 + 1.hours

        val (mine, link) = linkedCopy(theirs, ivan, later)

        assertEquals(
            MachineLink(link.id, ivan, mine.id, theirs.id, later, deleted = false),
            link,
        )
    }

    private companion object {
        val T0 = Instant.fromEpochSeconds(1_700_000_000)
    }
}
