package monster.greyde.kachalochka.core.domain.gym

import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class MachineTest {
    private val ivan = UserId("11111111-1111-4111-8111-111111111111")
    private val oleg = UserId("33333333-3333-4333-8333-333333333333")
    private val pasha = UserId("44444444-4444-4444-8444-444444444444")

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
        assertNull(machine.linkId)
    }

    @Test
    fun the_weight_steps_are_the_four_the_form_offers() {
        assertEquals(listOf(1.0, 2.5, 5.0, 10.0), Machine.WEIGHT_STEPS)
    }

    @Test
    fun a_machine_nobody_linked_is_its_own_link_key() {
        val press = Machine.new("Жим ногами", null, T0)

        assertEquals(press.id, press.linkKey)
    }

    @Test
    fun a_linked_copy_is_the_owner_s_own_row_with_the_friend_s_settings_and_key() {
        val theirs =
            Machine
                .new("Жим ногами", oleg, T0)
                .copy(weightStep = 5.0, unit = WeightUnit.Custom, unitLabel = "плитка")
        val later = T0 + 1.hours

        val mine = linkedCopy(theirs, ivan, later)

        assertNotEquals(theirs.id, mine.id)
        assertEquals(
            theirs.copy(id = mine.id, userId = ivan, linkId = theirs.id, updatedAt = later),
            mine,
        )
        assertEquals(theirs.linkKey, mine.linkKey)
    }

    @Test
    fun a_copy_of_a_linked_machine_joins_the_same_key() {
        val root = Machine.new("Жим ногами", oleg, T0)

        val second = linkedCopy(linkedCopy(root, ivan, T0), pasha, T0)

        assertEquals(root.id, second.linkKey)
    }

    private companion object {
        val T0 = Instant.fromEpochSeconds(1_700_000_000)
    }
}
