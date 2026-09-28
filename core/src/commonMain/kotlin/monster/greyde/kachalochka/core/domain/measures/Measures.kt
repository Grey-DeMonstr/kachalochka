package monster.greyde.kachalochka.core.domain.measures

import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.identity.newUuidV4
import monster.greyde.kachalochka.core.domain.identity.requireUuidV4
import monster.greyde.kachalochka.core.domain.profile.Sex
import kotlin.jvm.JvmInline
import kotlin.time.Instant

@JvmInline
value class MeasureId(
    val value: String,
) {
    init {
        requireUuidV4(value, "MeasureId")
    }

    companion object {
        fun random(): MeasureId = MeasureId(newUuidV4())
    }
}

@JvmInline
value class MeasurementId(
    val value: String,
) {
    init {
        requireUuidV4(value, "MeasurementId")
    }

    companion object {
        fun random(): MeasurementId = MeasurementId(newUuidV4())
    }
}

/** A predefined measure, named and measured in the app's terms rather than its stored ones. */
enum class MeasureKind { Weight, Waist, Chest, Hips, Biceps, Thigh, Neck, BodyFat }

fun MeasureKind.wireName(): String =
    when (this) {
        MeasureKind.Weight -> "weight"
        MeasureKind.Waist -> "waist"
        MeasureKind.Chest -> "chest"
        MeasureKind.Hips -> "hips"
        MeasureKind.Biceps -> "biceps"
        MeasureKind.Thigh -> "thigh"
        MeasureKind.Neck -> "neck"
        MeasureKind.BodyFat -> "body_fat"
    }

// A kind a newer version added reads as the user's own measure rather than failing a pull.
fun measureKindOf(wire: String): MeasureKind? =
    MeasureKind.entries.firstOrNull { it.wireName() == wire }

/** [kind] is null for a measure the user added. */
data class Measure(
    val id: MeasureId,
    val userId: UserId?,
    val name: String,
    val unit: String,
    val kind: MeasureKind?,
    val position: Int,
    val updatedAt: Instant,
    val deleted: Boolean,
) {
    val displayName: String get() = kind?.let(::predefinedOf)?.name ?: name

    val displayUnit: String get() = kind?.let(::predefinedOf)?.unit ?: unit
}

data class Measurement(
    val id: MeasurementId,
    val userId: UserId?,
    val measureId: MeasureId,
    val day: CalendarDay,
    val value: Double,
    val updatedAt: Instant,
    val deleted: Boolean,
)

interface MeasureRepository {
    suspend fun upsert(measure: Measure)

    /** The owner's live measures by position, then name. */
    suspend fun all(owner: UserId?): List<Measure>

    /** The owner's predefined measures, deleted ones included. */
    suspend fun predefined(owner: UserId?): List<Measure>
}

interface MeasurementRepository {
    suspend fun upsert(measurement: Measurement)

    /**
     * The owner's values, newest day first: of the rows of one day and measure the newest, left
     * out when it was deleted.
     */
    suspend fun all(owner: UserId?): List<Measurement>
}

val measureOrder: Comparator<Measure> =
    compareBy<Measure> { it.position }.thenBy { it.name }.thenBy { it.id.value }

// Two devices may each write a value for one day offline. [rows] includes deleted ones, so a
// cleared day stays cleared whatever an older row of it says.
fun newestPerDay(rows: List<Measurement>): List<Measurement> =
    rows
        .groupBy { it.measureId to it.day }
        .values
        .map { sameDay -> sameDay.maxWith(compareBy({ it.updatedAt }, { it.id.value })) }
        .filterNot { it.deleted }
        .sortedWith(compareByDescending<Measurement> { it.day }.thenBy { it.measureId.value })

private class Predefined(
    val kind: MeasureKind,
    val name: String,
    val unit: String,
)

private val defaultMeasures =
    listOf(
        Predefined(MeasureKind.Weight, "Вес", "кг"),
        Predefined(MeasureKind.Waist, "Талия", "см"),
        Predefined(MeasureKind.Chest, "Грудь", "см"),
        Predefined(MeasureKind.Hips, "Обхват бёдер", "см"),
        Predefined(MeasureKind.Biceps, "Бицепс", "см"),
        Predefined(MeasureKind.Thigh, "Окружность бедра", "см"),
        Predefined(MeasureKind.Neck, "Шея", "см"),
    )

private fun predefinedOf(kind: MeasureKind): Predefined? =
    defaultMeasures.firstOrNull { it.kind == kind }

/** The `updatedAt` of every seeded predefined measure, older than any real edit. */
val MEASURE_SEEDED_AT: Instant = Instant.fromEpochSeconds(0)

/**
 * The writes that keep [owner]'s [predefined] measures, deleted ones included, as the formulas
 * need them for [sex]: kinds never had are seeded, a deleted kind a formula reads comes back, and
 * a typed body fat measure goes, since body fat is calculated.
 */
fun measureUpkeep(
    owner: UserId?,
    predefined: List<Measure>,
    sex: Sex?,
    now: Instant,
): List<Measure> {
    val live = predefined.filterNot { it.deleted }.mapNotNull { it.kind }.toSet()
    val revived =
        predefined
            .filter { it.deleted && it.kind !in live && isLocked(it.kind!!, sex) }
            .groupBy { it.kind }
            .values
            .map { rows -> rows.maxBy { it.updatedAt }.copy(deleted = false, updatedAt = now) }
    val typedFat =
        predefined
            .filter { !it.deleted && it.kind == MeasureKind.BodyFat }
            .map { it.copy(deleted = true, updatedAt = now) }
    val duplicates = measureDuplicates(owner, predefined).keys
    val extra =
        predefined
            .filter { it.id in duplicates }
            .map { it.copy(deleted = true, updatedAt = now) }
    return missingDefaults(owner, predefined.mapNotNull { it.kind }.toSet()) + revived + typedFat +
        extra
}

/**
 * Each extra live row of a predefined kind, mapped to the row of that kind that stays. A first
 * sign-in claims the measures seeded without an account beside the account's own, so a kind can
 * have two. The derived row stays, so every device keeps the same one; else the smallest id.
 */
fun measureDuplicates(
    owner: UserId?,
    predefined: List<Measure>,
): Map<MeasureId, MeasureId> =
    predefined
        .filter { !it.deleted && it.kind != null && it.kind != MeasureKind.BodyFat }
        .groupBy { it.kind!! }
        .filterValues { it.size > 1 }
        .flatMap { (kind, rows) ->
            val derived = owner?.let { derivedId(it, kind) }
            val kept = rows.firstOrNull { it.id.value == derived } ?: rows.minBy { it.id.value }
            rows.filter { it != kept }.map { it.id to kept.id }
        }.toMap()

/** The predefined measures [owner] lacks, dated at the epoch so a real edit always wins. */
fun missingDefaults(
    owner: UserId?,
    existing: Set<MeasureKind>,
): List<Measure> =
    defaultMeasures.withIndex().filter { it.value.kind !in existing }.map { (position, default) ->
        Measure(
            id = MeasureId(owner?.let { derivedId(it, default.kind) } ?: newUuidV4()),
            userId = owner,
            name = default.name,
            unit = default.unit,
            kind = default.kind,
            position = position,
            updatedAt = MEASURE_SEEDED_AT,
            deleted = false,
        )
    }

private const val FNV_OFFSET_BASIS = 0xcbf29ce484222325uL
private const val FNV_PRIME = 0x100000001b3uL
private const val SECOND_BASIS_MASK = 0x9e3779b97f4a7c15uL

/** A v4-shaped id every device derives alike for [owner]'s predefined [kind]. */
fun derivedId(
    owner: UserId,
    kind: MeasureKind,
): String {
    val key = "${owner.value}:${kind.wireName()}".encodeToByteArray()
    val halves =
        listOf(
            fnv1a64(key, FNV_OFFSET_BASIS),
            fnv1a64(key, FNV_OFFSET_BASIS xor SECOND_BASIS_MASK),
        )
    val bytes = IntArray(16) { i -> (halves[i / 8] shr (56 - 8 * (i % 8))).toInt() and 0xff }
    bytes[6] = (bytes[6] and 0x0f) or 0x40
    bytes[8] = (bytes[8] and 0x3f) or 0x80
    val hex = bytes.joinToString("") { it.toString(16).padStart(2, '0') }
    return listOf(0..7, 8..11, 12..15, 16..19, 20..31).joinToString("-") { hex.substring(it) }
}

private fun fnv1a64(
    bytes: ByteArray,
    basis: ULong,
): ULong = bytes.fold(basis) { hash, byte -> (hash xor byte.toUByte().toULong()) * FNV_PRIME }
