package monster.greyde.kachalochka.core.domain.measures

import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.Sex
import kotlin.math.roundToLong

const val DEFAULT_CYCLE_LENGTH = 28
val CYCLE_LENGTHS: IntRange = 14..60

/** The longest chart period that the cycle phases are drawn on. */
const val CYCLE_CHART_MONTHS = 3

private const val MENSTRUATION_DAYS = 5

// Ovulation comes about 14 days before the next cycle starts.
private const val OVULATION_FIRST_BEFORE_END = 14
private const val OVULATION_LAST_BEFORE_END = 12

/** A day of the cycle recorded on [recorded] tells that the cycle started on [start]. */
data class CycleAnchor(
    val recorded: CalendarDay,
    val start: CalendarDay,
)

/** [anchors] are oldest recorded first; each cycle is [length] days. */
data class Cycle(
    val anchors: List<CycleAnchor>,
    val length: Int,
)

enum class CyclePhase { Menstruation, Ovulation }

/** [phase] from [first] to [last], both included. */
data class PhaseBand(
    val phase: CyclePhase,
    val first: CalendarDay,
    val last: CalendarDay,
)

/** The anchors of recorded days of the cycle, oldest recorded first; a value below 1 has none. */
fun cycleAnchors(values: Map<CalendarDay, Double>): List<CycleAnchor> =
    values
        .filterValues { it >= 1 }
        .map { (day, value) -> CycleAnchor(day, day.plusDays(1 - value.roundToLong())) }
        .sortedBy { it.recorded }

/** A woman's cycle from her recorded days of the cycle; null without a sex Female or a value. */
fun cycleOf(
    profile: Profile?,
    measures: List<Measure>,
    values: List<Measurement>,
): Cycle? {
    if (profile?.sex != Sex.Female) return null
    val ids = measures.filter { it.kind == MeasureKind.CycleDay }.map { it.id }.toSet()
    return cycleFrom(values.filter { it.measureId in ids }, profile.cycleLength)
}

/** The cycle that [values] of the cycle day tell; a null [length] is the default. */
fun cycleFrom(
    values: List<Measurement>,
    length: Int?,
): Cycle? =
    cycleAnchors(values.associate { it.day to it.value }).takeIf { it.isNotEmpty() }?.let {
        Cycle(it, length ?: DEFAULT_CYCLE_LENGTH)
    }

/**
 * The day of the cycle on [day], 1 to [Cycle.length]. Of the cycles started by [day], the one
 * recorded last rules; before every start, the earliest start counts back.
 */
fun Cycle.dayOn(day: CalendarDay): Int {
    val ruling = anchors.lastOrNull { it.start <= day } ?: anchors.minBy { it.start }
    return (day.epochDay - ruling.start.epochDay).mod(length) + 1
}

/** Menstruation wins a day that is in both phases, as in a short cycle. */
fun Cycle.phaseOn(day: CalendarDay): CyclePhase? {
    val number = dayOn(day)
    return when {
        number <= MENSTRUATION_DAYS -> CyclePhase.Menstruation
        number in
            (length - OVULATION_FIRST_BEFORE_END)..(length - OVULATION_LAST_BEFORE_END) ->
            CyclePhase.Ovulation
        else -> null
    }
}

/** The phases from [from] to [to], oldest first, each cut to the range. */
fun Cycle.bands(
    from: CalendarDay,
    to: CalendarDay,
): List<PhaseBand> {
    val bands = mutableListOf<PhaseBand>()
    for (epochDay in from.epochDay..to.epochDay) {
        val day = CalendarDay.ofEpochDay(epochDay)
        val phase = phaseOn(day) ?: continue
        val open = bands.lastOrNull()
        if (open != null && open.phase == phase && open.last.epochDay == epochDay - 1) {
            bands[bands.lastIndex] = open.copy(last = day)
        } else {
            bands += PhaseBand(phase, day, day)
        }
    }
    return bands
}

/** True when a chart of [period] that runs from [first] to [last] is short enough for phases. */
fun showsCycle(
    period: MeasurePeriod,
    first: CalendarDay,
    last: CalendarDay,
): Boolean =
    period.months?.let { it <= CYCLE_CHART_MONTHS }
        ?: (first > last.minusMonths(CYCLE_CHART_MONTHS))
