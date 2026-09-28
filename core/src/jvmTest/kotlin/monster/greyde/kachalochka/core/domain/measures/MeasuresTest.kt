package monster.greyde.kachalochka.core.domain.measures

import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class MeasuresTest {
    private val ivan = UserId("11111111-1111-4111-8111-111111111111")
    private val misha = UserId("22222222-2222-4222-8222-222222222222")
    private val epoch = Instant.fromEpochSeconds(0)

    @Test
    fun a_derived_id_is_the_same_on_every_device_and_a_valid_id() {
        val id = derivedId(ivan, MeasureKind.Weight)

        assertEquals("f6a47529-7f23-4e17-988c-4faee22cb778", id)
        assertEquals(id, MeasureId(id).value)
    }

    @Test
    fun derived_ids_differ_per_kind_and_per_owner() {
        val ivans = MeasureKind.entries.map { derivedId(ivan, it) }

        assertEquals(MeasureKind.entries.size, ivans.toSet().size)
        assertNotEquals(derivedId(ivan, MeasureKind.Waist), derivedId(misha, MeasureKind.Waist))
        MeasureKind.entries.forEach { MeasureId(derivedId(misha, it)) }
    }

    @Test
    fun an_owner_without_measures_gets_every_predefined_one_at_the_epoch() {
        val defaults = missingDefaults(ivan, emptySet())

        assertEquals(
            listOf(
                Triple("Вес", "кг", MeasureKind.Weight),
                Triple("Талия", "см", MeasureKind.Waist),
                Triple("Грудь", "см", MeasureKind.Chest),
                Triple("Бёдра", "см", MeasureKind.Hips),
                Triple("Бицепс", "см", MeasureKind.Biceps),
                Triple("Бедро", "см", MeasureKind.Thigh),
                Triple("Шея", "см", MeasureKind.Neck),
                Triple("Жир", "%", MeasureKind.BodyFat),
            ),
            defaults.map { Triple(it.name, it.unit, it.kind) },
        )
        assertEquals((0..7).toList(), defaults.map { it.position })
        assertTrue(defaults.all { it.updatedAt == epoch && !it.deleted && it.userId == ivan })
        assertEquals(
            defaults.map { derivedId(ivan, it.kind!!) },
            defaults.map { it.id.value },
        )
    }

    @Test
    fun only_the_kinds_the_owner_lacks_are_created() {
        val defaults =
            missingDefaults(ivan, MeasureKind.entries.toSet() - MeasureKind.Neck)

        assertEquals(listOf(MeasureKind.Neck), defaults.map { it.kind })
        assertEquals(6, defaults.single().position)
    }

    @Test
    fun the_anonymous_owner_gets_random_ids() {
        val first = missingDefaults(null, emptySet())
        val second = missingDefaults(null, emptySet())

        assertTrue(first.all { it.userId == null })
        assertNotEquals(first.map { it.id }, second.map { it.id })
    }

    @Test
    fun a_kind_reads_back_from_its_wire_name_and_an_unknown_name_reads_as_none() {
        assertEquals(
            listOf("weight", "waist", "chest", "hips", "biceps", "thigh", "neck", "body_fat"),
            MeasureKind.entries.map { it.wireName() },
        )
        MeasureKind.entries.forEach { assertEquals(it, measureKindOf(it.wireName())) }
        assertNull(measureKindOf("forearm"))
    }

    @Test
    fun of_two_values_on_one_day_the_newest_is_kept_and_the_newest_day_comes_first() {
        val weight = MeasureId.random()
        val waist = MeasureId.random()
        val monday = CalendarDay(2026, 9, 21)
        val tuesday = CalendarDay(2026, 9, 22)
        val older = value(weight, monday, 80.0, at = 1)
        val newer = value(weight, monday, 81.0, at = 2)
        val waistOnMonday = value(waist, monday, 90.0, at = 1)
        val laterDay = value(weight, tuesday, 79.0, at = 1)

        val kept = newestPerDay(listOf(newer, older, waistOnMonday, laterDay))

        assertEquals(listOf(laterDay), kept.take(1))
        assertEquals(setOf(newer, waistOnMonday), kept.drop(1).toSet())
    }

    @Test
    fun ids_are_random_and_reject_anything_but_a_uuid_v4() {
        assertNotEquals(MeasureId.random(), MeasureId.random())
        assertNotEquals(MeasurementId.random(), MeasurementId.random())
        assertFailsWith<IllegalArgumentException> { MeasureId("weight") }
        assertFailsWith<IllegalArgumentException> { MeasurementId("") }
    }

    private fun value(
        measure: MeasureId,
        day: CalendarDay,
        value: Double,
        at: Long,
    ) = Measurement(
        MeasurementId.random(),
        ivan,
        measure,
        day,
        value,
        Instant.fromEpochSeconds(at),
        false,
    )
}
