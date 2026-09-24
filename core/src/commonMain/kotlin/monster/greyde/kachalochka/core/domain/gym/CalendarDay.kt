package monster.greyde.kachalochka.core.domain.gym

import kotlin.time.Duration
import kotlin.time.Instant

data class CalendarDay(
    val year: Int,
    val month: Int,
    val day: Int,
) : Comparable<CalendarDay> {
    init {
        require(month in 1..12) { "month must be in 1..12, was $month" }
        require(day in 1..CalendarMonth(year, month).length) {
            "day must be in 1..${CalendarMonth(year, month).length}, was $day"
        }
    }

    // Howard Hinnant's days-from-civil algorithm.
    val epochDay: Long
        get() {
            val y = if (month <= 2) year - 1L else year.toLong()
            val era = (if (y >= 0) y else y - 399) / 400
            val yearOfEra = y - era * 400
            val dayOfYear = (153 * ((month + 9) % 12) + 2) / 5 + day - 1
            val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
            return era * 146_097 + dayOfEra - 719_468
        }

    val dayOfWeek: Int
        get() = (epochDay + 3).mod(7) + 1

    fun plusDays(days: Long): CalendarDay = ofEpochDay(epochDay + days)

    fun at(
        millisOfDay: Long,
        utcOffset: Duration,
    ): Instant =
        Instant.fromEpochMilliseconds(
            epochDay * MILLIS_PER_DAY + millisOfDay - utcOffset.inWholeMilliseconds,
        )

    override fun compareTo(other: CalendarDay): Int = epochDay.compareTo(other.epochDay)

    companion object {
        fun ofEpochDay(epochDay: Long): CalendarDay {
            val z = epochDay + 719_468
            val era = (if (z >= 0) z else z - 146_096) / 146_097
            val dayOfEra = z - era * 146_097
            val yearOfEra =
                (dayOfEra - dayOfEra / 1_460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
            val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
            val shifted = (5 * dayOfYear + 2) / 153
            val day = dayOfYear - (153 * shifted + 2) / 5 + 1
            val month = if (shifted < 10) shifted + 3 else shifted - 9
            val year = yearOfEra + era * 400 + if (month <= 2) 1 else 0
            return CalendarDay(year.toInt(), month.toInt(), day.toInt())
        }

        fun of(
            instant: Instant,
            utcOffset: Duration,
        ): CalendarDay = ofEpochDay(localDay(instant, utcOffset))
    }
}

data class CalendarMonth(
    val year: Int,
    val month: Int,
) {
    val length: Int
        get() =
            when (month) {
                2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
                4, 6, 9, 11 -> 30
                else -> 31
            }

    fun first(): CalendarDay = CalendarDay(year, month, 1)

    fun plusMonths(months: Int): CalendarMonth {
        val totalMonths = year * 12 + (month - 1) + months
        return CalendarMonth(totalMonths.floorDiv(12), totalMonths.mod(12) + 1)
    }

    fun weeks(): List<List<CalendarDay?>> {
        val leading = List<CalendarDay?>(first().dayOfWeek - 1) { null }
        val days = (1..length).map { CalendarDay(year, month, it) }
        val trailingCount = (7 - (leading.size + days.size) % 7) % 7
        val trailing = List<CalendarDay?>(trailingCount) { null }
        return (leading + days + trailing).chunked(7)
    }

    companion object {
        fun of(day: CalendarDay): CalendarMonth = CalendarMonth(day.year, day.month)
    }
}
