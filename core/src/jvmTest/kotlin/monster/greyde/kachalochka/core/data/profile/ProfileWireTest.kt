package monster.greyde.kachalochka.core.data.profile

import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.MachineSort
import monster.greyde.kachalochka.core.domain.gym.PhotoId
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.ProfileId
import monster.greyde.kachalochka.core.domain.profile.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ProfileWireTest {
    private val oleg = UserId("11111111-1111-4111-8111-111111111111")
    private val anna = UserId("22222222-2222-4222-8222-222222222222")

    @Test
    fun a_profile_with_every_field_set_survives_the_wire_round_trip() {
        val profile =
            Profile(
                id = ProfileId("9b1f0c3e-0000-4000-8000-000000000001"),
                userId = UserId("9b1f0c3e-0000-4000-8000-000000000002"),
                displayName = "Серж",
                updatedAt = Instant.fromEpochMilliseconds(1_700_000_000_123),
                deleted = false,
                friendColors = mapOf(oleg to 0xFF4CAF50.toInt(), anna to 3),
                sex = Sex.Female,
                birthDate = CalendarDay(1990, 6, 15),
                heightCm = 172.5,
                weightUnit = PreferredWeightUnit.Mixed,
                groupByTag = true,
                avatarPhoto = PhotoId("9b1f0c3e-0000-4000-8000-000000000003"),
                machineSort = MachineSort.Frequent,
            )

        assertEquals("1990-06-15", ProfileRow.of(profile).birthDate)
        assertEquals("mixed", ProfileRow.of(profile).weightUnit)
        assertEquals(profile, ProfileRow.of(profile).toProfile())
    }

    @Test
    fun the_weight_unit_travels_by_its_wire_name() {
        assertEquals(
            listOf("kg", "lb", "mixed"),
            PreferredWeightUnit.entries.map { it.wireName() },
        )
        assertEquals(PreferredWeightUnit.Lb, weightUnitOf("lb"))
    }

    @Test
    fun a_missing_or_unknown_weight_unit_reads_as_kilograms() {
        assertEquals(PreferredWeightUnit.Kg, weightUnitOf("stone"))
        assertEquals(PreferredWeightUnit.Kg, weightUnitOf(null))
    }

    @Test
    fun the_machine_sort_travels_by_its_wire_name() {
        assertEquals(
            listOf("recent", "name", "frequent"),
            MachineSort.entries.map { it.wireName() },
        )
        assertEquals(MachineSort.Name, machineSortOf("name"))
    }

    @Test
    fun a_missing_or_unknown_machine_sort_reads_as_recent() {
        assertEquals(MachineSort.Recent, machineSortOf("random"))
        assertEquals(MachineSort.Recent, machineSortOf(null))
    }

    @Test
    fun an_unreadable_birth_date_reads_as_none() {
        assertEquals(null, birthDateOf("15.06.1990"))
        assertEquals(null, birthDateOf("1990-02-30"))
        assertEquals(null, birthDateOf(null))
    }

    @Test
    fun unreadable_friend_colours_decode_to_none() {
        assertEquals(emptyMap(), friendColorsOf("garbage"))
    }

    @Test
    fun a_colour_under_an_invalid_user_id_is_dropped_and_the_rest_kept() {
        val text = """{"not-a-user":1,"${oleg.value}":2,"${anna.value}":"red"}"""

        assertEquals(mapOf(oleg to 2), friendColorsOf(text))
    }

    @Test
    fun friend_colours_written_to_text_read_back() {
        val colors = mapOf(oleg to -1, anna to 7)

        assertEquals(colors, friendColorsOf(friendColorsText(colors)))
    }

    @Test
    fun sex_travels_by_its_wire_name() {
        assertEquals("male", Sex.Male.wireName())
        assertEquals(Sex.Female, sexOf("female"))
    }

    @Test
    fun an_unknown_sex_reads_as_none() {
        assertEquals(null, sexOf("other"))
        assertEquals(null, sexOf(null))
    }
}
