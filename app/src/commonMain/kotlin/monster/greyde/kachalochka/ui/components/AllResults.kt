package monster.greyde.kachalochka.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.ui.strings.strings

data class HistoryRowUi(
    val day: CalendarDay,
    val date: String,
    val results: String,
)

/** "Все результаты": one row per day, the date and what was recorded on it. */
@Composable
fun AllResults(
    rows: List<HistoryRowUi>,
    tag: String,
    onOpenDay: ((CalendarDay) -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    SectionLabel(strings().allResults, Modifier.padding(top = 24.dp, bottom = 8.dp))
    rows.forEachIndexed { i, row ->
        val opening = onOpenDay?.let { Modifier.clickable { it(row.day) } } ?: Modifier
        Row(
            Modifier
                .fillMaxWidth()
                .then(opening)
                .padding(vertical = 11.dp)
                .testTag("$tag-$i"),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                row.date,
                modifier = Modifier.width(112.dp).testTag("$tag-date-$i"),
                fontSize = 14.sp,
                color = colors.onBackground.copy(alpha = 0.55f),
            )
            Text(
                row.results,
                modifier = Modifier.testTag("$tag-results-$i"),
                fontSize = 15.sp,
                color = colors.onBackground,
            )
        }
    }
}
