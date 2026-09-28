package monster.greyde.kachalochka.core.domain.measures

import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.Sex
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
    private val now = Instant.fromEpochSeconds(1_800_000_000)

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
                Triple("Обхват бёдер", "см", MeasureKind.Hips),
                Triple("Бицепс", "см", MeasureKind.Biceps),
                Triple("Окружность бедра", "см", MeasureKind.Thigh),
                Triple("Шея", "см", MeasureKind.Neck),
            ),
            defaults.map { Triple(it.name, it.unit, it.kind) },
        )
        assertEquals((0..6).toList(), defaults.map { it.position })
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
    fun a_predefined_measure_shows_the_app_s_name_and_unit_whatever_is_stored() {
        val hips = predefined(MeasureKind.Hips).copy(name = "Бёдра", unit = "дюйм")
        val own = hips.copy(kind = null, name = "Предплечье", unit = "см")

        assertEquals("Обхват бёдер" to "см", hips.displayName to hips.displayUnit)
        assertEquals("Предплечье" to "см", own.displayName to own.displayUnit)
        assertEquals(
            "Жир по весам или калиперу" to "%",
            predefined(MeasureKind.BodyFat).let { it.displayName to it.displayUnit },
        )
    }

    @Test
    fun upkeep_seeds_the_kinds_the_owner_never_had() {
        val stored = missingDefaults(ivan, emptySet()).filter { it.kind != MeasureKind.Neck }

        val writes = measureUpkeep(ivan, stored, emptySet(), Sex.Male, now)

        assertEquals(listOf(MeasureKind.Neck), writes.map { it.kind })
        assertEquals(epoch, writes.single().updatedAt)
    }

    @Test
    fun upkeep_revives_a_deleted_measure_a_formula_reads_and_dates_it_now() {
        val neck = predefined(MeasureKind.Neck).copy(deleted = true)
        val chest = predefined(MeasureKind.Chest).copy(deleted = true)
        val stored =
            everyPredefined() - predefined(MeasureKind.Neck) -
                predefined(MeasureKind.Chest) + neck + chest

        val writes = measureUpkeep(ivan, stored, emptySet(), Sex.Male, now)

        assertEquals(listOf(neck.copy(deleted = false, updatedAt = now)), writes)
    }

    @Test
    fun deleted_hips_come_back_unless_the_owner_is_a_man() {
        val hips = predefined(MeasureKind.Hips).copy(deleted = true)
        val stored = everyPredefined() - predefined(MeasureKind.Hips) + hips

        assertEquals(emptyList(), measureUpkeep(ivan, stored, emptySet(), Sex.Male, now))
        assertEquals(
            listOf(MeasureKind.Hips),
            measureUpkeep(ivan, stored, emptySet(), Sex.Female, now).map { it.kind },
        )
        assertEquals(
            listOf(MeasureKind.Hips),
            measureUpkeep(ivan, stored, emptySet(), null, now).map { it.kind },
        )
    }

    @Test
    fun upkeep_deletes_a_fat_measure_without_values_and_keeps_one_with_values() {
        val fat = predefined(MeasureKind.BodyFat)
        val stored = everyPredefined() + fat

        assertEquals(
            listOf(fat.copy(deleted = true, updatedAt = now)),
            measureUpkeep(ivan, stored, emptySet(), Sex.Male, now),
        )
        assertEquals(emptyList(), measureUpkeep(ivan, stored, setOf(fat.id), Sex.Male, now))
    }

    @Test
    fun formulas_read_weight_waist_neck_and_hips_only_for_a_woman_or_an_unknown_sex() {
        val navy = listOf(BodyFatMethod.Navy)

        assertEquals(
            listOf(BodyFatMethod.Ymca, BodyFatMethod.Deurenberg),
            methodsReading(MeasureKind.Weight, Sex.Male),
        )
        assertEquals(
            listOf(BodyFatMethod.Navy, BodyFatMethod.Ymca),
            methodsReading(MeasureKind.Waist, Sex.Male),
        )
        assertEquals(navy, methodsReading(MeasureKind.Neck, Sex.Male))
        assertEquals(emptyList(), methodsReading(MeasureKind.Hips, Sex.Male))
        assertEquals(navy, methodsReading(MeasureKind.Hips, Sex.Female))
        assertEquals(navy, methodsReading(MeasureKind.Hips, null))
        assertEquals(emptyList(), methodsReading(MeasureKind.Chest, Sex.Female))
        assertEquals(emptyList(), methodsReading(MeasureKind.BodyFat, Sex.Female))
    }

    private fun everyPredefined() = missingDefaults(ivan, emptySet())

    private fun predefined(kind: MeasureKind) =
        everyPredefined().firstOrNull { it.kind == kind }
            ?: everyPredefined().first().copy(
                id = MeasureId(derivedId(ivan, kind)),
                kind = kind,
                name = "Жир",
                unit = "%",
            )

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
    fun a_day_whose_newest_value_was_cleared_is_left_out_whatever_older_rows_say() {
        val weight = MeasureId.random()
        val monday = CalendarDay(2026, 9, 21)
        val older = value(weight, monday, 80.0, at = 1)
        val cleared = value(weight, monday, 81.0, at = 2).copy(deleted = true)
        val clearedEarlier = value(weight, monday.plusDays(7), 79.0, at = 1).copy(deleted = true)
        val rewritten = value(weight, monday.plusDays(7), 78.0, at = 2)

        val kept = newestPerDay(listOf(older, cleared, clearedEarlier, rewritten))

        assertEquals(listOf(rewritten), kept)
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
