package monster.greyde.kachalochka.ui.measures

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import monster.greyde.kachalochka.core.domain.measures.BodyFatMethod
import monster.greyde.kachalochka.ui.components.AllResults
import monster.greyde.kachalochka.ui.components.Screen
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** One formula's calculated percent over the periods. */
@Composable
fun BodyFatScreen(
    method: BodyFatMethod,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: BodyFatViewModel = koinViewModel { parametersOf(method) }
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    val fat = state
    Screen(fat?.title.orEmpty(), onBack = onBack, onOpenSettings = onOpenSettings) {
        if (fat == null) return@Screen
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                MeasureSummary(fat.latest, fat.change, fat.lastMeasured, fat.missing)
                if (fat.profileIncomplete) ProfileLink(onOpenSettings)
            }
            PeriodChoice(fat.period, viewModel::choose)
            PeriodChart(fat.points, ::fatPercent)
            if (fat.results.isNotEmpty()) {
                Column { AllResults(fat.results, "fat-history") }
            }
        }
    }
}
