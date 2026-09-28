package monster.greyde.kachalochka.core.data.measures

import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.measures.Measure
import monster.greyde.kachalochka.core.domain.measures.MeasureId
import monster.greyde.kachalochka.core.domain.measures.MeasureKind
import monster.greyde.kachalochka.core.domain.measures.Measurement
import monster.greyde.kachalochka.core.domain.measures.MeasurementId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class MeasuresWireTest {
    private val measure =
        Measure(
            id = MeasureId("9b1f0c3e-0000-4000-8000-000000000001"),
            userId = UserId("9b1f0c3e-0000-4000-8000-000000000002"),
            name = "Жир",
            unit = "%",
            kind = MeasureKind.BodyFat,
            position = 7,
            updatedAt = Instant.fromEpochMilliseconds(1_700_000_000_123),
            deleted = true,
        )

    @Test
    fun a_measure_survives_the_wire_round_trip() {
        assertEquals(measure, MeasureRow.of(measure).toMeasure())
        assertEquals("body_fat", MeasureRow.of(measure).kind)
    }

    @Test
    fun a_kind_this_version_does_not_know_reads_as_the_user_s_own_measure() {
        val row = MeasureRow.of(measure).copy(kind = "forearm")

        assertNull(row.toMeasure().kind)
    }

    @Test
    fun a_value_survives_the_wire_round_trip_with_its_day_as_an_iso_date() {
        val value =
            Measurement(
                id = MeasurementId("9b1f0c3e-0000-4000-8000-000000000003"),
                userId = UserId("9b1f0c3e-0000-4000-8000-000000000002"),
                measureId = measure.id,
                day = CalendarDay(2026, 9, 21),
                value = 82.4,
                updatedAt = Instant.fromEpochMilliseconds(1_700_000_000_123),
                deleted = false,
            )

        assertEquals(value, MeasurementRow.of(value).toMeasurement())
        assertEquals("2026-09-21", MeasurementRow.of(value).day)
    }
}
