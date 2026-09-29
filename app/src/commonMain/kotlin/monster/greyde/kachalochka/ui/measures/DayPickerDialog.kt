package monster.greyde.kachalochka.ui.measures

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.ui.calendar.DayUi
import monster.greyde.kachalochka.ui.calendar.MonthGrid
import monster.greyde.kachalochka.ui.calendar.MonthHeader
import monster.greyde.kachalochka.ui.strings.strings

/** Days with values are marked as visits are on the calendar. */
data class DayPickerUi(
    val monthTitle: String,
    val canShowNextMonth: Boolean,
    val weeks: List<List<DayUi?>>,
)

@Composable
internal fun DayPickerDialog(
    picker: DayPickerUi,
    onSelect: (CalendarDay) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
) {
    // The platform's dialog width is too narrow for seven 40 dp day cells on a phone.
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier
                .padding(16.dp)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .testTag("day-picker"),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MonthHeader(picker.monthTitle, picker.canShowNextMonth, onPrevious, onNext)
                MonthGrid(picker.weeks, onSelect)
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End).testTag("day-picker-cancel"),
                ) {
                    Text(strings().cancel)
                }
            }
        }
    }
}
