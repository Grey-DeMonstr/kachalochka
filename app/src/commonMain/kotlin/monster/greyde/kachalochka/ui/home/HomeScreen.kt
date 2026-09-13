package monster.greyde.kachalochka.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import monster.greyde.kachalochka.core.APP_NAME
import org.koin.compose.viewmodel.koinViewModel

private const val BACKEND_CONFIGURED = "Backend configured"
private const val BACKEND_ABSENT = "Backend not configured"

@Composable
fun HomeScreen(onOpenSettings: () -> Unit) {
    val viewModel: HomeViewModel = koinViewModel()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = APP_NAME,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.testTag("home-title"),
            )
            Text(
                text = if (viewModel.backendConfigured) BACKEND_CONFIGURED else BACKEND_ABSENT,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("backend-state"),
            )
            Button(onClick = onOpenSettings, modifier = Modifier.testTag("open-settings")) {
                Text("Settings")
            }
        }
    }
}
