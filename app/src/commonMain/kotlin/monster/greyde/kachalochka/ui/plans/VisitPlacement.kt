package monster.greyde.kachalochka.ui.plans

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import monster.greyde.kachalochka.core.domain.gym.CalendarDay

/** Asks which plan takes the visit's machines while [viewModel] offers its choices. */
@Composable
internal fun VisitPlanChooser(
    viewModel: VisitPlacementViewModel,
    onNewPlan: (CalendarDay) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val choices = state.choices ?: return
    PlanChoiceDialog(
        choices,
        onChoose = viewModel::addTo,
        onNewPlan = { viewModel.newPlan(onNewPlan) },
        onCancel = viewModel::cancelChoice,
    )
}

/** What the last placement of the visit on [day] said; a tap hides it. */
@Composable
internal fun VisitPlacementNotice(
    viewModel: VisitPlacementViewModel,
    day: CalendarDay,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val notice = state.notice?.takeIf { it.day == day } ?: return
    PlacementNoticeText(notice.text, viewModel::dismissNotice, modifier)
}
