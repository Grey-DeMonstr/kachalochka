package monster.greyde.kachalochka.ui.plans

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.MachineId

/**
 * Asks which plan takes the visit's machines while [viewModel] offers its choices. A new plan
 * opens with the day of an own visit, or with the stand-ins of a friend's visit.
 */
@Composable
internal fun VisitPlanChooser(
    viewModel: VisitPlacementViewModel,
    onNewPlanOfMachines: (List<MachineId>) -> Unit = {},
    onNewPlan: (CalendarDay) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val choices = state.choices ?: return
    PlanChoiceDialog(
        choices,
        onChoose = viewModel::addTo,
        onNewPlan = { viewModel.newPlan(onNewPlanOfMachines, onNewPlan) },
        onCancel = viewModel::cancelChoice,
    )
}

/** What the last placement of the visit shown as [visit] said; a tap hides it. */
@Composable
internal fun VisitPlacementNotice(
    viewModel: VisitPlacementViewModel,
    visit: String,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val notice = state.notice?.takeIf { it.visit == visit } ?: return
    PlacementNoticeText(notice.text, viewModel::dismissNotice, modifier)
}
