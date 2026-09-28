package monster.greyde.kachalochka.core.data.measures

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.measures.Measure
import monster.greyde.kachalochka.core.domain.measures.MeasureId
import monster.greyde.kachalochka.core.domain.measures.Measurement
import monster.greyde.kachalochka.core.domain.measures.MeasurementId
import monster.greyde.kachalochka.core.domain.measures.measureKindOf
import monster.greyde.kachalochka.core.domain.measures.wireName
import kotlin.time.Instant

const val MEASURE_TABLE: String = "measure"
const val MEASUREMENT_TABLE: String = "measurement"

@Serializable
internal data class MeasureRow(
    val id: String,
    @SerialName("user_id") val userId: String?,
    val name: String,
    val unit: String,
    val kind: String?,
    val position: Int,
    @SerialName("updated_at") val updatedAt: String,
    val deleted: Boolean,
) {
    fun toMeasure(): Measure =
        Measure(
            id = MeasureId(id),
            userId = userId?.let(::UserId),
            name = name,
            unit = unit,
            kind = kind?.let(::measureKindOf),
            position = position,
            updatedAt = Instant.parse(updatedAt),
            deleted = deleted,
        )

    companion object {
        fun of(measure: Measure): MeasureRow =
            MeasureRow(
                id = measure.id.value,
                userId = measure.userId?.value,
                name = measure.name,
                unit = measure.unit,
                kind = measure.kind?.wireName(),
                position = measure.position,
                updatedAt = measure.updatedAt.toString(),
                deleted = measure.deleted,
            )
    }
}

@Serializable
internal data class MeasurementRow(
    val id: String,
    @SerialName("user_id") val userId: String?,
    @SerialName("measure_id") val measureId: String,
    val day: String,
    val value: Double,
    @SerialName("updated_at") val updatedAt: String,
    val deleted: Boolean,
) {
    fun toMeasurement(): Measurement =
        Measurement(
            id = MeasurementId(id),
            userId = userId?.let(::UserId),
            measureId = MeasureId(measureId),
            day = CalendarDay.parse(day),
            value = value,
            updatedAt = Instant.parse(updatedAt),
            deleted = deleted,
        )

    companion object {
        fun of(measurement: Measurement): MeasurementRow =
            MeasurementRow(
                id = measurement.id.value,
                userId = measurement.userId?.value,
                measureId = measurement.measureId.value,
                day = measurement.day.iso,
                value = measurement.value,
                updatedAt = measurement.updatedAt.toString(),
                deleted = measurement.deleted,
            )
    }
}
