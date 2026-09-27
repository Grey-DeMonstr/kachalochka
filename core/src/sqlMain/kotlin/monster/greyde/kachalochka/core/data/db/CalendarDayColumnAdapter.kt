package monster.greyde.kachalochka.core.data.db

import app.cash.sqldelight.ColumnAdapter
import monster.greyde.kachalochka.core.domain.gym.CalendarDay

internal object CalendarDayColumnAdapter : ColumnAdapter<CalendarDay, String> {
    override fun decode(databaseValue: String): CalendarDay = CalendarDay.parse(databaseValue)

    override fun encode(value: CalendarDay): String = value.iso
}
